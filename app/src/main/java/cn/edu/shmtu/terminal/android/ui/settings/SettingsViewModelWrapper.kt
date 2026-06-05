package cn.edu.shmtu.terminal.android.ui.settings

import androidx.lifecycle.ViewModel
import cn.edu.shmtu.terminal.android.data.sync.BillRulesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * SettingsScreen 依赖 wrapper - 用 Hilt 注入 FeatureSettingsStore 与 BillRulesManager。
 */
@HiltViewModel
class SettingsViewModelWrapper @Inject constructor(
    val featureStore: FeatureSettingsStore,
    val rulesManager: BillRulesManager
) : ViewModel()
