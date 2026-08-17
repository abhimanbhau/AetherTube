---
layout: home
title: Home
nav_order: 1
description: "AetherTube documentation home"
permalink: /
---

# AetherTube
{: .fs-9 }

SmartTube, rebuilt with Jetpack Compose for TV.
{: .fs-6 .fw-300 }

[Get started](guides/installation){: .btn .btn-primary .fs-5 .mb-4 .mb-md-0 .mr-2 }
[View on GitHub](https://github.com/abhimanbhau/AetherTube){: .btn .fs-5 .mb-4 .mb-md-0 }

---

AetherTube is a personal fork of [SmartTube](https://github.com/yuliskov/SmartTube) — an
unofficial, ad-free YouTube client for Android TV. This fork replaces SmartTube's leanback
(View-based) interface with a new UI built in Jetpack Compose for TV. It's a hobby project, not
an attempt to compete with or replace SmartTube: everything that isn't the UI layer — YouTube API
access, playback, the settings model, device compatibility — is SmartTube's work, tracked from
upstream and left alone wherever possible.

{: .important }
**Status: personal / experimental.** Built for one setup (an Android TV box, `armeabi-v7a`), not
tested broadly, no promises about other devices. The Compose UI is on by default, but it's one
toggle away from SmartTube's original leanback interface (Settings → AetherTube → **New UI
(beta)**) if you'd rather fall back.

## Where to go next

| If you want to... | Go to |
| :-- | :-- |
| Install AetherTube on your TV | [Installation guide](guides/installation) |
| Understand stable vs. beta | [Stable or beta?](guides/stable-vs-beta) |
| Move your settings to a new device | [Transfer settings](guides/transfer-settings) |
| See what's different from SmartTube | [Features](features) |
| Get a quick answer | [FAQ](faq) |

## Not affiliated

AetherTube is not affiliated with YouTube, Google, or SmartTube's maintainer. It is not on the
Play Store, F-Droid, or any app store — get builds only from the
[Releases page](https://github.com/abhimanbhau/AetherTube/releases).
