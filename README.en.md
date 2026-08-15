<h1 align="center">SD302</h1>

<p align="center">
  <a href="README.md">简体中文</a> |
  <a href="README.en.md">English</a>
</p>

SD302 is a native Android image generation and editing app built with Kotlin, Jetpack Compose, Room, Android Keystore, and a foreground generation service.

The first implementation referenced the official 302.ai documentation:

- [Seedream 5.0 Pro Image Generation API](https://s.apifox.cn/apidoc/docs-site/4012774/484942949e0)
- [Seedream 5.0 Image Generation API](https://s.apifox.cn/apidoc/docs-site/4012774/419295548e0)
- [Seedream 4.5 Image Generation API](https://s.apifox.cn/apidoc/docs-site/4012774/385925488e0)

## Features

- Seedream 5.0 Pro / 5.0 / 4.5 model selection.
- Android 7.0 and later support (minSdk 24).
- API key save, show/hide, clear, encrypted with Android Keystore.
- Editable API endpoint and latency test.
- Prompt input, multiple local reference images, URL reference images, sorting and deletion.
- Payload options for `size`, `seed`, `response_format`, `watermark`, `stream`, `sequential_image_generation`, `max_images`, and 5.0 / 5.0 Pro web search.
- Seedream 5.0 Pro uses the same call shape as 5.0; only the `model` field changes to `doubao-seedream-5-0-pro-260628` (official guidance).
- External web search via Tavily, Brave Search, Bing Web Search, or DuckDuckGo; search summaries are injected into the prompt before sending requests to 302.ai.
- Foreground service while generating, notification stop action, retry for 5xx/network failures.
- JSON and SSE response parsing.
- Result preview, fullscreen view, save to `Pictures/Seedream`.
- Local history with Room and app-private image cache.
- Bottom-tab UI for Create, Result, History, and Debug views.

## Build

Required tools:

- JDK 17
- Android SDK with API 37 and Build Tools 37.0.0
- Android Studio or the bundled Gradle wrapper

Create a local `local.properties` file if Android Studio did not create it:

```properties
sdk.dir=C\:\\Android\\Sdk
```

Then build:

```powershell
.\env.ps1
.\gradlew.bat test assembleDebug
```

Debug APK output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

`local.properties`, generated APKs/AABs, signing keys, and local environment files are intentionally ignored by Git.

## Versioning And Releases

The app version is controlled in `gradle.properties`:

```properties
APP_VERSION_CODE=13
APP_VERSION_NAME=2.0.0-beta1
```

Before each release, increment `APP_VERSION_CODE` and keep `APP_VERSION_NAME` aligned with the Git tag. For example, tag `v2.0.0-beta1` should use `APP_VERSION_NAME=2.0.0-beta1`.

After each local build, copy the APK to `releases/apk/` using the name pattern `SD302-v2.0.0-beta1-debug.apk`. APKs under that folder should not be committed; publish distributable builds via GitHub Releases.

Pushing a `v*` tag triggers `.github/workflows/android-release.yml`, which builds the APK with GitHub Actions and uploads it to GitHub Releases. See [docs/RELEASE.md](docs/RELEASE.md) for details.
