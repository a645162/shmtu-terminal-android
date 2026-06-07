package cn.edu.shmtu.terminal.android.data.sync

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import cn.edu.shmtu.terminal.android.data.p2p.P2PForegroundService
import cn.edu.shmtu.terminal.android.ui.settings.FeatureSettingsStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutoSyncStatusNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: FeatureSettingsStore
) {
    fun refresh() {
        if (store.autoSyncPersistentNotificationValue()) {
            P2PForegroundService.start(context)
        } else {
            NotificationManagerCompat.from(context).cancel(P2PForegroundService.NOTIFICATION_ID)
        }
    }
}
