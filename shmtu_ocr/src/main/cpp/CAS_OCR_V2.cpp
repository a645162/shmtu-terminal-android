//
// CAS_OCR_V2.cpp
//
// Implementation of the v2 single-model TriSlot decoder engine.
// See CAS_OCR_V2.h for the contract and the design notes.
//

#include "CAS_OCR_V2.h"

#include <android/log.h>
#include <android/asset_manager.h>

#include <cstdio>
#include <cstring>
#include <mutex>
#include <sstream>
#include <vector>

#include <opencv2/imgproc.hpp>
#include <net.h>
#include <benchmark.h>

#ifdef NCNN_SUPPORT_VULKAN
#include <gpu.h>
#endif

extern std::string logcat_tag;

namespace CAS_OCR_V2
{
    static ncnn::UnlockedPoolAllocator g_blob_pool_allocator;
    static ncnn::PoolAllocator g_workspace_pool_allocator;

    static ncnn::Net g_net;
    static std::mutex g_mutex;

    static bool g_loaded = false;
    static V2_MODEL_STATUS g_status = V2_MODEL_STATUS_NOT_LOADED;
    static bool g_use_gpu = false;

    // Discovered output blob names. Empty until the model is loaded and we
    // query net.output_names() (or the layer-index fallback).
    static std::string g_out_digit_left;
    static std::string g_out_operator;
    static std::string g_out_digit_right;

    static void log_info(const char* fmt, ...)
    {
        va_list ap;
        va_start(ap, fmt);
        __android_log_vprint(ANDROID_LOG_INFO, logcat_tag.c_str(), fmt, ap);
        va_end(ap);
    }
    static void log_err(const char* fmt, ...)
    {
        va_list ap;
        va_start(ap, fmt);
        __android_log_vprint(ANDROID_LOG_ERROR, logcat_tag.c_str(), fmt, ap);
        va_end(ap);
    }

    static void apply_net_opt(bool use_gpu)
    {
        ncnn::Option& opt = g_net.opt;
        opt.lightmode = true;
        opt.num_threads = use_gpu ? 1 : 4;
        opt.blob_allocator = &g_blob_pool_allocator;
        opt.workspace_allocator = &g_workspace_pool_allocator;
        // Android/ARM has shown NaN outputs on this v2 model. Force the
        // conservative fp32 path first; desktop x86 naturally tends to do this.
        opt.use_fp16_packed = false;
        opt.use_fp16_storage = false;
        opt.use_fp16_arithmetic = false;
#if NCNN_VERSION_CODE >= 20240102
        opt.use_bf16_storage = false;
#endif
#ifdef NCNN_SUPPORT_VULKAN
        opt.use_vulkan_compute = use_gpu;
#endif
        log_info("v2: net opt gpu=%s threads=%d fp16_packed=%d fp16_storage=%d fp16_arithmetic=%d",
                 use_gpu ? "true" : "false",
                 opt.num_threads,
                 opt.use_fp16_packed ? 1 : 0,
                 opt.use_fp16_storage ? 1 : 0,
                 opt.use_fp16_arithmetic ? 1 : 0);
    }

    // pnnx export via ExportWrapper always produces out0/out1/out2.
    // Confirmed by both the C++ OCR server and the actual .param file:
    //   InnerProduct ... out0  0=10  -> digit_left  (10 classes)
    //   InnerProduct ... out1  0=3   -> operator    (3 classes)
    //   InnerProduct ... out2  0=10  -> digit_right (10 classes)
    static void discover_output_names()
    {
        g_out_digit_left  = "out0";
        g_out_operator    = "out1";
        g_out_digit_right = "out2";
        log_info("v2: using output names [out0, out1, out2]");
    }

    static bool load_param_and_bin(const std::string& param_path,
                                   const std::string& bin_path)
    {
        int ret_param = g_net.load_param(param_path.c_str());
        if (ret_param != 0) {
            log_err("v2: load_param failed: %s (ret=%d)", param_path.c_str(), ret_param);
            return false;
        }
        int ret_model = g_net.load_model(bin_path.c_str());
        if (ret_model != 0) {
            log_err("v2: load_model failed: %s (ret=%d)", bin_path.c_str(), ret_model);
            return false;
        }
        return true;
    }

