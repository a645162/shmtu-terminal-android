package cn.edu.shmtu.terminal.android.di

import cn.edu.shmtu.terminal.android.data.notification.bot.BotWebhookNotifier
import cn.edu.shmtu.terminal.android.data.notification.bot.CustomWebhookNotifier
import cn.edu.shmtu.terminal.android.data.notification.bot.FeishuBotNotifier
import cn.edu.shmtu.terminal.android.data.notification.bot.WechatWorkBotNotifier
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap
import dagger.multibindings.StringKey

/**
 * Notification DI Module.
 *
 * 1. 显式 `@Binds` 把 3 个具体 notifier 绑定到抽象 [BotWebhookNotifier],
 *    BotManager 通过按需注入的 (concrete class) 解决依赖, 不需要 multibinding.
 * 2. 同时通过 [IntoMap] + [StringKey] 多绑定到一个 `Map<String, BotWebhookNotifier>`,
 *    方便未来按 type 字符串动态查找 (e.g. 插件系统).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class NotificationModule {

    @Binds
    @IntoMap
    @StringKey("FEISHU")
    abstract fun bindFeishuBotNotifier(impl: FeishuBotNotifier): BotWebhookNotifier

    @Binds
    @IntoMap
    @StringKey("WECHAT_WORK")
    abstract fun bindWechatWorkBotNotifier(impl: WechatWorkBotNotifier): BotWebhookNotifier

    @Binds
    @IntoMap
    @StringKey("CUSTOM")
    abstract fun bindCustomWebhookNotifier(impl: CustomWebhookNotifier): BotWebhookNotifier
}
