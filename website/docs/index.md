# AetherTube

**SmartTube, rebuilt with Jetpack Compose for TV.**

AetherTube is a personal fork of [SmartTube](https://github.com/yuliskov/SmartTube) — an
unofficial, ad-free YouTube client for Android TV. This fork replaces SmartTube's leanback
(View-based) interface with a new UI built in Jetpack Compose for TV. It's a hobby project, not an
attempt to compete with or replace SmartTube: everything that isn't the UI layer — YouTube API
access, playback, the settings model, device compatibility — is SmartTube's work, tracked from
upstream and left alone wherever possible.

!!! warning "Status: personal / experimental"
    Built for one setup (an Android TV box, `armeabi-v7a`), not tested broadly, no promises about
    other devices. The Compose UI is on by default — it's the whole point of this fork — but it's
    one toggle away from SmartTube's original leanback interface (**Settings → AetherTube → New UI
    (beta)**) if you'd rather fall back.

[Get started :material-arrow-right:](guides/installation.md){ .md-button .md-button--primary }
[View on GitHub :fontawesome-brands-github:](https://github.com/abhimanbhau/AetherTube){ .md-button }

---

## The headline feature: settings that travel in a 12-character code

Every setting you've tuned — video quality, playback behavior, SponsorBlock, interface tweaks —
collapses into a code like `3G41-96RJ-ARGN`. Type it into another device and you're back exactly
where you left off: no account, no cloud, no internet connection needed on either end. The code
*is* the backup.

**[Read the full write-up →](guides/transfer-settings.md)** — how the 60-bit format works, exactly
which 33 settings travel, and why it's deliberately not a full account backup.

---

## Where to go next

<div class="grid cards" markdown>

-   :material-download:{ .lg .middle } **Install AetherTube**

    ---

    Pick a channel, grab the right APK, and sideload it onto your TV.

    [:octicons-arrow-right-24: Installation guide](guides/installation.md)

-   :material-source-branch:{ .lg .middle } **Stable or beta?**

    ---

    Two separate apps, side by side. Here's which one you want.

    [:octicons-arrow-right-24: Stable or beta?](guides/stable-vs-beta.md)

-   :material-key-variant:{ .lg .middle } **Transfer settings**

    ---

    Move your whole setup to a new device with one twelve-character code.

    [:octicons-arrow-right-24: Transfer settings](guides/transfer-settings.md)

-   :material-star-four-points:{ .lg .middle } **What's different**

    ---

    Everything AetherTube changes from stock SmartTube, in one page.

    [:octicons-arrow-right-24: Features](features.md)

</div>

---

## Not affiliated

AetherTube is not affiliated with YouTube, Google, or SmartTube's maintainer. It is not on the
Play Store, F-Droid, or any app store — get builds only from the
[Releases page](https://github.com/abhimanbhau/AetherTube/releases).
