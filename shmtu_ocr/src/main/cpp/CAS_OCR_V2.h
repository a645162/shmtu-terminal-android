//
// CAS_OCR_V2.h
//
// v2 OCR engine: single NCNN model (TriSlot Decoder) that takes a grayscale
// 64x192 image and returns digit_left, operator, digit_right in one forward pass.
//
// Preprocessing: BGR -> Gray -> resize(192x64) -> [0,1] normalization.
// No equal-sign splitting, no per-head cropping, no BGR ImageNet mean/std.
//
// Native side uses a self-contained ncnn::Net to avoid colliding with the
// v1 engine's three independent networks (net_equal_symbol/net_operator/net_digit).
//

#pragma once

#include <string>
#include <tuple>
#include <opencv2/core.hpp>

namespace CAS_OCR_V2
{
    enum V2_MODEL_STATUS
    {
        V2_MODEL_STATUS_NOT_LOADED = 0,
        V2_MODEL_STATUS_LOADED_CPU = 1,
        V2_MODEL_STATUS_LOADED_GPU = 2
    };

    /** Operator class index returned by v2 head_logits. */
    enum V2_OPERATOR
    {
        V2_OPERATOR_ADD = 0,
        V2_OPERATOR_SUB = 1,
        V2_OPERATOR_MUL = 2
    };

    // v2 input geometry is fixed: 1x64x192 grayscale normalized to [0,1].
    constexpr int V2_INPUT_H = 64;
    constexpr int V2_INPUT_W = 192;

    // v2 outputs the three heads. 10 digits for each side, 3 operator classes.
    constexpr int V2_NUM_DIGITS = 10;
    constexpr int V2_NUM_OPERATORS = 3;

    // Known output blob names of the v2 NCNN export. The first existing blob
    // in the param file is used. We try this list in order; the first that
    // exists in the model is kept for subsequent calls.
    // NOTE: confirm on a real device and paste the actual names into the
    // project notes.
    constexpr const char* V2_OUT_DIGIT_LEFT  = "digit_left_logits";
    constexpr const char* V2_OUT_OPERATOR    = "operator_logits";
    constexpr const char* V2_OUT_DIGIT_RIGHT = "digit_right_logits";

    // Initialization (Android entry-points). The asset manager variant loads
    // the model from APK assets; the dir variant loads from the app's
    // filesDir (ncnn_model/v2/...).
    bool init_from_assets(void* asset_manager, bool use_gpu);
    bool init_from_dir(const std::string& dir_path, bool use_gpu);

    // Returns:
    //   get<0>: result = left op right
    //   get<1>: expression string "left op right = result"
    //   get<2>: left digit (0..9)
    //   get<3>: operator (0=+, 1=-, 2=*)
    //   get<4>: right digit (0..9)
    std::tuple<int, std::string, int, int, int>
    predict(const cv::Mat& bgr_image_input);

    void release();

    bool is_loaded();

    V2_MODEL_STATUS get_status();
}
