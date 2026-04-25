# Android

## 最新规范

请尽量使用最新的规范

## Jetpack Compose

请尽量使用androidx，Jetpack Compose等谷歌最新的技术！

## UI

请尽量使用Material Design 3！

## 架构

- Clean Architecture 三层分离：UI → Domain → Data
- Hilt 依赖注入
- Room 本地存储
- Kotlin Coroutines + Flow 异步

## 数据库

- 主数据库 `shmtu_terminal`：存储 identities 和 accounts 元数据
- 每个账号独立数据库 `account_{id}_bills`：存储该账号的账单
- 每个身份独立数据库 `identity_{id}_bills`：存储该身份下所有账号的合并账单
- BillDatabaseManager 管理多数据库实例的创建、缓存、关闭和删除
- 账单增量更新：按 transactionNo 判断已存在条目，遇到即停止获取
- 新增账单同步写入账号数据库和身份数据库

## 模块

- `app/` — 海事终端 Android 应用（可修改）
- `cas_lib/` — CAS 认证接口库（纯 JVM，仅接口实现，可修改）
- `cas_demo/` — CAS 接口验证 CLI（纯 JVM 可运行，含 main/demo/测试）
- `demo/` — OCR Demo（不可修改）
- `shmtu_ocr/` — OCR 核心实现（不可修改）

## 密码存储

- 密码使用 EncryptedSharedPreferences 存储，key 格式：`account_password_{accountId}`
- 绝不将密码明文写入 Room 数据库或日志