    bool init_from_assets(void* asset_manager_ptr, bool use_gpu)
    {
        if (g_loaded) {
            return true;
        }
        AAssetManager* mgr = static_cast<AAssetManager*>(asset_manager_ptr);
        if (mgr == nullptr) {
            log_err("v2: AAssetManager is null");
            return false;
        }

        // v2 model is currently download-only. Built-in assets are not
        // bundled, so this path is reserved for future use.
        log_err("v2: init_from_assets is not yet supported (model is download-only)");
        (void)mgr;
        return false;
    }

    bool init_from_dir(const std::string& dir_path_in, bool use_gpu)
    {
        std::lock_guard<std::mutex> lock(g_mutex);

        if (g_loaded) {
            return true;
        }

        if (dir_path_in.empty()) {
            log_err("v2: empty model dir");
            return false;
        }

        std::string dir = dir_path_in;
        if (dir.back() != '/' && dir.back() != '\\') {
            dir += '/';
        }

        g_net.clear();
        g_blob_pool_allocator.clear();
        g_workspace_pool_allocator.clear();
        apply_net_opt(use_gpu);

        // v2 file naming convention: <stem>.<precision>.{param,bin}
        // The stem is configured by the Java/Kotlin layer; we accept the
        // fully-qualified param/bin file names via the directory layout
        // "v2/<filename>".  The Java caller will resolve the filenames and
        // place them in dir_path directly.  We try the conventional stem
        // names here in order to keep the C++ layer self-contained.
        static const char* candidate_stems[] = {
            "mobilenet_v3_small.trislot_decoder.v2_0.fp16",
            "mobilenet_v3_small.trislot_decoder.v2_0.fp32"
        };

        bool ok = false;
        for (const char* stem : candidate_stems) {
            std::string param = dir + stem + ".param";
            std::string bin   = dir + stem + ".bin";
            log_info("v2: trying %s + %s", param.c_str(), bin.c_str());
            if (load_param_and_bin(param, bin)) {
                ok = true;
                break;
            }
        }

        // As a last resort, allow the caller to pass absolute paths by
        // dropping the prefix-agnostic filenames. We list the directory and
        // pick the first .param that mentions "v2_0".
        if (!ok) {
            // Minimal best-effort fallback: glob via stat.
            const std::vector<std::string> tries = {
                "mobilenet_v3_small.trislot_decoder.v2_0.fp16.param",
                "mobilenet_v3_small.trislot_decoder.v2_0.fp32.param"
            };
            for (const auto& p : tries) {
                std::string bin = p;
                auto dot = bin.find_last_of('.');
                if (dot != std::string::npos) {
                    bin.replace(dot, std::string::npos, ".bin");
                }
                std::string param_path = dir + p;
                std::string bin_path   = dir + bin;
                if (load_param_and_bin(param_path, bin_path)) {
                    ok = true;
                    break;
                }
            }
        }

        if (!ok) {
            log_err("v2: failed to load any v2 model from %s", dir.c_str());
            return false;
        }

        discover_output_names();

        g_use_gpu = use_gpu;
        g_loaded  = true;
#ifdef NCNN_SUPPORT_VULKAN
        g_status = (use_gpu && ncnn::get_gpu_count() > 0)
                     ? V2_MODEL_STATUS_LOADED_GPU
                     : V2_MODEL_STATUS_LOADED_CPU;
#else
        g_status = V2_MODEL_STATUS_LOADED_CPU;
#endif
        log_info("v2: model loaded from %s (gpu=%s)", dir.c_str(), use_gpu ? "true" : "false");
        return true;
    }

    static int argmax(const ncnn::Mat& m)
    {
        int best = 0;
        for (int i = 1; i < m.w; ++i) {
            if (m[i] > m[best]) best = i;
        }
        return best;
    }

