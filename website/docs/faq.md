# Frequently asked questions

## Is AetherTube on the Play Store?

No. Same as SmartTube, and for the same reason: neither is officially published anywhere. Get
builds only from this project's [Releases page](https://github.com/abhimanbhau/AetherTube/releases).
Don't trust an AetherTube APK from anywhere else.

## What's the difference between AetherTube and SmartTube?

AetherTube replaces SmartTube's leanback (View-based) interface with a new UI built in Jetpack
Compose for TV, and adds its own features on top — most notably
[portable settings codes](guides/transfer-settings.md), a dedicated Shorts feed, and an ambient
reactive background. Everything else — YouTube API access, playback engine, the settings model,
device compatibility — is SmartTube's work, tracked from upstream. See
[Features](features.md) for the full list.

## How do I move my settings to a new TV without redoing everything by hand?

**Settings → AetherTube → Transfer settings.** Generate a twelve-character code on your current
device, type it into the new one, and every tuned setting — video quality, SponsorBlock,
playback behavior, interface tweaks — is back exactly as it was. No account, no cloud, and it
works with no internet connection on either end. See the
[full guide](guides/transfer-settings.md) for exactly what travels and how the code works.

## Should I install stable or beta?

Take **stable** unless you specifically want to help test upcoming changes. See
[Stable or beta?](guides/stable-vs-beta.md) for the details — they're separate apps that install
side by side, so you can run both if you want.

## Does AetherTube require a Google account?

No — same as SmartTube, a Google account is not required.

## Is this a Play Store / F-Droid replacement client?

No. AetherTube is a personal, experimental fork built for one setup (an Android TV box,
`armeabi-v7a`) and hasn't been tested broadly across devices.

## Is my settings-transfer code sent anywhere?

No. It's generated and applied entirely on-device — there's no server round trip, no account, and
nothing stored anywhere except wherever you wrote the code down yourself. See
[Transfer settings](guides/transfer-settings.md#why-offline-no-account-matters) for why that
design was deliberate.

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
