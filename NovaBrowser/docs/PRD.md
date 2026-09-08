# NovaBrowser — Product Requirements Document (PRD)

**Version:** 1.2 (Synchronized with Codebase)  
**Status:** Active Implementation  
**Owner:** Sonu / Team Gintama  

---

## 1. Vision

NovaBrowser is a **security-first, local-first AI browser** for Android (with planned desktop expansion). It is not "Chrome with a chatbot bolted on." It is a browser where a deterministic security core protects navigation by default, and a local intelligence layer enhances browser search and navigation — without depending on cloud AI, without leaking private browsing data, without bloating RAM/storage, and without ever permitting an AI model to decide whether a URL is safe to open.

**One-line pitch:** *A browser that keeps you safe by default, gives you multi-tab spatial productivity with Nova QuadView, and remembers your browsing for you — entirely on your device.*

---

## 2. Problem Statement

- **The Privacy Dilemma:** Mainstream browsers are either "dumb and safe" (no native intelligence) or "AI-heavy and privacy-leaky" (cloud LLMs ingest your browsing history, page contents, and bookmarks).
- **Security Misconceptions:** Modern AI browser add-ons frequently treat probabilistic LLMs as semi-trusted decision makers (e.g. asking a model "is this link safe?"). Probabilistic models are vulnerable to adversarial prompts, hallucinations, and jailbreaks; they cannot serve as an auditable security boundary.
- **Hardware Inequity:** Most "AI browsers" target flagship hardware. Millions of real-world Android users browse on 2–4GB RAM devices where a bundled 7B model causes immediate Out-Of-Memory (OOM) crashes and system lag.
- **Mobile Multitasking Friction:** Mobile browsers force users into single-tab tunnel vision. Comparing two to four live web pages (e.g. documentation, live feeds, prices) requires jarring tab switches or reloading page state.
- **Aggressive Web Tracking & Malvertising:** Pervasive trackers degrade mobile performance and drain battery, while deceptive download vectors exploit unmonitored browser download folders.

---

## 3. Goals

1. **Ship a Real, Usable Browser:** Complete native Android shell with multi-tab lifecycle, bookmarks, downloads, history, dark mode, reader mode, and PWA shortcuts — not a web wrapper or proof-of-concept demo.
2. **Deterministic Security Authority:** Enforce deterministic security policies on every navigation, download, and redirect — auditable, explainable, and never bypassed by AI.
3. **Nova QuadView Productivity:** Allow users to view and interact with up to four live browser tab sessions simultaneously in a responsive 2×2 grid or single-tap Focus Mode without destroying or reloading page state.
4. **Local Ad & Tracker Shielding:** Filter network trackers and ads locally in $O(\text{labels})$ lookup time and inject cosmetic element hiding without remote telemetry.
5. **Low-Memory First Architecture:** Run reliably on **low-memory Android devices** (2GB RAM class) via hardware-aware RAM tiering, zero idle AI overhead, and strict resource budgets.
6. **Local-First History Retrieval:** Fast, deterministic lexical history search powered by SQLite FTS5 with BM25 ranking, setting the foundation for local-first semantic retrieval.
7. **Empirical Honesty:** State security boundaries accurately ("UNKNOWN != SAFE") without claiming impossible guarantees like "100% immune to malware" or "zero malicious links".

---

## 4. Non-Goals

- **No Custom Rendering Engine:** We do not build an independent web rendering engine from scratch. NovaBrowser leverages the hardened Android WebView / Chromium platform implementation.
- **No Absolute Security Guarantees:** We will never claim "zero malicious links can ever open" or "100% malware immunity". Offline threat feeds capture known bad actors, while heuristics catch statistical anomalies; unknown threats can exist.
- **No Mandatory Cloud Services:** Browsing, gating, history search, and shield enforcement require zero cloud dependencies. Cloud AI is strictly out of scope for the core offline browser.
- **No Open-Ended General Chatbot:** NovaBrowser is a browser, not a chatbot wrapper. The intelligence layer is strictly scoped to search query parsing, page summarization, and retrieval formatting.
- **No Cross-Device Sync in v1:** Cross-device sync is deferred to Phase 6 (requires zero-knowledge encryption architecture).

---

## 5. Target Users

| Persona | Primary Need | NovaBrowser Value Proposition |
|---|---|---|
| **Budget & Mid-Tier Android Users** | Fast, stable browsing on 2–4GB RAM devices without thermal throttling or app restarts. | Low-memory tiering, zero AI RAM overhead when idle, and lightweight native components. |
| **Privacy-Conscious Individuals** | Browsing history, cookies, and search queries must never leave the device. | 100% offline security gate, local SQLite FTS5 database, zero remote telemetry, biometric private tabs. |
| **Power Users & Researchers** | Comparing multiple live web pages, documentation, and dashboards side-by-side on mobile. | **Nova QuadView**: up to 4 concurrent live tabs in 2×2 grid / Focus Mode without session recreation. |
| **Security Auditors & Evaluators** | Auditable, predictable protection against homoglyphs, phishing, and malvertising. | Deterministic Security Gate with explainable warning interstitials, download quarantine, and SHA-256 verification. |

