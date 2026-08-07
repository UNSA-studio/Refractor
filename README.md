# Refractor

一个基于 WebRTC 的免费低延迟直播 / 屏幕共享 Android 应用，界面为中文（zh-CN）。无需注册，无隐藏费用。

## 功能

- 🎬 **主播端（Broadcaster）**：通过 MediaProjection 屏幕捕获 + 麦克风采集，以 720x1280@30 推流
- 👀 **观众端（Viewer）**：加入房间实时观看
- 💬 **房间聊天**：通过信令通道收发消息
- 🎨 **主题自定义**：多种主题色 + 动态取色（Material You）
- 📱 **无需注册**：输入房间码即可加入

## 技术栈

| 组件 | 技术 |
|---|---|
| 语言 | Kotlin 2.1.0 |
| UI | Jetpack Compose (Material3, BOM 2024.12.01) |
| 导航 | Navigation Compose |
| WebRTC | `io.getstream:stream-webrtc-android`（`org.webrtc.*`） |
| 信令 | Java-WebSocket（WebSocket + HTTP） |
| 构建 | AGP 8.9.0 / Gradle 9.2.1 / JDK 17 |
| 最低系统 | Android 8.0（minSdk 28），目标 36 |

## 架构

- **信令服务器**：外部服务，硬编码为 `rfr-sl.cc.cd`（WebSocket `wss://rfr-sl.cc.cd/room/{roomId}` + HTTP `/create`、`/check/{roomId}`），**不在本仓库内**
- **信令协议**：JSON 消息，`type` ∈ `join` / `ping` / `pong` / `signal` / `user-joined` / `user-left` / `chat` / `error`，心跳每 30s
- **媒体传输**：主播发送 Offer，观众应答；ICE 候选通过信令通道交换（STUN: Google + Cloudflare，无 TURN）
- **捕获服务**：`ScreenCaptureService`（前台 `mediaProjection`）+ `AudioCaptureService`（前台 `microphone|mediaProjection`）

### 项目结构

```
app/src/main/java/unsa/rfr/com/
├── MainActivity.kt          # 所有 Compose 页面 + 导航
├── App.kt                   # 全局崩溃处理（写入 Downloads）
├── SignalingClient.kt       # 信令 WebSocket + 房间 HTTP 接口
├── RfrIdGenerator.kt        # 房间码生成
├── RefractorLog.kt          # 日志（cacheDir/refractor_log.txt）
├── webrtc/WebRtcManager.kt  # PeerConnection / Offer / Answer / ICE
├── capture/ScreenCaptureService.kt
├── audio/                   # 音频采集
└── ui/                      # Compose 页面（screens + theme）
```

## 构建（GitHub Actions）

本项目**推荐通过 GitHub Actions 构建**，无需本地 Android 环境。`.github/workflows/build.yml` 会在推送到 `main` 分支时自动构建 debug APK。

### 方式一：手动触发

1. 打开仓库的 **Actions** 页面
2. 选择 **Build Android APK** 工作流
3. 点击 **Run workflow** → **Run workflow**（无需本地代码）
4. 等待构建完成后，进入该次运行页面，在底部 **Artifacts** 区域下载 `Refractor-debug-APK`

### 方式二：推送到 main 自动构建

```bash
git push origin main
```

推送后进入 Actions 页面查看自动构建结果，同样在 Artifacts 下载 APK。

> APK 输出路径：`app/build/outputs/apk/debug/app-debug.apk`（debug 构建使用仓库内固定的 `debug.keystore` 签名）。

## 本地构建（可选）

需要 JDK 17 与 Android SDK（通过 `ANDROID_HOME` 或 `local.properties` 指定）：

```bash
./gradlew assembleDebug
```

## 声明

- 信令服务器为外部服务，其部署不在本仓库范围内；如需自建请修改 `SignalingClient.kt` 中的地址
- 项目无自动化测试，验证方式为编译通过
