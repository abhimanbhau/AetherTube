# Privacy Policy for AetherTube

**Last Updated:** August 15, 2026

This applies to all AetherTube builds (`com.abhimankolte.aethertube` and its beta/stable/F-Droid
variants). Unlike upstream SmartTube, every AetherTube build ships with the self-updater and crash
reporting disabled, so there is no per-flavor split here — this policy covers all of them.

## 1. Data Collection and Processing
* **No Backend:** AetherTube operates entirely as a client-side application. It does not
  communicate with any developer-controlled servers.
* **No Telemetry or Analytics:** The app contains no tracking code, telemetry, or analytics
  frameworks. Crash reporting (Crashlytics) is disabled in code, not just unconfigured. We do not
  monitor how you use the app.
* **No Update Checks:** The in-app updater is disabled. Updates are obtained manually from this
  repository's Releases page.

## 2. Third-Party Services (YouTube/Google)
* **Authentication:** Signing in uses the official Google OAuth 2.0 device-code flow. The app
  never sees or handles your password.
* **Local Storage:** Authentication tokens are stored exclusively on your device's local storage.
  They are never transmitted to, or stored by, the developer.
* **Data Flow:** Video data and account information are fetched directly from YouTube/Google
  servers to your device.

## 3. Community-Driven Services & Third-Party APIs
To provide core functionality and optional features, the app communicates directly with:
* **SponsorBlock** — sends the ID of the video being viewed to retrieve crowd-sourced skip
  segments.
* **DeArrow** — sends the ID of the video being viewed to retrieve crowd-sourced titles.
* **Return YouTube Dislike (RYD)** — sends the video ID to retrieve estimated dislike counts.

These are read-only requests: no personal identifiers, account tokens, or profile data are
included. None of this data passes through or is retained by the developer.

## 4. Automated Decision-Making and Profiling
* **No Profiling:** User behavior is not tracked to build profiles.
* **No Automated Decisions:** The app does not use algorithms to make decisions about users or to
  manipulate content ranking. Content is served as-is from the YouTube API.

## 5. Your Rights (GDPR / CCPA)
Because the app does not store or transmit personal data to the developer, there are no
identifiable records (names, emails, IPs) associated with your identity for the developer to
provide or delete on request.

## 6. Contact
For questions about this policy, open an issue on this repository.
