package cn.edu.shmtu.cas.ocr;

import android.content.Context;
import android.content.res.AssetManager;

import java.io.File;

public class SHMTU_NCNN_Model {
    public enum ModelSource {
        GITEE,
        GITHUB
    }

    /**
     * Engine generation. v1 = 3 separate resnet models (equal/operator/digit).
     * v2 = single TriSlot-decoder NCNN that returns (left, op, right) in one
     * forward pass. v2 is the default.
     */
    public enum ModelVersion {
        V1,
        V2;

        public static ModelVersion fromString(String s) {
            if (s == null) return V2;
            switch (s.toLowerCase()) {
                case "v1": case "1": return V1;
                case "v2": case "2": return V2;
                default: return V2;
            }
        }

        public String toStorageString() {
            return name();  // "V1" / "V2"
        }
    }

    public interface LoadCallback {
        void onSuccess();
        void onError(String error);
    }

    // ============ v1 (legacy) source URLs ============
    public static final String URL_MODEL_PREFIX_GITEE
            = "https://gitee.com/a645162/shmtu-cas-ocr-model/releases/download/v1.0-NCNN/";
    public static final String URL_MODEL_PREFIX_GITHUB
            = "https://github.com/a645162/shmtu-cas-ocr-model/releases/download/v1.0-NCNN/";

    public static final String FILE_NAME_MODEL_EQUAL_SYMBOL_BIN
            = "resnet18_equal_symbol_latest.fp16.bin";
    public static final String FILE_NAME_MODEL_EQUAL_SYMBOL_PARAM
            = "resnet18_equal_symbol_latest.fp16.param";
    public static final String FILE_NAME_MODEL_OPERATOR_BIN
            = "resnet18_operator_latest.fp16.bin";
    public static final String FILE_NAME_MODEL_OPERATOR_PARAM
            = "resnet18_operator_latest.fp16.param";
    public static final String FILE_NAME_MODEL_DIGIT_BIN
            = "resnet34_digit_latest.fp16.bin";
    public static final String FILE_NAME_MODEL_DIGIT_PARAM
            = "resnet34_digit_latest.fp16.param";

    public static final String CHECKSUM_FILENAME = "SHA256SUMS.txt";

    /** v1 has 6 files: 3 resnet models × {param, bin}. */
    public static final String[] MODEL_FILES = {
            FILE_NAME_MODEL_EQUAL_SYMBOL_BIN,
            FILE_NAME_MODEL_EQUAL_SYMBOL_PARAM,
            FILE_NAME_MODEL_OPERATOR_BIN,
            FILE_NAME_MODEL_OPERATOR_PARAM,
            FILE_NAME_MODEL_DIGIT_BIN,
            FILE_NAME_MODEL_DIGIT_PARAM
    };

    // ============ v2 (default) ============
    public static final String V2_DEFAULT_BACKBONE = "mobilenet_v3_small";
    public static final String V2_DEFAULT_PRECISION = "fp16";
    public static final String V2_DEFAULT_TAG = "v2.0.5";
    /**
     * 主版本号锁:maxSupportedMajor. 改这个常量相当于声明客户端支持哪些 major。
     * 当前支持 v2 全部 (2.x.x),不想支持 v3 时保持 2。
     */
    public static final int V2_MAX_SUPPORTED_MAJOR = 2;

    /**
     * MINOR 上界:
     *  * 0 或正数 N = 锁 major+minor (v2.0.x 或 v2.x.x, 0<=x<=N)
     *  * Int.MIN_VALUE = 不限 minor,只锁 major (v2.x.x 全范围)
     * 当前用户要求"小于 3 就可以" → 设成 Int.MIN_VALUE (支持 v2.0.x ~ v2.x.x)
     */
    public static final int V2_MAX_SUPPORTED_MINOR = Integer.MIN_VALUE;

    /**
     * 最低支持的主版本号。低于此版本的 tag 不会出现在候选列表,也不允许手动选择。
     */
    public static final int V2_MIN_SUPPORTED_MAJOR = 2;
    /**
     * 最低支持的次版本号。
     */
    public static final int V2_MIN_SUPPORTED_MINOR = 0;
    /**
     * 最低支持的补丁版本号。
     * v2.0.0 ~ v2.0.3 已删除或存在兼容性问题，最低要求 v2.0.4。
     */
    public static final int V2_MIN_SUPPORTED_PATCH = 4;

