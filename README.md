# ChronoText · 时光短信

一款 Android 定时短信应用：到点自动用你的手机给重要的人发短信。适用于每年给女朋友发生日祝福、纪念日提醒、定期问候等场景。

## 功能

- **四种周期**：单次 / 每年公历 / 每年农历（支持闰月，闰月缺失年份可自动落普通月）/ 每 N 天
- **双卡可选**：自动识别 SIM 卡，每个任务可指定用哪张卡发送
- **通讯录选人**：调用系统联系人选择器，支持搜索
- **自动重试**：发送失败自动间隔 5 分钟重试，最多 3 次，仍失败弹通知提醒
- **发送前提醒**：可配置提前 1 天 / 3 天通知你
- **发送记录**：每次发送（含重试、跳过）全程留痕
- **可靠触发**：
  - 精确闹钟（支持息屏触发），失败自动降级非精确
  - 开机 / 覆盖安装 / 系统时间或时区变化后自动重排全部任务
  - 错过触发窗口超过 1 小时视为过期，跳过并记录，不发"过期祝福"
- **农历纯本地换算**：基于 [lunar](https://github.com/6tail/lunar-java) 库离线计算，支持公元 1—9999 年，永不联网

## 重要说明

- 本应用**仅供个人侧载使用**。Google Play 等商店禁止普通应用持有 `SEND_SMS` 权限，因此不适用商店分发；自装 APK 不受影响。
- 短信按运营商正常资费计费。
- 国产 ROM（MIUI / EMUI / ColorOS 等）会杀后台，首次使用请按应用内引导完成：运行时权限、精确闹钟权限、电池优化白名单。

## 构建

要求：JDK 17+、Android SDK（含 platform android-36）。

```bash
# 在 local.properties 配置 sdk.dir 后
./gradlew assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

依赖通过阿里云镜像解析（`settings.gradle.kts`），无需代理。

## 技术栈

Kotlin · Jetpack Compose (Material 3) · Room · AlarmManager · SmsManager（按 SubscriptionId 双卡） · lunar（农历）