    static std::string logits_to_string(const ncnn::Mat& m)
    {
        std::string s = "[";
        for (int i = 0; i < m.w; ++i) {
            char buf[32];
            std::snprintf(buf, sizeof(buf), i == 0 ? "%.4f" : ",%.4f", m[i]);
            s += buf;
        }
        s += "]";
        return s;
    }

    static bool mat_has_nan(const ncnn::Mat& m)
    {
        for (int i = 0; i < m.w; ++i) {
            if (std::isnan(m[i])) return true;
        }
        return false;
    }

    static const char* mat_type_name(int type)
    {
        switch (type) {
            case CV_8UC1: return "CV_8UC1";
            case CV_8UC3: return "CV_8UC3";
            case CV_8UC4: return "CV_8UC4";
            default: return "UNKNOWN";
        }
    }

    static std::string mat_summary(const cv::Mat& mat)
    {
        if (mat.empty()) return "empty";

        cv::Scalar mean_scalar = cv::mean(mat);
        double min_val = 0.0;
        double max_val = 0.0;
        cv::minMaxLoc(mat.reshape(1), &min_val, &max_val);

        std::ostringstream oss;
        oss << "size=" << mat.cols << "x" << mat.rows
            << " ch=" << mat.channels()
            << " type=" << mat_type_name(mat.type())
            << " mean=";
        oss.setf(std::ios::fixed);
        oss.precision(2);
        oss << mean_scalar[0];
        if (mat.channels() > 1) oss << "/" << mean_scalar[1] << "/" << mean_scalar[2];
        if (mat.channels() > 3) oss << "/" << mean_scalar[3];
        oss << " min=" << min_val
            << " max=" << max_val;
        return oss.str();
    }

    static char op_symbol(V2_OPERATOR op)
    {
        switch (op) {
            case V2_OPERATOR_ADD: return '+';
            case V2_OPERATOR_SUB: return '-';
            case V2_OPERATOR_MUL: return '*';
        }
        return '+';
    }

    static cv::Mat preprocess_v2_input(const cv::Mat& bgr_image_input)
    {
        // Align with training/inference preprocessing in
        // Model/.../common/preprocess.py:
        //   score = 255 - min(B, G, R)
        //   GaussianBlur(3x3)
        //   Otsu binary
        //   resize to 192x64 with nearest interpolation
        cv::Mat score;
        if (bgr_image_input.channels() == 3) {
            std::vector<cv::Mat> channels;
            cv::split(bgr_image_input, channels);
            cv::Mat min_bg;
            cv::min(channels[0], channels[1], min_bg);
            cv::Mat min_bgr;
            cv::min(min_bg, channels[2], min_bgr);
            score = cv::Scalar::all(255) - min_bgr;
        } else if (bgr_image_input.channels() == 4) {
            std::vector<cv::Mat> channels;
            cv::split(bgr_image_input, channels);
            cv::Mat min_bg;
            cv::min(channels[0], channels[1], min_bg);
            cv::Mat min_bgr;
            cv::min(min_bg, channels[2], min_bgr);
            score = cv::Scalar::all(255) - min_bgr;
        } else if (bgr_image_input.channels() == 1) {
            score = bgr_image_input.clone();
        } else {
            cv::Mat gray;
            cv::cvtColor(bgr_image_input, gray, cv::COLOR_BGR2GRAY);
            score = gray;
        }

        cv::Mat blur;
        cv::GaussianBlur(score, blur, cv::Size(3, 3), 0.0);

        cv::Mat binary;
        cv::threshold(blur, binary, 0, 255, cv::THRESH_BINARY | cv::THRESH_OTSU);

        cv::Mat resized;
        cv::resize(binary, resized, cv::Size(V2_INPUT_W, V2_INPUT_H), 0.0, 0.0, cv::INTER_NEAREST);
        return resized;
    }

