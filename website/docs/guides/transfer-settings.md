# Transfer settings

Move your whole configuration — video quality, playback behavior, SponsorBlock, interface
tweaks — to another device, or back onto the same one after a wipe or reinstall, by typing a
twelve-character code. No account, no cloud step, no internet connection required at either end.

```
3G41-96RJ-ARGN
```

## Why this exists

Setting up an Android TV box by hand is forty small decisions: seek step, SponsorBlock behavior,
frame-rate matching, which overlay elements show during playback, the new interface toggle, and
more. Do that once and it's fine. Do it every time you get a new box, wipe a device, or want a
second TV to match the first, and it's the most annoying part of owning the app. A settings code
collapses all of it into something you can type with a D-pad in under a minute.

## Using it

### Generate a code on the source device

1. Open **Settings → AetherTube → Transfer settings**.
2. Generate a code — it looks like `3G41-96RJ-ARGN`.
3. Write it down, photograph it, or keep it on screen while setting up the new device.

### Apply it on the destination device

1. Install AetherTube on the new device (see [Installation](installation.md) if you haven't yet).
2. Open **Settings → AetherTube → Transfer settings** there too.
3. Enter the code from the source device.

Settings apply immediately — no restart needed.

!!! tip "Typos are forgiving"
    The code ignores hyphens, spaces, and letter case, and its alphabet deliberately excludes the
    characters people misread most on a TV screen — see [The alphabet](#the-alphabet) below for
    why. If you do mistype something, the code is rejected outright with a clear error rather than
    silently applying the wrong settings.

## What actually travels in the code

Every one of the 33 settings below is bit-packed into the code, grouped by area:

| Area | What's included |
| :-- | :-- |
| **Picture** | Video quality preset, video buffer type, legacy codec forcing |
| **Playback** | Playback mode, background playback mode, OK-button behavior, seek step size, number-key seeking, skip Shorts |
| **Frame-rate matching** | AFR on/off, FPS correction, resolution switching, 24 Hz skip |
| **Player overlay** | Clock, remaining time, ending time, quality info display |
| **SponsorBlock** | Enabled, "don't skip this segment again," paid-content notification |
| **General behavior** | Watch history, screensaver, return-to-launcher, 24-hour clock, fullscreen mode, D-pad-left-as-volume, app-exit shortcut |
| **Search** | Instant voice search |
| **Interface** | New Compose UI toggle, visual effects level, card preview type, thumbnail quality, multi-line card titles |

### What's deliberately left out

Accounts, subscriptions, pinned channels, and search/watch history are **not** in the code — on
purpose. Those are either server-side already, unbounded in size (a code has to stay short enough
to type), or credentials that have no business traveling in something you might read aloud to
someone. This is the "make a new TV behave like my old one" set, not a full backup.

## How it fits in twelve characters

A settings code is 60 bits, encoded as 12 characters:

```
60 bits total  →  12 characters  →  "A7K2M-9QT4X-B3NP"

[ 4 bits ] format version
[48 bits ] the 33 settings above, packed field by field
[ 8 bits ] checksum
```

### The alphabet

Codes use [Crockford's Base32](https://www.crockford.com/base32.html): 32 symbols, so each
character carries 5 bits. It excludes the letters `I`, `L`, `O`, and `U`, and — critically — it
still *accepts* them on input, mapping `I`/`L` to `1` and `O` to `0`. Those are the exact two
mistakes people make reading a code off a TV screen at a distance, so a code with a misread
character in it still decodes correctly instead of failing. Entry is also case-insensitive, since
typing capitals on a D-pad remote is its own small misery.

A denser alphabet (Base62, say) would fit more bits into the same twelve characters, but only by
being case-sensitive — which costs far more usability on a remote than the extra bits are worth.

### Forward and backward compatibility, without a version negotiation step

The 48-bit payload has no field tags — at this size, a tag would cost more than the value it
labels. Instead, every setting has a fixed position, and the layout is **append-only**: new
settings can only be added at the end, never reordered, removed, or resized. That one rule gives
compatibility in both directions for free:

- An **older app reading a newer code** just stops at the last field it recognizes and ignores
  whatever comes after.
- A **newer app reading an older code** runs out of bits partway through and leaves the settings
  it didn't find exactly as they already were on the device — never resetting them to zero.

The 4-bit version prefix exists for the one thing that discipline can't absorb: changing the
*meaning* or width of a field that already shipped. That's treated as a breaking change requiring
a version bump and a compatibility path for the old layout — not something that happens casually.

### Integrity

The last 8 bits are a CRC-8 checksum over the version and payload. That's a meaningful fraction of
a 60-bit budget to spend on error detection, and it's spent deliberately: the alternative is
silently applying a garbled or mistyped code, which is both hard to notice (nothing crashes) and
annoying to untangle after the fact. A code that fails its checksum is rejected outright, with a
message telling you to check for a mistyped character — rather than partially applying whatever it
happened to decode to.

## Why offline, no-account matters

There's no server round trip anywhere in this flow. The code is generated entirely on-device and
applied entirely on-device — nothing is uploaded, nothing is looked up, and there's no account to
sign into on either end. That means:

- It works with **no internet connection** on either the source or destination TV.
- There's **nothing to breach** — the code doesn't exist anywhere except where you wrote it down.
- It works identically whether the two devices are on the same network, different countries, or
  one of them no longer exists (recovering your setup from a code you saved before wiping a box).

The code *is* the backup. The tradeoff is that it's your responsibility to keep it somewhere you
can find again — AetherTube doesn't store it anywhere on its own.
