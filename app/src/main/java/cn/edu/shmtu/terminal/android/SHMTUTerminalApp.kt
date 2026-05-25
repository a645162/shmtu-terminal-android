package cn.edu.shmtu.terminal.android

import android.app.Application
import android.util.Log
import cn.edu.shmtu.terminal.android.data.remote.SessionExpirationWorker
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class SHMTUTerminalApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // 启动定期 session 过期检查（默认 10 分钟间隔，±1 分钟浮动）
        SessionExpirationWorker.schedule(this, intervalMinutes = 10)
        Log.i("SHMTUTerminalApp", "Session 过期检查已启动")
    }
}
