<h1 align="center">Seedream 302 Android</h1>

<p align="center">
  <a href="README.md">简体中文</a> |
  <a href="README.en.md">English</a>
</p>

Seedream 302 Android 是一个原生 Android 图像生成与编辑工具，基于 Kotlin、Jetpack Compose、Room、Android Keystore 和前台生成服务实现。

本项目最开始参考了 302.ai 官方文档：

- [Seedream 5.0 Pro 图像生成 API](https://s.apifox.cn/apidoc/docs-site/4012774/484942949e0)
- [Seedream 5.0 图像生成 API](https://s.apifox.cn/apidoc/docs-site/4012774/419295548e0)
- [Seedream 4.5 图像生成 API](https://s.apifox.cn/apidoc/docs-site/4012774/385925488e0)

## 功能

- 支持 Seedream 5.0 Pro / 5.0 / 4.5 模型选择。
- 支持 Android 7.0 及以上系统（minSdk 24）。
- API Key 保存、显示/隐藏、清空，并通过 Android Keystore 加密存储。
- 接口地址可编辑，支持延迟测试。
- 支持 Prompt、本地多图参考、URL 参考图、排序和删除。
- 支持 `size`、`seed`、`response_format`、`watermark`、`stream`、`sequential_image_generation`、`max_images`、5.0 / 5.0 Pro 联网搜索等参数。
- 5.0 Pro 与 5.0 调用方式相同，仅替换 `model` 为 `doubao-seedream-5-0-pro-260628`（官网说明）。
- 支持外部联网搜索：Tavily、Brave Search、Bing Web Search、DuckDuckGo；搜索结果会在发送到 302.ai 前注入 Prompt。
- 请求期间使用前台服务，通知栏支持停止；网络错误和 5xx 错误支持重试。
- 支持普通 JSON 和 SSE 流式响应解析。
- 结果图片可预览、全屏查看、保存到 `Pictures/Seedream`。
- 使用 Room 保存历史记录，并将生成图片缓存到应用私有目录。
- 底部标签页界面：创作、结果、历史、调试。

## 构建

需要安装：

- JDK 17
- Android SDK API 37 和 Build Tools 37.0.0
- Android Studio 或项目自带 Gradle Wrapper

如果 Android Studio 没有自动生成 `local.properties`，请在本地创建：

```properties
sdk.dir=C\:\\Android\\Sdk
```

构建命令：

```powershell
.\env.ps1
.\gradlew.bat test assembleDebug
```

Debug APK 输出位置：

```text
app/build/outputs/apk/debug/app-debug.apk
```

`local.properties`、生成的 APK/AAB、签名密钥和本地环境文件不会提交到 Git。

## 版本与发行版

App 版本号在 `gradle.properties` 中维护：

```properties
APP_VERSION_CODE=10
APP_VERSION_NAME=1.1.8
```

每次发布前都要递增 `APP_VERSION_CODE`，并让 `APP_VERSION_NAME` 与 Git 标签一致，例如 `v1.1.8` 对应 `APP_VERSION_NAME=1.1.8`。

推送 `v*` 标签后，`.github/workflows/android-release.yml` 会在 GitHub Actions 中构建 APK，并上传到 GitHub Releases。详细流程见 [docs/RELEASE.md](docs/RELEASE.md)。

本地构建后须把对应版本 APK 备份到 `releases/apk/`（命名如 `Seedream-302-v1.1.8-debug.apk`），但该目录下的 APK 不应提交到源码仓库；正式分发请使用 GitHub Releases。

如果 `v1.1.8` 的 APK 没有出现在 GitHub Release，请检查标签是否已经推送：

```powershell
git push origin v1.1.8
```

如果标签已经存在但没有触发新的 workflow，可以删除远端旧标签后重新推送：

```powershell
git push origin :refs/tags/v1.1.8
git tag -d v1.1.8
git tag v1.1.8
git push origin v1.1.8
```

也可以手动把本地 APK 上传到已有发行版：

```powershell
gh release upload v1.1.8 app\build\outputs\apk\debug\app-debug.apk --repo OneGemmj/302AISeedreamAPP --clobber
```
