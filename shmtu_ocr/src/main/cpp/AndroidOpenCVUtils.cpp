//
// Created by konghaomin on 2024/2/11.
//

#include "AndroidOpenCVUtils.h"

#include <android/log.h>

extern std::string logcat_tag;

// OpenCV
#include <opencv2/imgproc.hpp>

cv::Mat convertBitmapToMat(JNIEnv *env, jobject thiz, jobject bitmap) {
    AndroidBitmapInfo info;
    void *pixels;

    // 获取 Bitmap 信息
    AndroidBitmap_getInfo(env, bitmap, &info);
    __android_log_print(
            ANDROID_LOG_INFO,
            logcat_tag.c_str(),
            "bitmap: width=%u height=%u stride=%u format=%d flags=%u",
            info.width,
            info.height,
            info.stride,
            info.format,
            info.flags
    );

    // 锁定 Bitmap 并获取像素数据
    AndroidBitmap_lockPixels(env, bitmap, &pixels);

    // Android Bitmap is RGBA_8888. Copy before unlock, then convert to the
    // BGR 3-channel layout expected by the native OCR pipelines.
    cv::Mat rgba(info.height, info.width, CV_8UC4, pixels);
    cv::Mat rgba_copy = rgba.clone();

    // 解锁 Bitmap
    AndroidBitmap_unlockPixels(env, bitmap);

    cv::Mat bgr;
    cv::cvtColor(rgba_copy, bgr, cv::COLOR_RGBA2BGR);
    return bgr;
}
