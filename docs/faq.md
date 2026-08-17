---
layout: default
title: FAQ
nav_order: 2
---

# Frequently asked questions
{: .no_toc }

## Table of contents
{: .no_toc .text-delta }

1. TOC
{:toc}

---

## Is AetherTube on the Play Store?

No. Same as SmartTube, and for the same reason: neither is officially published anywhere. Get
builds only from this project's [Releases page](https://github.com/abhimanbhau/AetherTube/releases).
Don't trust an AetherTube APK from anywhere else.

## What's the difference between AetherTube and SmartTube?

AetherTube replaces SmartTube's leanback (View-based) interface with a new UI built in Jetpack
Compose for TV, and adds a handful of its own features on top (portable settings codes, a
dedicated Shorts feed, an ambient reactive background, in-app update checks). Everything else —
YouTube API access, playback engine, the settings model, device compatibility — is SmartTube's
work, tracked from upstream. See [Features](features) for the full list.

## Should I install stable or beta?

Take **stable** unless you specifically want to help test upcoming changes. See
[Stable or beta?](guides/stable-vs-beta) for the details — they're separate apps that install
side by side, so you can run both if you want.

## Does AetherTube require a Google account?

No — same as SmartTube, a Google account is not required.

## Is this a Play Store / F-Droid replacement client?

No. AetherTube is a personal, experimental fork built for one setup (an Android TV box,
`armeabi-v7a`) and hasn't been tested broadly across devices.

## Can I move my settings to a new TV or a fresh install?

Yes — see [Transfer settings](guides/transfer-settings). Your whole configuration collapses into
a short code you type back in on the new install.

## Is my data sent anywhere?

The settings transfer code has no account and no cloud step — it's a local code you carry
yourself. See [Transfer settings](guides/transfer-settings) for how it works.

## Why does the app look unchanged after installing?

The Compose UI is on by default, but if you're seeing the old leanback interface, check
**Settings → AetherTube → New UI (beta)** — it may have been switched off, or you're running an
older build from before the toggle defaulted on.

## Was this project built with AI assistance?

Yes — most of the code was written or substantially modified with AI assistance (an LLM agent,
working under the maintainer's direction), not hand-written line by line. It's tested on one
device, not audited, and comes with no warranty of any kind. Read the code before you trust it
with your setup.

## Where do I report a bug or ask something not covered here?

Open an issue on the [GitHub repository](https://github.com/abhimanbhau/AetherTube/issues). This
is a personal project maintained by one person, so responses aren't guaranteed to be fast.

## Is AetherTube affiliated with YouTube, Google, or SmartTube's maintainer?

No, none of the above.
