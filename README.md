<div align="center">
<img src="branding/aethertube_master.png" width="112" alt="AetherTube logo">

# AetherTube

**[SmartTube](https://github.com/yuliskov/SmartTube), rebuilt with Jetpack Compose for TV.**

[![Latest release](https://img.shields.io/github/v/release/abhimanbhau/AetherTube?style=flat-square&color=8b5cf6&labelColor=1a1a2e&label=release)](https://github.com/abhimanbhau/AetherTube/releases)
[![Build](https://img.shields.io/github/actions/workflow/status/abhimanbhau/AetherTube/CI.yml?style=flat-square&color=8b5cf6&labelColor=1a1a2e&label=build)](https://github.com/abhimanbhau/AetherTube/actions/workflows/CI.yml)
[![License](https://img.shields.io/github/license/abhimanbhau/AetherTube?style=flat-square&color=8b5cf6&labelColor=1a1a2e)](LICENSE)
[![Platform](https://img.shields.io/badge/platform-Android_TV-8b5cf6?style=flat-square&labelColor=1a1a2e)](#installation)

[Install](#installation) &nbsp;·&nbsp; [What's different](#whats-different-from-smarttube) &nbsp;·&nbsp; [Screenshots](#screenshots) &nbsp;·&nbsp; [Build from source](#build)

</div>

<br>

AetherTube is a personal fork of SmartTube — an unofficial, ad-free YouTube client for Android
TV. This fork replaces SmartTube's leanback (View-based) interface with a new UI built in Jetpack
Compose for TV. It is a hobby project, not an attempt to compete with or replace SmartTube:
everything that isn't the UI layer — YouTube API access, playback, the settings model, device
compatibility — is SmartTube's work, tracked from upstream and left alone wherever possible. See
[NOTICE.md](NOTICE.md) for the attribution this project runs on.

> [!IMPORTANT]
> **Status: personal / experimental.** Built for one setup (an Android TV box, `armeabi-v7a`),
> not tested broadly, no promises about other devices. The Compose UI is on by default — it's the
> whole point of this fork — but it's one toggle away from SmartTube's original leanback interface
> (Settings → AetherTube → **New UI (beta)**) if you'd rather fall back.

<br>

## What's different from SmartTube

**🔑 Move your whole setup in 12 characters.** Every setting you've tuned — video quality,
playback behavior, SponsorBlock, interface tweaks — collapses into a short code like
`3G41-96RJ-ARGN`. Setting up a new TV, or wiped and reinstalled? Type the code in and you're
back exactly where you left off. No account, no cloud, nothing to breach — the code *is* the
backup. (Settings → AetherTube → Transfer settings)

**📱 Shorts that actually feel like Shorts.** A dedicated, full-bleed vertical feed — flick up,
next video, exactly like the app on your phone. SmartTube opens each one like a regular video;
this scrolls.

**🎨 A UI built for now, not stretched onto a TV from a decade-old toolkit.** The whole browsing
and playback experience, rebuilt from scratch in Jetpack Compose.

**🔔 Updates that find you.** AetherTube checks for its own new releases and tells you right in
the app — no more remembering to come back and check GitHub for a new APK.

Everything else — SponsorBlock, adjustable playback speed, HDR/8K/60fps, live chat, no Google
account requirement — is SmartTube's, unchanged.

<br>

## Screenshots

<table>
<tr>
<td width="50%">
<img src="images/screenshots/home.jpg" alt="Home"><br>
<strong>Home</strong> — Compose UI browse screen
</td>
<td width="50%">
<img src="images/screenshots/shorts-grid.jpg" alt="Shorts grid"><br>
<strong>Shorts</strong> — grid entry point
</td>
</tr>
<tr>
<td width="50%">
<img src="images/screenshots/shorts-player-1.jpg" alt="Vertical Shorts player"><br>
<strong>Vertical Shorts player</strong> — scroll up/down between videos
</td>
<td width="50%">
<img src="images/screenshots/settings-new-ui.jpg" alt="Settings"><br>
<strong>Settings</strong> — New UI toggle and portable settings codes
</td>
</tr>
</table>

<img src="images/screenshots/playback-format-dialog.jpg" alt="Advanced playback settings"><br>
<strong>Manual format selection</strong> — one of SmartTube's features, carried over unchanged

<sub>All captured on an Android TV emulator running this fork's Compose UI.</sub>

<br>

## Limitations

Same as upstream: not supported on phones/tablets, comment support is unstable, and voice
search/casting may lag behind the official YouTube app depending on your device. This fork adds
its own: the Compose UI is beta, has not been tested across device families, and the package
layout assumes a single-maintainer project rather than a community one (see
[Project layout](#project-layout)).

<br>

## Installation

> [!WARNING]
> AetherTube is not on the Play Store, F-Droid, or any app store — same as SmartTube, and for the
> same reason: neither is officially published anywhere. Get builds from this repository's
> **[Releases](../../releases)** page only. Do not trust an AetherTube APK from anywhere else.

To install on an Android TV device without a Play Store side-load path, use a file manager app,
a USB stick, or ADB (`adb install <apk>`). SmartTube's README has a more detailed walkthrough of
sideloading if you need one.

Once installed, AetherTube checks for new releases itself — no need to come back and check this
page manually.

<br>

## Build

This project depends on two git submodules from upstream SmartTube (`MediaServiceCore`,
`SharedModules`) that are forked into this account so a plain clone works:

```bash
git clone --recursive https://github.com/abhimanbhau/AetherTube.git
cd AetherTube
```

(If you already cloned without `--recursive`: `git submodule update --init --recursive`.)

Build with JDK 17+ set as `JAVA_HOME` (Android Studio's bundled JBR works):

```bash
export JAVA_HOME="/path/to/jdk-17-or-newer"
./gradlew :smarttubetv:assembleStbetaDebug
```

Release builds look for a `keystore.properties` file at the repo root (developer-local, not
tracked in git):

```properties
storeFile=/path/to/your.keystore
storePassword=...
keyAlias=...
keyPassword=...
```

Without it, `assembleRelease` still works but produces an unsigned APK.

### Project layout

```
com.abhimankolte.aethertube.*      this fork — Compose UI, Shorts player, settings codes.
                                    The only part actively maintained here.

com.liskovsoft.*                   SmartTube's, deliberately left in place rather than
MediaServiceCore, SharedModules    renamed or restructured, so it stays diffable against
(vendored ExoPlayer too)           upstream and upstream's YouTube-API fixes keep applying
                                    cleanly.
```

<br>

## Credits & License

[SmartTube](https://github.com/yuliskov/SmartTube) by yuliskov and contributors is the project
this is built on — the YouTube API integration, playback engine, and the leanback UI this fork
replaces.

MIT — see [LICENSE](LICENSE). Vendored and forked third-party modules keep their own licenses;
full attribution in [NOTICE.md](NOTICE.md).

<div align="center">
<sub>Not affiliated with YouTube, Google, or SmartTube's maintainer.</sub>
</div>