    public static final String GITHUB_REPO = "a645162/shmtu-cas-ocr-model";
    public static final String GITHUB_RELEASES_API =
            "https://api.github.com/repos/a645162/shmtu-cas-ocr-model/releases";
    public static final String GITEE_RELEASES_API =
            "https://gitee.com/api/v5/repos/a645162/shmtu-cas-ocr-model/releases";

    public static final String V2_URL_MODEL_PREFIX_GITHUB
            = "https://github.com/a645162/shmtu-cas-ocr-model/releases/download/";
    public static final String V2_URL_MODEL_PREFIX_GITEE
            = "https://gitee.com/a645162/shmtu-cas-ocr-model/releases/download/";

    public static final String V2_MANIFEST_FILENAME = "model-assets.json";

    /**
     * Build the v2 .param / .bin filenames from a (backbone, precision) pair.
     */
    public static String[] getV2ModelFiles(String backbone, String precision) {
        String stem = backbone + ".trislot_decoder.v2_0." + precision;
        return new String[] { stem + ".param", stem + ".bin" };
    }

    /**
     * Returns the v2 asset stem prefix used both for the v2 manifest lookup
     * and for filesystem layout.
     */
    public static String v2AssetStem(String backbone, String precision) {
        return backbone + ".trislot_decoder.v2_0." + precision;
    }

    // ============ Directory layout ============
    // filesDir/ncnn_model/        -> legacy v1 location (pre-v1/v2 split)
    // filesDir/ncnn_model/v1/     -> v1 model files
    // filesDir/ncnn_model/v2/     -> v2 model files

    public static String getModelDir(Context context) {
        return getModelDir(context, ModelVersion.V2);
    }

    public static String getModelDir(Context context, ModelVersion version) {
        File base = new File(context.getFilesDir().getAbsolutePath(), "ncnn_model");
        if (version == ModelVersion.V1) {
            File v1Dir = new File(base, "v1");
            if (v1Dir.isDirectory()) {
                return v1Dir.getAbsolutePath() + "/";
            }
            // Backward compatibility: legacy v1 files live directly in
            // filesDir/ncnn_model/ if the v1/ sub-directory does not exist.
            if (isLegacyV1LayoutPresent(base)) {
                return base.getAbsolutePath() + "/";
            }
            return v1Dir.getAbsolutePath() + "/";
        }
        File v2Dir = new File(base, "v2");
        return v2Dir.getAbsolutePath() + "/";
    }

    private static boolean isLegacyV1LayoutPresent(File base) {
        for (String name : MODEL_FILES) {
            File f = new File(base, name);
            if (f.exists() && f.length() > 0) return true;
        }
        return false;
    }

    public static String getModelFilePath(Context context, String fileName) {
        return getModelDir(context) + fileName;
    }

    // ============ v1 URL / checksum helpers (unchanged) ============
    public static String[] buildModelUrls(ModelSource source) {
        String prefix = (source == ModelSource.GITHUB) ? URL_MODEL_PREFIX_GITHUB : URL_MODEL_PREFIX_GITEE;
        String[] urls = new String[MODEL_FILES.length];
        for (int i = 0; i < MODEL_FILES.length; i++) {
            urls[i] = prefix + MODEL_FILES[i];
        }
        return urls;
    }

    public static String buildChecksumUrl(ModelSource source) {
        String prefix = (source == ModelSource.GITHUB) ? URL_MODEL_PREFIX_GITHUB : URL_MODEL_PREFIX_GITEE;
        return prefix + CHECKSUM_FILENAME;
    }

    // ============ v1 built-in / downloaded detection (kept for back-compat) ============
    public static boolean isModelBuiltIn(AssetManager assetManager) {
        return isModelBuiltIn(assetManager, ModelVersion.V1);
    }

