package cn.edu.shmtu.terminal.android.ui.settings

import androidx.compose.runtime.compositionLocalOf

/**
 * 简化方案: 用 CompositionLocal 暴露 [FeatureSettingsStore],
 * 避免每个 Screen 单独写 hiltViewModel()。
 */
val LocalFeatureStore = compositionLocalOf<FeatureSettingsStore> {
    error("FeatureSettingsStore not provided. Use SettingsCompositionLocalProvider.")
}