---

## 6. Feature Inventory & Implementation Status

### 6.1 Core Browser Foundation
- [x] **Multi-Tab Architecture:** Dynamic tab lifecycle via `TabManager` supporting standard and isolated private tabs.
- [x] **Navigation Stack:** Full Back, Forward, Reload, Stop, and URL sanitization workflows.
- [x] **Address Bar / Omnibox:** Search-as-you-type suggestions, default search engine selection (Google, DuckDuckGo, Bing, Brave, Ecosia, Startpage).
- [x] **Bookmarks Management:** Add, edit, remove bookmarks; export/import standard HTML bookmarks format (`BookmarkHtmlManager`).
- [x] **History System:** Local SQLite history persistence with FTS5 lexical token search (`history_fts`) and time filters.
- [x] **Downloads Engine:** Background downloads with progress tracking, pause/resume, and Android notification updates.
- [x] **Web Dark Theme:** Algorithmic darkening and AndroidX WebKit `ForceDark` integration.
- [x] **Reader Mode:** Distraction-free article extraction (`ReaderExtractor`) with customizable typography and theme presets.
- [x] **Media Sniffer Engine:** Automatic detection and stream capture for downloadable web media elements.
- [x] **Offline Web Archive:** Save complete web pages as MHTML archives for offline reading (`OfflinePageManager`).
- [x] **Biometric Authentication:** Fingerprint/face unlock protection for private browsing sessions and security settings (`NovaBiometricHelper`).

### 6.2 Nova QuadView (Spatial Multi-Tab Workspace)
- [x] **2×2 Live Grid:** Display up to 4 concurrent, live browser tabs in a synchronized spatial workspace.
- [x] **Session Preservation:** Reparent live `NovaWebView` instances directly between single-tab and quad containers without reloading or resetting DOM/session state (*"Layout change != session recreation"*).
- [x] **Active Pane Management:** Distinct visual focus indicator, active tab title binding, and bidirectional synchronization with omnibox and bottom dock.
- [x] **Focus Mode:** Instant single-tap or double-tap expansion of any quad pane to dominant view without evicting other live sessions.
- [x] **Responsive Geometry:** Adaptive vertical and horizontal split weights optimizing 2×2 layout for both Portrait and Landscape orientations.
- [x] **Pane Context Menus & Slot Swapping:** Contextual pane controls allowing in-place reload, slot-to-slot swapping, tab replacement, and direct slot closure.
- [x] **Privacy Boundary Enforcement:** Strict isolation preventing private tabs from mixing with standard tabs inside the same QuadView workspace.

### 6.3 Deterministic Security Gate
- [x] **Deterministic Evaluation Authority:** URL navigation gate executing on `IO` dispatcher in $\le 3\text{ ms}$; zero reliance on probabilistic AI.
- [x] **URL Canonicalization:** Punycode / IDN conversion (`xn--`), recursive percent-decoding, and port normalization (`UrlCanonicalizer`).
- [x] **Local Threat Feed Matching:** Fast binary and hash matching against embedded URLhaus malware datasets (`ThreatFeedManager`).
- [x] **Statistical & Algorithmic Heuristics:** Shannon entropy calculation for randomized DGA domains and Levenshtein distance checks for brand typosquatting / homoglyphs (`HeuristicsEngine`).
- [x] **Redirect Loop & Downgrade Interception:** Deep redirect inspection intercepting chains $> 4$ hops and SSL-stripping protocol downgrades (`RedirectTracker`).
- [x] **Explainable Security Warning Interstitial:** Dedicated warning screen rendering granular threat categories, feed sources, and risk scores with explicit override options (`SecurityWarningActivity`).
- [x] **In-App Security Diagnostics Runner:** Built-in self-check runner verifying 5 live test vectors in airplane mode (`NovaDiagnostics`).

### 6.4 Ad, Tracker & Privacy Protection
- [x] **Network AdBlock Engine:** Subresource request filtering using domain label tree matching over EasyList/EasyPrivacy rules (`AdBlockEngine`).
- [x] **Cosmetic Element Hiding:** Injection of batched CSS display rules hiding intrusive ad containers (`CosmeticEngine`).
- [x] **Site Shields Manager:** Brave-style granular per-domain controls (toggle shields, ad blocking, cosmetic filters, JavaScript, third-party cookies).
- [x] **Privacy Guardrails:** Default third-party cookie blocking, HTTPS-only auto-elevation, and Do-Not-Track / Global Privacy Control header injection.
- [x] **Download Quarantine Sandbox:** Dangerous file types (`.apk`, `.dex`, `.sh`, `.exe`) routed to `.nova_quarantine/` with SHA-256 integrity digest pending explicit user release (`DownloadHandler`).
- [x] **Known Boundary Acknowledged:** Simple domain filtering cannot remove server-side injected video ads (e.g. YouTube CDN media streams) without custom stream parsing.

