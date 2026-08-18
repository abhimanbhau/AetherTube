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

## Videos are buffering a lot

This isn't usually the app — it's more often DNS or network-level interference with YouTube
traffic. Switching to an encrypted DNS provider (e.g. NextDNS) on the device or router fixes it
for a lot of people. If that doesn't help, try the usual playback fixes below.

## I get "the video profile is not supported"

Open the format picker (the **HQ** button in the player) and pick a codec other than AV1 — most
devices don't have AV1 hardware decoding. See [Video & playback](guides/video-playback.md#choosing-a-codec)
for which codec to pick instead.

## Why doesn't it auto-select the highest quality?

It does, by default — but if you've set a video preset under **Settings → Player → Video
presets**, that preset acts as a ceiling, not a suggestion. Set it back to "Without preset" if you
want the highest available quality every time. This is also not the same thing as bitrate — a
lower-bitrate codec at the same resolution isn't lower quality, see
[Video & playback](guides/video-playback.md#choosing-a-codec).

## Can it pick resolution automatically based on my bandwidth?

Not yet — there's no adaptive bandwidth-based mode. Set a fixed ceiling under **Settings → Player
→ Video presets** instead; see [Choosing a resolution](guides/video-playback.md#choosing-a-resolution).

## The debug overlay says 1080p but I have a 4K display (or vice versa)

The debug overlay's resolution readout is unreliable — ignore it. Play a 4K video and judge by
what's actually on screen, not that number.

## Does HDR actually work?

If your device, TV, and (if applicable) TV box and HDMI cable all support it — yes. If HDR looks
dim or washed out despite all of that, it's very likely a device-side limitation rather than an
app bug. See [HDR](guides/video-playback.md#hdr) for the full explanation.

## What does Auto Frame Rate (AFR) do, and should I turn it on?

It matches your TV's refresh rate to the content playing, which can smooth out panning shots
slightly. Most people don't notice a difference either way, and it doesn't behave well on every
device. Try it; if it causes glitches when switching, turn it off. See
[Auto Frame Rate](guides/video-playback.md#auto-frame-rate-afr).

## Should I set a high or low player buffer?

High, generally. A bigger buffer preloads more of the video ahead of your playback position,
which smooths over network hiccups — the RAM cost isn't significant. A small buffer only helps if
you frequently close videos early and want to save a little bandwidth. Note that seeking backward
always has to rebuffer regardless of buffer size — that's not something a bigger buffer avoids.

## My device freezes while watching videos

This is almost always a firmware or Android-level issue outside the app's control, especially on
custom ROMs. The usual workarounds apply: reboot, clear the app's cache, reinstall, or — as a
last resort — factory-reset the device.

## Can I download videos for offline viewing?

No, AetherTube doesn't have a download feature.

## Can updates install themselves automatically?

No — that's an Android platform limitation, not a choice. Only the app that originally installed
a package (normally an app store) can silently update it; every other installer, AetherTube
included, can only show an install prompt for you to confirm. AetherTube does check for and
notify you about updates automatically, though — see [Installation](guides/installation.md).

## Why does a recommended video have nothing to do with what I watch?

Recommendations come from YouTube's own algorithm, not from AetherTube — the app has no influence
over what gets suggested. If you're not signed in, recommendations are essentially anonymous
(based on region and general trends, not your history); if you are, they follow your account's
normal YouTube history and preferences, same as anywhere else.

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

No, none of the above. See [Legal](legal.md) for the full disclaimer, including the liability and
no-warranty terms this software is provided under.
