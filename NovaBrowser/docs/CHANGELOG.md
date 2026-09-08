# NovaBrowser Changelog

All notable changes to NovaBrowser are documented here based on verified codebase implementations.

The format is inspired by [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

---

## [Current Active Codebase] - 2026-09-08

### Added
- **Nova QuadView Spatial Workspace:**
  - Up to 4 live concurrent browser tabs in a responsive 2×2 grid (`QuadViewManager`, `QuadPaneView`, `QuadSlot`, `QuadViewState`).
  - View reparenting architecture preserving `NovaWebView` instances without DOM recreation or session reload.
  - Focus Mode toggling between 2×2 grid and 100% dominant view.
  - Slot swapping, contextual pane actions, tab reload, and slot closure.
  - Dynamic orientation adjustments (horizontal split for portrait, vertical split for landscape).
- **Ad & Tracker Blocking Subsystem:**
  - `AdBlockEngine` using in-memory label tree for $O(\text{labels})$ fast-path subresource filtering over 9,578 EasyList & EasyPrivacy rules.
  - `CosmeticEngine` injecting 118 batched CSS hiding selectors on page load.
  - `SiteShieldManager` with SQLite persistence (`site_shields_settings` table) for granular per-domain controls (toggle shields, ads, cosmetic filters, JS, cookies).
- **Physical Download Quarantine Sandbox:**
  - Risky file extensions (`.apk`, `.dex`, `.sh`, `.exe`, `.js`, `.bat`) routed to `.nova_quarantine/` sandbox.
  - Running cryptographic SHA-256 digest calculation during stream download.
  - Explainable download confirmation dialog presenting detected risk, filesize, and SHA-256 fingerprint.
- **Reader Mode:**
  - Distraction-free content extraction (`ReaderExtractor`) with customizable typography, margins, and theme presets (`ReaderPreferences`, `ReaderActivity`).
- **Media Sniffer Engine:**
  - Automatic sniffer intercepting direct media stream URLs for audio/video downloads (`MediaSnifferEngine`).
- **Offline Web Archives:**
  - MHTML web archive saving and loading for offline reading (`OfflinePageManager`).
- **Web Dark Theme Manager:**
  - Algorithmic darkening and AndroidX WebKit `ForceDark` integration (`WebDarkThemeManager`).
- **PWA Shortcuts:**
  - Standalone home screen shortcut pinning for Progressive Web Apps (`PwaShortcutManager`).
- **In-App Security Diagnostics Runner:**
  - 5-vector diagnostic self-check in Settings verifying benign baseline, homoglyph detection, malware blocking, SSL stripping, and adblock filters in airplane mode.
- **Biometric Security:**
  - AndroidX Biometric prompt protecting private browsing tabs and security settings (`NovaBiometricHelper`).

### Changed
- **Database Schema Upgraded to Version 2:**
  - Added non-destructive migration (`migrateV1ToV2`) in `NovaDatabaseHelper`.
  - Added tables: `adblock_site_rules`, `broken_site_reports`, `site_permissions`, `site_shields_settings`.
  - Upgraded history search to SQLite FTS5 prefix token matching (`"word"*`) with BM25 ranking and graceful `LIKE` fallback.
- **TabManager Architecture Refined:**
  - Standardized `TabManager` as the sole source of truth for tab lifecycle and session identity across single-tab, switcher, and QuadView modes.
- **Address Bar & Omnibox:**
  - Integrated search-as-you-type suggestions with search engine selection (Google, DuckDuckGo, Bing, Brave, Ecosia, Startpage).

### Security
- **Deterministic Security Gate:**
  - `UrlCanonicalizer`: Punycode/IDN Unicode decoding, recursive percent-decoding, auth stripping, port normalization.
  - `ThreatFeedManager`: Fast binary match against embedded URLhaus malware dataset (5,057+ rules).
  - `HeuristicsEngine`: Shannon entropy calculation for DGA detection and Levenshtein distance check against protected brand registry (`paypa1.com` → `WARN`).
  - `RedirectTracker`: Max 4-hop chain enforcement and SSL downgrade (`HTTPS -> HTTP`) interception.
- **Android JS Bridge Hard Boundary:**
  - Audited and verified zero privileged `@JavascriptInterface` objects exposed to untrusted web content.
- **Explainable Interstitial:**
  - `SecurityWarningActivity` provides structured threat breakdown, matched feed sources, and risk scores.

### Privacy
- **Zero Remote Telemetry:**
  - Complete elimination of remote analytics, trackers, or cloud dependencies.
- **Private Browsing Mode Isolation:**
  - Private tabs write zero records to SQLite, suppress cache (`LOAD_NO_CACHE`), and flush session cookies on tab closure.
- **Tracker Mitigation:**
  - Third-party cookies blocked by default (`setAcceptThirdPartyCookies(false)`).
  - Automatic injection of `DNT: 1` and `Sec-GPC: 1` HTTP headers.

### UI/UX
- **Liquid System Design:**
  - Fluid squircle geometry, glassmorphism cards, and floating island navigation dock.
  - Ambient celestial brand mark and pulsing security indicator.
  - Restrained dark theme palette (`#0B0E14`, `#121824`, `#1A2333`).
- **NovaMotion Animation Framework:**
  - Hardware-accelerated 60/120 FPS transitions and tactile spring animations (`NovaMotion.kt`).

### Performance
- **Low-Memory First Strategy:**
  - Minimal APK binary size: ~7.2 MB debug, ~9.8 MB release.
  - Fast-path Security Gate lookup in $\le 3\text{ ms}$.
  - Minimal RAM footprint ($< 150\text{ MB}$ idle) allowing smooth execution on 2GB RAM devices.

### Developer Experience & Tests
- **SDK Target Modernization:**
  - Configured `compileSdk = 35`, `targetSdk = 35`, `minSdk = 24`, Java 17 toolchain.
- **Automated Test Coverage:**
  - 121 actionable Gradle tasks passing across `:browser-core`, `:app`, and `:ai`.
  - Comprehensive unit test suites covering URL canonicalization, heuristics, redirect loops, download risk, database migrations, shields, and QuadView state.