    public static boolean isModelBuiltIn(AssetManager assetManager, ModelVersion version) {
        // v2 is download-only; v1 may still ship inside the APK for legacy
        // builds that embedded the model.
        if (version == ModelVersion.V2) {
            return false;
        }
        try {
            String[] files = assetManager.list("");
            if (files == null) return false;
            for (String fileName : MODEL_FILES) {
                boolean found = false;
                for (String asset : files) {
                    if (asset.equals(fileName)) {
                        found = true;
                        break;
                    }
                }
                if (!found) return false;
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isModelDownloaded(Context context) {
        return isModelDownloaded(context, ModelVersion.V2);
    }

    public static boolean isModelDownloaded(Context context, ModelVersion version) {
        String modelDir = getModelDir(context, version);
        String[] files = (version == ModelVersion.V1)
                ? MODEL_FILES
                : getV2ModelFiles(V2_DEFAULT_BACKBONE, V2_DEFAULT_PRECISION);
        for (String fileName : files) {
            File file = new File(modelDir + fileName);
            if (!file.exists() || file.length() == 0) {
                return false;
            }
        }
        return true;
    }

    public static String getDownloadedModelInfo(Context context) {
        return getDownloadedModelInfo(context, ModelVersion.V2);
    }

    public static String getDownloadedModelInfo(Context context, ModelVersion version) {
        String modelDir = getModelDir(context, version);
        String[] files = (version == ModelVersion.V1)
                ? MODEL_FILES
                : getV2ModelFiles(V2_DEFAULT_BACKBONE, V2_DEFAULT_PRECISION);
        StringBuilder info = new StringBuilder();
        for (String fileName : files) {
            File file = new File(modelDir + fileName);
            long size = file.exists() ? file.length() : 0;
            info.append(fileName).append(": ").append(size).append(" bytes\n");
        }
        return info.toString();
    }

    public static int deleteDownloadedModels(Context context) {
        return deleteDownloadedModels(context, ModelVersion.V2);
    }

    public static int deleteDownloadedModels(Context context, ModelVersion version) {
        String modelDir = getModelDir(context, version);
        String[] files = (version == ModelVersion.V1)
                ? MODEL_FILES
                : getV2ModelFiles(V2_DEFAULT_BACKBONE, V2_DEFAULT_PRECISION);
        int deleted = 0;
        for (String fileName : files) {
            File file = new File(modelDir + fileName);
            if (file.exists() && file.delete()) {
                deleted++;
            }
        }
        File dir = new File(modelDir);
        File[] remaining = dir.listFiles();
        if (remaining != null && remaining.length == 0) {
            dir.delete();
        }
        return deleted;
    }

    // ============ v1 async loaders (unchanged) ============
    public static void loadModelFromAssetsAsync(SHMTU_NCNN ncnn, AssetManager assetManager, boolean useGpu, LoadCallback callback) {
        new Thread(() -> {
            try {
                boolean success = ncnn.InitModel(assetManager, useGpu);
                if (success) {
                    callback.onSuccess();
                } else {
                    callback.onError("Failed to load model from assets");
                }
            } catch (Exception e) {
                callback.onError(e.getMessage());
            }
        }).start();
    }

    public static void loadModelFromDirAsync(SHMTU_NCNN ncnn, Context context, boolean useGpu, LoadCallback callback) {
        new Thread(() -> {
            try {
                ModelVersion v = ModelVersion.V1;
                String modelDir = getModelDir(context, v);
                for (String fileName : MODEL_FILES) {
                    File file = new File(modelDir + fileName);
                    if (!file.exists() || file.length() == 0) {
                        callback.onError("Model file missing or empty: " + fileName);
                        return;
                    }
                }
                boolean success = ncnn.InitModelFromDir(modelDir, useGpu);
                if (success) {
                    callback.onSuccess();
                } else {
                    callback.onError("Failed to load model from " + modelDir);
                }
            } catch (Exception e) {
                callback.onError(e.getMessage());
            }
        }).start();
    }

    // ============ v2 async loaders ============
    public static void loadV2ModelFromDirAsync(SHMTU_NCNN ncnn, Context context, boolean useGpu, LoadCallback callback) {
        new Thread(() -> {
            try {
                ModelVersion v = ModelVersion.V2;
                String modelDir = getModelDir(context, v);
                String[] files = getV2ModelFiles(V2_DEFAULT_BACKBONE, V2_DEFAULT_PRECISION);
                for (String fileName : files) {
                    File file = new File(modelDir + fileName);
                    if (!file.exists() || file.length() == 0) {
                        callback.onError("V2 model file missing or empty: " + fileName);
                        return;
                    }
                }
                boolean success = ncnn.InitV2ModelFromDir(modelDir, useGpu);
                if (success) {
                    callback.onSuccess();
                } else {
                    callback.onError("Failed to load v2 model from " + modelDir);
                }
            } catch (Exception e) {
                callback.onError(e.getMessage());
            }
        }).start();
    }
}
