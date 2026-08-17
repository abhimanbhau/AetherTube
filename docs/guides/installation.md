---
layout: default
title: Installation
parent: Guides
nav_order: 1
---

# Installation

{: .warning }
AetherTube is not on the Play Store, F-Droid, or any app store. Get builds from this
repository's [Releases page](https://github.com/abhimanbhau/AetherTube/releases) only. Do not
trust an AetherTube APK from anywhere else.

## 1. Pick a channel

Decide between **stable** and **beta** first — see [Stable or beta?](stable-vs-beta). Most people
want stable.

## 2. Download the right APK

On the [Releases page](https://github.com/abhimanbhau/AetherTube/releases), each release has
several APK files attached:

| File | Use it if... |
| :-- | :-- |
| `..._universal.apk` | you're unsure which to pick — this works everywhere, just slightly larger |
| `..._arm64-v8a.apk` | your device is a modern 64-bit Android TV box (most recent hardware) |
| `..._armeabi-v7a.apk` | your device is an older 32-bit ARM box |
| `..._x86.apk` | your device runs an x86 Android TV image (rare) |

If you're not sure which architecture your device uses, grab the universal APK.

## 3. Sideload it

Android TV has no built-in path to install an APK you downloaded yourself. Use one of:

- **A file manager app** — download the APK on-device (or copy it over) and open it.
- **A USB stick** — copy the APK to a USB drive, plug it into the TV box, and open it with a file
  manager.
- **ADB** — from a computer on the same network or over USB:
  ```bash
  adb install AetherTube_stable_<version>_universal.apk
  ```

SmartTube's own README has a more detailed sideloading walkthrough if you need device-specific
steps.

## 4. After installing

AetherTube checks for new releases on its own and tells you in-app when one's available, with the
actual changelog — you don't need to come back to this page to check for updates.

If you're moving from an existing install (or setting up a second device), see
[Transfer settings](transfer-settings) instead of reconfiguring everything by hand.
