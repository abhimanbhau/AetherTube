# AetherTube

AetherTube is a personal fork of [SmartTube](https://github.com/yuliskov/SmartTube) — an
unofficial, ad-free YouTube client for Android TV. This fork replaces SmartTube's leanback
(View-based) interface with a new UI built in **Jetpack Compose for TV**.

It is a hobby project, not an attempt to compete with or replace SmartTube. Everything that isn't
the UI layer — YouTube API access, playback, the settings model, device compatibility — is
SmartTube's work, tracked from upstream and left alone wherever possible. See
[NOTICE.md](NOTICE.md) for the attribution this project runs on.

**Status: personal / experimental.** Built for one setup (an Android TV box, `armeabi-v7a`),
not tested broadly, no promises about other devices. The Compose UI is on by default — it's the
whole point of this fork — but it's one toggle away from SmartTube's original leanback interface
(Settings → AetherTube → "New UI (beta)") if you'd rather fall back.

---

### What's different from SmartTube

- **Compose for TV interface** *(on by default — toggle off via "New UI (beta)" in Settings →
  AetherTube if you want the original)* — the home, browse and player screens rebuilt in Jetpack
  Compose instead of the leanback widget toolkit.
- **Vertical Shorts player** — a dedicated, full-bleed vertical feed for Shorts: scroll up/down
  between videos instead of opening them one at a time.
- **Portable settings codes** — Settings → AetherTube → Transfer settings generates a short
  alphanumeric code (e.g. `3G41-96RJ-ARGN`) that captures your configuration — video quality,
  playback behavior, SponsorBlock, interface options — and can be typed back in on another device
  or after a reinstall. No account, no cloud backup, nothing to breach: the code is the whole
  mechanism.

Everything else — SponsorBlock, adjustable playback speed, HDR/8K/60fps playback, live chat, no
Google account requirement — is SmartTube's, unchanged.

### Limitations

Same as upstream: not supported on phones/tablets, comment support is unstable, and voice
search/casting may lag behind the official YouTube app depending on your device. This fork adds
its own: the Compose UI is beta, has not been tested across device families, and package layout
assumes a single-maintainer project rather than a community one (see below).

---

## Installation

AetherTube is not on the Play Store, F-Droid, or any app store — same as SmartTube, and for the
same reason: neither is officially published anywhere. Get builds from this repository's
[Releases](../../releases) page only. Do not trust an AetherTube APK from anywhere else.

To install on an Android TV device without a Play Store side-load path, use a file manager app,
a USB stick, or ADB (`adb install <apk>`). SmartTube's README has a more detailed walkthrough of
sideloading if you need one.

---

## Build

This project depends on two git submodules from upstream SmartTube
(`MediaServiceCore`, `SharedModules`) that are forked into this account so a plain clone works:

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

- `com.abhimankolte.aethertube.*` — this fork's code: the Compose UI, the Shorts player, settings
  codes. This is the only part actively maintained here.
- `com.liskovsoft.*`, `MediaServiceCore`, `SharedModules`, the vendored ExoPlayer — SmartTube's,
  deliberately left in place rather than renamed or restructured, so it stays diffable against
  upstream and upstream's YouTube-API fixes keep applying cleanly.

---

## Credits

[SmartTube](https://github.com/yuliskov/SmartTube) by yuliskov and contributors is the project
this is built on — the YouTube API integration, playback engine, and the leanback UI this fork
replaces. See [NOTICE.md](NOTICE.md) for the full license text this is used under.

## License

MIT — see [LICENSE](LICENSE). Vendored and forked third-party modules keep their own licenses;
see [NOTICE.md](NOTICE.md).
