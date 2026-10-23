<p align="center">
  <img src="art/akagi-icon.svg" width="128" height="128" alt="Akagi icon">
</p>

<h1 align="center">Akagi</h1>

<p align="center">
  A modern YouTube audio downloader and player for Android.<br>
  Browse YouTube, save the audio of any video as Opus or M4A, and listen offline.
</p>

---

> **Status:** in development. Browsing, downloading, the library with playlists and the background player
> work; YouTube sign-in, error handling and polish come next. The first full release will be **v1.0.0**;
> until then the only release (v0.0.1) is an early foundation build.

## Features

- **Built-in YouTube**: browse and search YouTube inside the app, or paste any YouTube link.
- **One-tap audio download**: Opus or M4A straight from YouTube's own streams (MP3 only as a fallback), with a recommended quality that balances size and sound.
- **Library sidebar**: a drag-out panel with your downloads (newest first) and your playlists.
- **Player**: shuffle, repeat one or all, and a waveform seekbar. Playback continues in the background with the screen off, with minimal lock-screen and notification controls.
- **Considerate audio**: when headphones disconnect it pauses and turns the media volume to 0, so nothing blasts from the speaker; it never fights with YouTube or other apps over playback.
- **Stays working**: yt-dlp can be updated from inside the app when YouTube changes.
- Files are saved to `Music/Akagi`, so other music players can see them too.

## Install

1. Open the [latest release](../../releases/latest) on your phone and download `Akagi-vX.Y.Z-arm64-v8a.apk`.
2. Open it and allow installing from your browser when Android asks.
3. Updates install over the existing app and keep your library.

Requires **Android 10 or newer** on a 64-bit (arm64) phone.

### If music stops with the screen off

Akagi plays as a standard media app, which stock Android leaves alone. Some phone makers add their own
battery savers that can still stop it. If playback stops after a while with the screen off:

- **Xiaomi / Redmi / POCO (HyperOS, MIUI):** Settings → Apps → Akagi → Battery saver → **No restrictions**,
  and turn on **Autostart**. In Recents, long-press Akagi and lock it.
- **Samsung:** Settings → Battery → Background usage limits: make sure Akagi isn't under
  *Sleeping apps* or *Deep sleeping apps*.
- **OnePlus / OPPO / realme:** Settings → Battery → App battery management → Akagi → **Allow background activity**.
- **Huawei / Honor:** Settings → Battery → App launch → Akagi → **Manage manually**, with all three switches on.

More vendor-specific steps are collected at [dontkillmyapp.com](https://dontkillmyapp.com).

## Building from source

Requirements: JDK 17+ (21 recommended) and the Android SDK (platform 37, build-tools 36).
On Windows, `tools/setup-toolchain.ps1` installs both into your user profile without admin rights.

```sh
./gradlew assembleDebug        # debug APK in app/build/outputs/apk/debug/
./gradlew spotlessCheck lintDebug testDebugUnitTest
```

Release APKs are built and signed by GitHub Actions when a `vX.Y.Z` tag is pushed.

## Tech

Kotlin · Jetpack Compose (Material 3) · Hilt · Room · Media3 (ExoPlayer, MediaSession) ·
[youtubedl-android](https://github.com/yausername/youtubedl-android) (yt-dlp, Python, FFmpeg, QuickJS).

## Credits and licences

Akagi is free software under the [GNU GPL v3](LICENSE). It bundles or builds on:

| Component | Licence |
|---|---|
| [yt-dlp](https://github.com/yt-dlp/yt-dlp) | Unlicense |
| [youtubedl-android](https://github.com/yausername/youtubedl-android) | GPL-3.0 |
| [FFmpeg](https://ffmpeg.org) | GPL / LGPL |
| [Python](https://www.python.org) | PSF License |
| [QuickJS](https://bellard.org/quickjs/) | MIT |
| AndroidX, Media3, Material components | Apache 2.0 |

The app icon is original artwork made for Akagi.

## Disclaimer

Akagi is a personal project for downloading content you have the right to download. Respect
YouTube's Terms of Service and creators' copyrights. Akagi is not affiliated with YouTube, Google
or TubeMate.
