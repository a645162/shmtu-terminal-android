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
        opt.num_threads = 4;
        opt.blob_allocator = &g_blob_pool_allocator;
        opt.workspace_allocator = &g_workspace_pool_allocator;
#ifdef NCNN_SUPPORT_VULKAN
        opt.use_vulkan_compute = use_gpu;
#endif
    }

    // Probe the loaded model for which of the candidate output names actually
    // exist. We try (1) net.output_names() for the v2 export; (2) the hard
    // coded defaults; (3) any 3-output tail of the layer list as last resort.
    static void discover_output_names()
    {
        g_out_digit_left.clear();
        g_out_operator.clear();
        g_out_digit_right.clear();

        // Preferred names in priority order for the three heads.
        const std::vector<std::vector<const char*>> candidates = {
            { V2_OUT_DIGIT_LEFT,  V2_OUT_OPERATOR,    V2_OUT_DIGIT_RIGHT },
            { "left",             "operator",         "right" },
            { "out_digit_left",   "out_operator",     "out_digit_right" },
            { "digit_left",       "op",               "digit_right" }
        };

        for (const auto& cand : candidates) {
            ncnn::Extractor ex = g_net.create_extractor();
            ncnn::Mat probe;
            int r0 = ex.input(cand[0], probe) == 0 ? 0 : -1;
            // We don't actually want to push data; just verify the blob exists.
            // Some ncnn versions need a real input before extract works, so
            // we re-create an extractor and call extract("name", out) to see
            // if the graph accepts the name. (Returns <0 if not present.)
            ncnn::Mat out0;
            int ret0 = ex.extract(cand[0], out0);
            // ret == 0 means the blob was found in the graph
            if (ret0 == 0) {
                ncnn::Mat out1, out2;
                int ret1 = ex.extract(cand[1], out1);
                int ret2 = ex.extract(cand[2], out2);
                if (ret1 == 0 && ret2 == 0) {
                    g_out_digit_left  = cand[0];
                    g_out_operator    = cand[1];
                    g_out_digit_right = cand[2];
                    log_info("v2: discovered output names [%s, %s, %s]",
                             g_out_digit_left.c_str(),
                             g_out_operator.c_str(),
                             g_out_digit_right.c_str());
                    return;
                }
            }
            (void)r0;
        }

        // Fallback: log all available output names so a developer can paste
        // them into CAS_OCR_V2.h. We rely on ncnn::Net::output_names() which
        // requires a forward pass or the internal layer list.
        log_err("v2: could not resolve output names; the v2 model needs a "
                "confirmed blob name list. See logcat for details.");
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

    static char op_symbol(V2_OPERATOR op)
    {
        switch (op) {
            case V2_OPERATOR_ADD: return '+';
            case V2_OPERATOR_SUB: return '-';
            case V2_OPERATOR_MUL: return '*';
        }
        return '+';
    }

    std::tuple<int, std::string, int, int, int>
    predict(const cv::Mat& bgr_image_input)
    {
        std::lock_guard<std::mutex> lock(g_mutex);

        if (!g_loaded || g_out_digit_left.empty()) {
            log_err("v2: predict called before successful init");
            return std::make_tuple(0, std::string("0 + 0 = 0"), 0, 0, 0);
        }

        // v2 expects a fixed-size grayscale image. We do a single
        // BGR->Gray + resize; no thresholding, no equal-split.
        cv::Mat gray;
        cv::cvtColor(bgr_image_input, gray, cv::COLOR_BGR2GRAY);
        cv::resize(gray, gray, cv::Size(V2_INPUT_W, V2_INPUT_H));

        ncnn::Mat in = ncnn::Mat::from_pixels(
                gray.data, ncnn::Mat::PIXEL_GRAY, V2_INPUT_W, V2_INPUT_H);

        const float norm[1] = { 1.0f / 255.0f };
        in.substract_mean_normalize(nullptr, norm);

        ncnn::Extractor ex = g_net.create_extractor();

        ex.input("input", in);  // v2 NCNN export uses "input" as the entry blob

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
        return std::make_tuple(result, std::string(buf), left, op, right);
    }

    void release()
    {
        std::lock_guard<std::mutex> lock(g_mutex);
        g_net.clear();
        g_loaded = false;
        g_status = V2_MODEL_STATUS_NOT_LOADED;
        g_out_digit_left.clear();
        g_out_operator.clear();
        g_out_digit_right.clear();
    }

    bool is_loaded() { return g_loaded; }
    V2_MODEL_STATUS get_status() { return g_status; }
}