### 6.5 History Search & Retrieval
- [x] **FTS5 Lexical Search:** Fast full-text token search (`"word"*` prefix matching) ranked by SQLite BM25 algorithms.
- [x] **Graceful Fallback:** Substring SQL `LIKE` fallback if FTS5 is uncompiled or encountering syntax issues.
- [x] **Temporal Filter Chips:** Quick filter chips for Today, Yesterday, Security Warnings, and Bookmarks.
- [ ] **Arbitrary Natural Language Date Parsing:** *[Planned / In Progress]* Semantic parsing of complex temporal queries (e.g. `"docs from last Tuesday"`).
- [ ] **On-Device Vector Search:** *[Planned / Roadmap]* Local embedding generation and nearest-neighbor vector retrieval.

### 6.6 Local AI & Machine Learning
- [x] **Hardware Device Tier Detection:** Dynamic RAM inspection classifying host device into `MINIMAL` ($\le 2\text{GB}$), `LIGHT` ($3\text{--}4\text{GB}$), or `STANDARD` ($\ge 6\text{GB}$) (`DeviceTierDetector`).
- [x] **AI Engine Architecture Contract:** Modular stub isolating AI execution from browser startup and UI threads (`AiEngine`).
- [ ] **Native Inference Runtime:** *[Planned / In Progress]* `llama.cpp` Android NDK compilation and quantized GGUF model execution.
- [ ] **Contextual Page Summarization:** *[Planned / Roadmap]* On-demand reader summarization via local small language models (0.5B–1.5B).
- [ ] **In-Page Question Answering:** *[Planned / Roadmap]* Local retrieval-augmented Q&A over current page DOM.

### 6.7 Platform & Ecosystem Roadmap
- [ ] **Desktop Shell (Phase 5):** Electron / Chromium desktop application sharing the deterministic security core.
- [ ] **Encrypted Cross-Device Sync (Phase 6):** End-to-end encrypted synchronization for bookmarks and history.

---

## 7. Key Architectural Invariants

1. **AI is Never the Security Authority:** No AI model output may allow, block, or downgrade a Security Gate decision. Allow/block decisions are 100% deterministic, auditable, and testable.
2. **"Unknown" $\neq$ "Safe":** Unlisted domains evaluate to `RiskState.UNKNOWN`. NovaBrowser never claims a link is safe simply because it does not appear on a blocklist.
3. **Zero Telemetry by Default:** No user browsing events, search queries, or visited domains are transmitted to any remote server.
4. **Layout Change $\neq$ Session Recreation:** Switching between single-tab view, tab switcher, and Nova QuadView reparents existing WebView view hierarchies without reloading or dropping session state.
5. **Private Means Zero Disk Persistence:** Private tabs write zero rows to SQLite tables, suppress cache, clear session cookies on tab closure, and require biometric re-authentication.

---

## 8. Target Performance Metrics

| Metric | Target | Actual Verified Status |
|---|---|---|
| **APK Binary Size** | $< 40\text{ MB}$ | **Achieved:** $\sim 7.2\text{ MB}$ debug APK, $\sim 9.8\text{ MB}$ release APK |
| **Security Gate Evaluation Latency** | $< 10\text{ ms}$ | **Achieved:** $\le 3\text{ ms}$ in-memory lookup on Android 14/15 |
| **AdBlock Request Interception** | $< 2\text{ ms}$ | **Achieved:** Fast-path hash map lookup |
| **Idle Memory Footprint** | $< 150\text{ MB}$ | **Achieved:** Minimal tier runs smoothly on 2GB RAM devices |
| **QuadView Pane Transition** | $< 100\text{ ms}$ | **Achieved:** Zero reload, smooth View reparenting |
| **Unit Test Suite Coverage** | $100\%$ Pass Rate | **Achieved:** 121 Gradle tasks pass with zero regressions |

---

## 9. Constraints & Dependencies

- **OS Target:** Android 7.0 (API level 24) minimum; target SDK 35 (Android 15) with forward compatibility for Android 16 (API 36).
- **Toolchain:** OpenJDK 17, Android Gradle Plugin 8.7+, Kotlin 1.9+.
- **Rendering Engine:** System Android WebView (`androidx.webkit:webkit:1.12.1`).
- **Data Persistence:** Android SQLite 3 with FTS5 virtual table extension.
- **Third-Party Data Licensing:** URLhaus (Abuse.ch, CC0 / Open Data) and EasyList/EasyPrivacy (GPLv3 / CC BY-SA 3.0). Attribution displayed in Settings.
