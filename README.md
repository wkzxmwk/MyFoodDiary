# 饮食体重记录 · FoodDiary

中文 Android 单机应用，通过照片记录早餐、午餐、晚餐和加餐，并记录早晚体重、查看趋势。

当前版本：`1.2.0`（versionCode `3`），应用标识：`com.wkzxmwk.fooddiary`。最低支持 Android 8.0 / API 26。

## 功能

- 今日饮食照片、缩略图区域选择、完整图片查看与每日饮食拼图分享。
- 历史记录按周、月和自定义日期范围筛选，支持备注和记录编辑。
- 早晚体重、日平均、体重趋势和 BMI 数值。
- 目标体重、身高、性别、图片压缩质量和自定义每日提醒。
- 本地 ZIP 备份导出、导入恢复及数据清除。

数据默认保存在设备本地；应用未声明 INTERNET 权限，不使用服务器或登录账号。

## 技术栈

Kotlin、Jetpack Compose、Material 3、Navigation Compose、Room、DataStore、ViewModel 和 StateFlow。采用单 Activity 和手工依赖容器。

## 构建

使用 Android Studio 打开项目，安装项目 Gradle 配置要求的 Android SDK，并配置本机 JDK 和 SDK 路径。`local.properties` 不纳入版本管理。

Windows：

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug
```

Debug APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。正式发布需使用原有发布签名；签名密钥、密码和 APK/AAB 不保存在源码仓库中。

## 开发交接

详细分层、关键文件职责、图片存储、备份格式、提醒调度和功能扩展入口见 [项目架构与设计说明](PROJECT_ARCHITECTURE.md)。

`design-reference/` 保存 UI 与图标设计参考。`app/schemas/` 保存 Room 历史结构，数据库变更时应维护迁移。

本应用仅用于个人记录，不提供医疗诊断或治疗建议。