    std::tuple<int, std::string, int, int, int>
    predict(const cv::Mat& bgr_image_input)
    {
        std::lock_guard<std::mutex> lock(g_mutex);

        if (!g_loaded || g_out_digit_left.empty()) {
            log_err("v2: predict called before successful init");
            return std::make_tuple(0, std::string("0 + 0 = 0"), 0, 0, 0);
        }

        log_info("v2: raw_input %s", mat_summary(bgr_image_input).c_str());
        cv::Mat gray = preprocess_v2_input(bgr_image_input);
        const int white_pixels = cv::countNonZero(gray);
        const int total_pixels = gray.rows * gray.cols;
        const double white_ratio = total_pixels > 0
                                 ? static_cast<double>(white_pixels) / static_cast<double>(total_pixels)
                                 : 0.0;
        log_info("v2: preprocessed %s white_pixels=%d/%d white_ratio=%.3f",
                 mat_summary(gray).c_str(),
                 white_pixels,
                 total_pixels,
                 white_ratio);

        ncnn::Mat in = ncnn::Mat::from_pixels(
                gray.data, ncnn::Mat::PIXEL_GRAY, gray.cols, gray.rows);
        const float norm[1] = { 1.0f / 255.0f };
        in.substract_mean_normalize(nullptr, norm);

        ncnn::Extractor ex = g_net.create_extractor();
        int input_ret = ex.input("in0", in);
        const char* input_name = "in0";
        if (input_ret != 0) {
            log_err("v2: ex.input(in0) failed ret=%d, trying input", input_ret);
            input_ret = ex.input("input", in);
            input_name = "input";
        }
        if (input_ret != 0) {
            log_err("v2: ex.input failed for both in0/input (ret=%d)", input_ret);
            return std::make_tuple(0, std::string("0 + 0 = 0"), 0, 0, 0);
        }
        log_info("v2: ex.input succeeded input_name=%s", input_name);

        ncnn::Mat out_left, out_op, out_right;
        int r0 = ex.extract(g_out_digit_left.c_str(),  out_left);
        int r1 = ex.extract(g_out_operator.c_str(),    out_op);
        int r2 = ex.extract(g_out_digit_right.c_str(), out_right);
        if (r0 != 0 || r1 != 0 || r2 != 0) {
            log_err("v2: extract failed (l=%d op=%d r=%d) names=[%s,%s,%s]",
                    r0, r1, r2,
                    g_out_digit_left.c_str(),
                    g_out_operator.c_str(),
                    g_out_digit_right.c_str());
            return std::make_tuple(0, std::string("0 + 0 = 0"), 0, 0, 0);
        }

        int left  = argmax(out_left);
        int op    = argmax(out_op);
        int right = argmax(out_right);

        int result = 0;
        switch (static_cast<V2_OPERATOR>(op)) {
            case V2_OPERATOR_ADD: result = left + right; break;
            case V2_OPERATOR_SUB: result = left - right; break;
            case V2_OPERATOR_MUL: result = left * right; break;
        }

        char buf[64];
        std::snprintf(buf, sizeof(buf), "%d %c %d = %d",
                      left, op_symbol(static_cast<V2_OPERATOR>(op)),
                      right, result);
        const std::string left_logits = logits_to_string(out_left);
        const std::string op_logits = logits_to_string(out_op);
        const std::string right_logits = logits_to_string(out_right);
        if (mat_has_nan(out_left) || mat_has_nan(out_op) || mat_has_nan(out_right)) {
            log_err("v2: NaN detected in logits_left=%s logits_op=%s logits_right=%s",
                    left_logits.c_str(),
                    op_logits.c_str(),
                    right_logits.c_str());
        }
        log_info("v2: logits_left=%s logits_op=%s logits_right=%s -> %s",
                 left_logits.c_str(),
                 op_logits.c_str(),
                 right_logits.c_str(),
                 buf);
        return std::make_tuple(result, std::string(buf), left, op, right);
    }

    void release()
    {
        std::lock_guard<std::mutex> lock(g_mutex);
        g_net.clear();
        g_blob_pool_allocator.clear();
        g_workspace_pool_allocator.clear();
        g_loaded = false;
        g_status = V2_MODEL_STATUS_NOT_LOADED;
        g_out_digit_left.clear();
        g_out_operator.clear();
        g_out_digit_right.clear();
    }

    bool is_loaded() { return g_loaded; }
    V2_MODEL_STATUS get_status() { return g_status; }
}
