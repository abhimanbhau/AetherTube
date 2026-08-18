# Video & playback

The playback engine, codec handling, and buffering behavior are inherited unchanged from
[SmartTube](https://github.com/yuliskov/SmartTube) — this page documents that shared engine, not
something AetherTube-specific. If you've used SmartTube before, none of this will surprise you.

## Choosing a codec

Video codecs are the algorithms YouTube uses to compress video. AetherTube can play whichever
your device's hardware decodes, selectable under **Settings → Player → Video presets**.

|  | Recommendation | Hardware support | Bitrate at the same quality | 
| :-- | :-- | :-- | :-- |
| **AV1 (AV01)** | Best choice, *if your device supports it* | First devices from 2020 onward | Best (e.g. 1.6 Mbps) |
| **VP9** | Best choice on most devices | Most devices since 2015 | Better (e.g. 2.1 Mbps) |
| **AVC (H.264)** | Only for old or slow hardware | All devices | Higher (e.g. 2.7 Mbps) |

!!! tip "Lower bitrate isn't lower quality"
    YouTube targets the *same* visual quality regardless of codec — a newer codec just reaches it
    with less data. AVC has the highest bitrate of the three specifically because it's the least
    efficient, not because it looks better. Pick the newest codec your device actually decodes
    smoothly; don't equate a bigger number with a better picture.

**AV1 not playing, or VP9 stuttering?** Most TV boxes and TVs still have no AV1 hardware decoder at
all — if yours doesn't, AV1 simply won't play, full stop. VP9 without hardware support can still
work, but leans on the CPU, so it may stutter on cheaper boxes at higher resolutions. If either
happens, open the video's format picker and switch to a codec your device handles natively —
VP9 first, AVC as the fallback.

## Choosing a resolution

There's no bandwidth-adaptive "auto" mode yet. Instead, set a default under **Settings → Player →
Video presets**: "Without preset" remembers your last per-video choice, while any other preset is
applied to every video from then on (falling back to the next-best available resolution if the
exact one isn't offered). You can still override it per-video from the in-player format picker.

What actually limits your ideal resolution:

- **Your bandwidth** — pick something your connection can sustain; a speed test (e.g. fast.com)
  tells you your ceiling.
- **Your TV's real resolution** — going one step above your display's native resolution can help
  very slightly, but don't expect a dramatic difference.
- **Whether 60fps is worth it to you** — it's smoother, but if you don't notice or care, skipping
  it saves bandwidth.

## HDR

HDR only works if your entire chain supports it: **the content, your TV, and — if you're using a
TV box — the box and the HDMI cable too** (HDR support varies by cable revision). Some devices
advertise HDR support generally but not the specific format YouTube serves, so "HDR-capable"
hardware can still show flat, washed-out HDR video. If that happens, it's very likely a
device/cable limitation rather than an app bug — search your device's model name plus "HDR" for
guidance specific to it.

## SponsorBlock

[SponsorBlock](https://sponsor.ajay.app/) is an open-source, crowdsourced database of sponsor
segments, intros, outros, and other skippable moments in YouTube videos — AetherTube can
auto-skip whichever categories you choose, under **Settings → SponsorBlock**.

It's community-submitted, not perfect: a very new or low-traffic video may not have segments
submitted yet, and the SponsorBlock service itself is a free, volunteer-run project that can be
slow or briefly unavailable. Unlike the browser extension, AetherTube can't submit new segments —
a TV remote isn't a precise enough input for that.

## Picture-in-Picture

Enable it under **Settings → General → Background playback**, choosing **Picture in picture**.
Once on, pressing Home while a video plays shrinks it into a PiP window instead of stopping it;
depending on your background-playback activation setting, **Back** can do the same.

## Adjusting playback speed

The speed control (a gauge icon) sits in the player's top row and is remembered across videos.
Some non-1× speeds can drop frames on weaker hardware — a known limitation of the underlying
player, not something specific to a given video.

## Auto Frame Rate (AFR)

AFR matches your TV's refresh rate to whatever you're watching, which can reduce judder on
panning shots — the difference is subtle and most people don't notice it either way, and it
doesn't work equally well on every device. If you're not sure, try it; if it causes issues (black
flashes when switching, mode-switch delays), turn it back off. Found under **Settings → Auto
Frame Rate**.

## Casting from your phone

Unlike the stock YouTube app, AetherTube doesn't advertise itself automatically to phones on the
same network — you link a phone to it explicitly, once:

1. On the TV, open **Settings → Remote control** and note the pairing code shown there.
2. On your phone's YouTube app, go to **Settings → General → Watch on TV → Connect using TV code**
   and enter that code.

You do need to have AetherTube already open on the TV before casting to it — it can't wake the TV
up on its own.
