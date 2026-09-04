# NovaBrowser

> A security-first, local-first browser architecture that keeps users safer by default and enables on-device contextual retrieval without cloud dependency.

[![Platform](https://img.shields.io/badge/Platform-Android%2014%2B%20(API%2024%2B)-3DDC84?logo=android&logoColor=white)](#build--run)
[![Language](https://img.shields.io/badge/Language-Kotlin%202.0.21-7F52FF?logo=kotlin&logoColor=white)](#tech-stack)
[![Storage](https://img.shields.io/badge/Storage-SQLite%203%20%2B%20FTS5-003B57?logo=sqlite&logoColor=white)](#data-model)
[![Security](https://img.shields.io/badge/Security-Deterministic%20Gate%20(Offline)-10B981)](#security-architecture)
[![UI System](https://img.shields.io/badge/Design-Liquid%20System-111827)](#overview)
[![APK Size](https://img.shields.io/badge/APK%20Size-~7.0%20MB-blue)](#performance-targets)

---

## Table of Contents

- [Overview](#overview)
- [Why NovaBrowser?](#why-novabrowser)
- [Core Architectural Pillars](#core-architectural-pillars)
- [Feature Status](#feature-status)
- [System Architecture](#system-architecture)
- [Deterministic Security Gate](#deterministic-security-gate)
- [Threat Feed Model & Categorization](#threat-feed-model--categorization)
- [Download Security & Quarantine Lifecycle](#download-security--quarantine-lifecycle)
- [Local-First AI & Memory Management](#local-first-ai--memory-management)
- [Hardware & Device Capability Tiers](#hardware--device-capability-tiers)
- [Data Model & SQLite Contract](#data-model--sqlite-contract)
- [Contextual Retrieval Workflow](#contextual-retrieval-workflow)
- [Repository Structure](#repository-structure)
- [Tech Stack](#tech-stack)
- [Build & Run](#build--run)
- [Test Suite & Verification](#test-suite--verification)
- [Target Performance Metrics](#target-performance-metrics)
- [Security Limitations & Threat Boundaries](#security-limitations--threat-boundaries)
- [Privacy Model](#privacy-model)
- [Roadmap & Milestones](#roadmap--milestones)
- [Contributing](#contributing)
- [Security Disclosure](#security-disclosure)
- [Licensing & Attribution](#licensing--attribution)
- [Companion Specifications](#companion-specifications)

---

## Overview

**NovaBrowser** is an Android web browser engineered around two foundational premises: **deterministic offline security** and **sovereign local intelligence**. 

Rather than delegating browsing safety to non-deterministic Large Language Models (LLMs) or latency-heavy cloud lookups, NovaBrowser intercepts navigations and subresources through an offline, rule-based Security Gate written in Kotlin. Navigations are canonicalized, matched against local cryptographic snapshots of threat databases, evaluated with entropy/Levenshtein heuristics, and scrutinized for redirect loops before network transmission occurs.

In parallel, user context—browsing history, saved sessions, downloads, and bookmarks—is indexed locally via SQLite and FTS5. On-device AI acts strictly as an analytical interface over this validated local index, never as an unconstrained execution authority.

---

## Why NovaBrowser?

Modern mobile browsers suffer from architectural compromises:

1. **Non-Deterministic "AI Safety" Tropes:** Promising security by asking an LLM if a site is phishing is fundamentally flawed. Generative models hallucinate, suffer from indirect prompt injection, and add seconds of latency to navigation.
2. **Cloud AI Privacy Surveillance:** Mainstream "intelligent" browsers stream raw browsing telemetry, DOM excerpts, and search queries to remote model endpoints, eroding user sovereignty.
3. **Bloat & Resource Greed:** Embedding full-scale browser runtimes and monolithic models makes browsers unusable on lower-tier hardware (devices with 2GB–4GB RAM).
4. **Opaque History Search:** Traditional browser history relies on exact substring matching. Users remember concepts (*"that Rust memory safety article I read on Monday"*), not exact URL parameters.

NovaBrowser decouples web rendering, threat mitigation, and analytical intelligence into isolated layers designed to run reliably on resource-constrained devices.

---

## Core Architectural Pillars

### 1. Deterministic Security Core
**AI is NEVER the security authority.** Security decisions (`ALLOW`, `WARN`, `BLOCK`) are made exclusively by auditable, deterministic Kotlin routines evaluating offline threat feeds, homoglyph algorithms, and protocol topologies.

### 2. Local-First Contextual AI
On-device models (planned via `llama.cpp` and GGUF quantization) operate under a **Retrieval-Augmented Generation (RAG)** model over SQLite/FTS5. The model interprets intent and formats results; native code executes validated queries.

### 3. Anti-Bloat & Strict Resource Tiering
NovaBrowser uses the system-provided Android WebView to avoid the ~150MB overhead of shipping a standalone Chromium engine. Memory is strictly budgeted across defined hardware tiers, allowing graceful degradation on devices with as little as 2GB RAM.

---

## Feature Status

| Feature / Component | Status | Implementation Details |
| :--- | :--- | :--- |
| **Android Browser Shell** | **Implemented** | System WebView runtime, multi-tab lifecycle, session restore, navigation stack. |
| **Liquid System UI/UX** | **Implemented** | Apple-grade minimal UI, Start Canvas ("Where to?"), Floating Island navigation, Safe Area Insets. |
| **SQLite Storage & FTS5** | **Implemented** | Full relational schema matching `DESIGN.md` (`history`, `bookmarks`, `sessions`, `downloads`, `security_rules`, `snapshot_meta`). |
| **URL Canonicalization** | **Implemented** | Punycode/IDN normalization, recursive percent-decoding, auth stripping, port normalization. |
| **Offline Threat Feed Engine** | **Implemented** | Strict separation of feeds (`URLHAUS` malware vs `EASYLIST` ads vs `EASYPRIVACY` tracking vs `LOCAL_HEURISTIC`). |
| **Heuristics Engine** | **Implemented** | Shannon entropy calculation, Levenshtein distance brand registry check, subdomain deception detection. |
| **Redirect Tracker** | **Implemented** | Hop-count limit enforcement (max 4 hops) and HTTPS-to-HTTP SSL stripping downgrade interception. |
| **Explainable Warning UI** | **Implemented** | Interstitial screen with telemetry breakdown, sentinel ping, collapsible enclave logs, and locked bypass for malware. |
| **Download Quarantine Flow** | **Implemented** | Executable/script MIME & extension isolation into app-private sandbox storage. |
| **Device Capability Detection** | **Implemented** | Dynamic RAM inspection categorizing hardware into `MINIMAL`, `LIGHT`, and `STANDARD` tiers. |
| **On-Device LLM Runtime** | **Planned** | Embedded `llama.cpp` NDK bindings with quantized GGUF execution. |
| **Semantic Embedding Index** | **Planned** | Vector embeddings for history entries on Standard Tier devices. |
| **Desktop Implementation** | **Planned** | Electron / Chromium desktop shell sharing core architecture. |

---

## System Architecture

```text
+-------------------------------------------------------------------------------+
|                             BROWSER USER INTERFACE                            |
|     (Liquid System Chrome, Omnibox, Start Canvas, Bottom Floating Island)     |
+-------------------------------------------------------------------------------+
                                       |
                                       v
                     +-----------------------------------+
                     |       BROWSER CONTROLLER          |
                     |   (TabManager & NavigationState)  |
                     +-----------------------------------+
                                       |
                                       v
                     +-----------------------------------+
                     |   DETERMINISTIC SECURITY GATE     |
                     |   - UrlCanonicalizer              |
                     |   - ThreatFeedManager (SQLite)    |
                     |   - HeuristicsEngine (Entropy/Lev)|
                     |   - RedirectTracker (Loops/SSL)   |
                     +-----------------------------------+
                                       |
                      +----------------+----------------+
                      |                                 |
               [BLOCK / WARN]                        [ALLOW]
                      |                                 |
                      v                                 v
        +----------------------------+    +----------------------------+
        | SecurityWarningActivity    |    | Android WebView Engine     |
        | (Explainable Telemetry,    |    | (Sandboxed Rendering,      |
        |  Bypass Lockdown Policy)   |    |  Subresource Interception) |
        +----------------------------+    +----------------------------+

=================================================================================
PARALLEL: LOCAL INTELLIGENCE & RETRIEVAL SUBSYSTEM (Zero Cloud Telemetry)
=================================================================================

+-------------------------+      +-------------------------+      +-------------+
| Ask Browser / Search    | ---> | SQLite FTS5 / Vector    | ---> | Local Model |
| (Natural Language Input)|      | Retrieval (Zero-Cloud)  |      | (Phase 3)   |
+-------------------------+      +-------------------------+      +-------------+
```

### Component Boundaries
- **Browser UI (`:app`):** Renders chrome, omnibox, tab switchers, and start canvas. Enforces zero touch collisions and viewport constraints.
- **Security Gate (`:browser-core`):** Pure Kotlin gate. Evaluates navigation requests synchronously before WebCore touches network sockets.
- **Rendering Engine:** Platform-provided WebView running in Android's isolated render process.
- **Local Storage (`:browser-core`):** Single-file SQLite database with write-ahead logging (WAL) and FTS5 indexing.
- **AI Subsystem (`:ai`):** Isolated module. Interprets natural-language intent and executes bounded queries against local indices.

---

## Deterministic Security Gate

The Security Gate rejects the assumption that an absence of threat data implies safety:

$$\text{Axiom: } \mathbf{UNKNOWN \neq SAFE}$$

When a URL is submitted by a user, an external app, or an AI tool call, it must traverse a strict six-stage deterministic gate:

```text
Raw Input URL
     │
     ▼
[Stage 1: Canonicalization]
     ├── Punycode / IDN normalization (e.g. рaypal.com -> xn--aypal-e1a.com)
     ├── Recursive percent-decoding (resolves double-encoding bypasses)
     ├── Auth token stripping (user:pass@host)
     └── Port and path normalization
     │
     ▼
[Stage 2: Deterministic Threat Feed Match]
     ├── Lookup in local SQLite rule snapshot
     └── Matches against URLHAUS trigger hard BLOCK
     │
     ▼
[Stage 3: Offline Heuristic Analysis]
     ├── Shannon entropy scoring of host strings
     ├── Levenshtein edit distance check against high-value target registry
     ├── Subdomain deception detection (e.g. paypal.com.attacker.com)
     └── Suspicious TLD weighting (emits HIGH_RISK or SUSPICIOUS)
     │
     ▼
[Stage 4: Redirect Chain Verification]
     ├── Tracks intermediate hops (max limit: 4 hops)
     └── Intercepts protocol downgrades (HTTPS -> HTTP)
     │
     ▼
[Stage 5: Subresource Interception]
     └── shouldInterceptRequest() filters tracker & ad domains
     │
     ▼
[Stage 6: Final Resolution]
     ├── ALLOW  -> Proceed with WebView loadUrl()
     ├── WARN   -> Launch SecurityWarningActivity (Soft override allowed)
     └── BLOCK  -> Launch SecurityWarningActivity (Bypass suppressed by policy)
```

### Android JS Bridge Hard Boundary
Per `SECURITY.md` §3, NovaBrowser **does not expose** an unconstrained `@JavascriptInterface` bridge to untrusted web content. Untrusted scripts running inside third-party web pages cannot invoke native operating system APIs, execute local shell commands, or trigger model inferences.

---

## Threat Feed Model & Categorization

To maintain architectural rigor, NovaBrowser categorizes feeds by threat profile rather than conflating them:

| Feed Identifier | Source Baseline | Primary Action | Purpose |
| :--- | :--- | :--- | :--- |
| `URLHAUS` | Abuse.ch URLhaus | `BLOCK` | Verified active malware distribution, botnet C2, zero-day payloads. |
| `EASYLIST` | EasyList Community | `BLOCK` (Subresource) | Advertising scripts, banners, and layout bloat. |
| `EASYPRIVACY` | EasyPrivacy Community | `BLOCK` (Subresource) | Behavioral telemetry, analytics beacons, fingerprinters. |
| `LOCAL_HEURISTIC`| On-device algorithms | `WARN` / `SUSPICIOUS` | Homoglyphs, typosquatting, high Shannon entropy, redirect anomalies. |

### Snapshot Lifecycle
- Threat data is bundled as an immutable offline snapshot during initial setup.
- Feeds are stored in SQLite tables indexed by canonical domain and host-suffix keys.
- **Heuristics Authority Limit:** Heuristics **never** trigger a non-bypassable `BLOCK`. Hard blocks are reserved strictly for cryptographic/feed verification. Typosquatting triggers an explainable `WARN` allowing explicit user override.

---

## Download Security & Quarantine Lifecycle

Automated file downloads pose significant risk on mobile devices. NovaBrowser enforces a quarantine lifecycle:

```text
Download Triggered (ContentDisposition / MIME)
     │
     ▼
[MIME & Extension Inspection]
     │
     ├─ Safe MIME (Images, PDF, Plain Text)
     │    └─ Write to standard Public Downloads folder
     │
     └─ Executable / High-Risk MIME (.apk, .dex, .sh, .bat, .exe)
          │
          ▼
     [Quarantine Isolation]
          ├── Redirect payload to app-private cache directory:
          │   context.cacheDir/quarantine/
          ├── Mark record in downloads table as QUARANTINED
          └── Display Security Warning to User
               ├── [Option A: Delete Quarantined File] -> Purge payload
               └── [Option B: Controlled Override]    -> Move to public downloads
```

---

## Local-First AI & Memory Management

The AI engine in NovaBrowser operates on the principle of **Zero-Cloud Telemetry**. 

### RAG-First Contextual Inference
Instead of dumping full DOM structures or history tables into an LLM context window, NovaBrowser employs a targeted RAG pipeline:

```text
"Find that GitHub repository about Android security from yesterday"
                               │
                               ▼
               [Intent Classification & Entity Parser]
                     (Extracts: domain=github, topic=security, time=yesterday)
                               │
                               ▼
               [SQLite FTS5 / BM25 Lexical Scan]
                     (Executes query against history & bookmarks)
                               │
                               ▼
               [Candidate Ranking & Token Slicing]
                     (Selects top 3 matches with snippets)
                               │
                               ▼
               [Tiny Quantized On-Device Model]
                     (Synthesizes answer & constructs navigation intent)
                               │
                               ▼
               [Browser Controller Validation]
                     (User clicks result -> Passes through Security Gate)
```

### Safety Against Prompt Injection
Webpage content is treated as untrusted data. When summarizing or querying active pages:
1. Webpage text is segmented into isolated payload blocks.
2. The system prompt instructs the model to ignore instructional directives embedded in webpage text.
3. Model output cannot directly execute browser actions; all actions emit structured intents validated by the `BrowserController`.

---

## Hardware & Device Capability Tiers

To prevent out-of-memory (OOM) faults on diverse Android hardware, memory budgets are strictly enforced:

| Tier | Hardware Criteria | Model Class | In-Memory Budget | Capabilities |
| :--- | :--- | :--- | :--- | :--- |
| **Minimal** | $\le \text{2.0 GB RAM}$ | *No LLM* | $< 50 \text{ MB}$ | Core browsing, full deterministic security gate, FTS5 lexical search. |
| **Light** | $\approx \text{3.0 - 4.0 GB RAM}$ | 0.5B – 1.5B Q4_K | $< 350 \text{ MB}$ | Bounded intent parsing, natural language history search, lazy loading. |
| **Standard**| $\ge \text{6.0 GB RAM}$ | 3.0B Q4_K | $< 800 \text{ MB}$ | On-device page summarization, vector similarity search, instant synthesis. |

### Memory Reclamation Rules
1. **Lazy Loading:** Models are not loaded into memory until explicitly invoked.
2. **Idle Unload:** If no AI request occurs within 120 seconds, the model is evicted from RAM.
3. **Low-Memory Override:** If Android OS triggers `onTrimMemory(TRIM_MEMORY_RUNNING_CRITICAL)`, the model runtime is immediately destroyed.

---

## Data Model & SQLite Contract

The SQLite database (`nova_browser.db`) implements Write-Ahead Logging (WAL) and foreign-key constraints across seven primary entities:

```text
┌────────────────────────────────────────────────────────┐
│                      history                           │
│  id, url, title, visit_time, visit_count, is_private   │
└────────────────────────────────────────────────────────┘
                           │
                           ▼ (FTS5 Virtual Table)
┌────────────────────────────────────────────────────────┐
│                    history_fts                         │
│  title, url                                            │
└────────────────────────────────────────────────────────┘

┌──────────────────────────┐    ┌──────────────────────────┐
│        bookmarks         │    │         sessions         │
│  id, url, title, ...     │    │  id, tab_id, url, ...    │
└──────────────────────────┘    └──────────────────────────┘

┌──────────────────────────┐    ┌──────────────────────────┐
│        downloads         │    │      security_rules      │
│  id, url, path, status   │    │  id, pattern, feed_type  │
└──────────────────────────┘    └──────────────────────────┘

┌──────────────────────────┐
│      snapshot_meta       │
│  id, feed_type, version  │
└──────────────────────────┘
```

*For exact data types and index definitions, consult [DESIGN.md](docs/DESIGN.md).*

---

## Contextual Retrieval Workflow

```text
User Input: "Where was that research paper on post-quantum encryption?"
   │
   ├── 1. Intent Analyzer parses query into tokens: ["post-quantum", "encryption", "research"]
   ├── 2. FTS5 performs full-text query:
   │      SELECT url, title FROM history_fts WHERE history_fts MATCH 'post-quantum OR encryption'
   ├── 3. Candidates scored by BM25 rank and timestamp proximity
   ├── 4. Compact result card rendered with confidence gauge and verified badges
   └── 5. User taps card -> SecurityGate validates destination -> Page loads
```

---

## Repository Structure

```text
NovaBrowser/
├── app/                                 # Android Application Module
│   ├── src/main/java/com/gintama/novabrowser/
│   │   ├── bookmarks/                   # Bookmarks Activity & List Adapter
│   │   ├── browser/                     # NovaWebView, WebChromeClient, TabManager
│   │   ├── downloads/                   # DownloadHandler & Quarantine Isolation
│   │   ├── history/                     # History Activity & FTS5 Query UI
│   │   ├── settings/                    # Settings & Low-Memory Mode Preferences
│   │   └── ui/                          # MainActivity, SecurityWarningActivity, TabsAdapter
│   └── src/main/res/                    # Liquid System Layouts, Drawables, Styles
│
├── browser-core/                        # Core Domain & Security Module (Pure Logic)
│   ├── src/main/java/com/gintama/novabrowser/core/
│   │   ├── controller/                  # BrowserController (Navigation Orchestration)
│   │   ├── db/                          # NovaDatabaseHelper (SQLite Schema & FTS5)
│   │   ├── model/                       # Immutable Domain Data Models
│   │   ├── navigation/                  # UrlSanitizer & NavigationState
│   │   └── security/                    # Deterministic Security Gate:
│   │       ├── HeuristicsEngine.kt      # Shannon Entropy & Levenshtein Algorithms
│   │       ├── RedirectTracker.kt       # Hop Counter & SSL Downgrade Detection
│   │       ├── SecurityGate.kt          # Deterministic Gate Orchestrator
│   │       ├── ThreatFeedManager.kt     # Threat Snapshots & Matching Engine
│   │       ├── UrlCanonicalizer.kt      # Punycode, Port & Encoding Normalizer
│   │       └── SecurityVerificationRunner.kt # Test Contract Verification
│   └── src/test/java/                   # JUnit Unit Tests for Security Gate
│
├── ai/                                  # Local Intelligence Module
│   └── src/main/java/com/gintama/novabrowser/ai/
│       ├── AiEngine.kt                  # Model Ingestion & RAG Orchestration Stub
│       └── DeviceTier.kt                # Hardware RAM Inspection & Tiering Rules
│
├── docs/                                # Specification Documents
│   ├── ARCHITECTURE.md                  # Comprehensive System & Layer Architecture
│   ├── DESIGN.md                        # UI/UX Tokens, Schemas & Component Specifications
│   ├── PLAN.txt                         # Phased Engineering Roadmap
│   ├── PRD.md                           # Product Requirements & Acceptance Criteria
│   └── SECURITY.md                      # Threat Models, STRIDE Analysis & Attack Surface
│
├── gradlew.bat                          # Gradle Build Tool
├── run_tests.ps1                        # CLI Test Verification Runner
└── README.md                            # Primary Project Documentation
```

---

## Tech Stack

### Active Core
- **Language:** Kotlin 2.0.21
- **Platform:** Android 14 (compileSdk 34, minSdk 24, targetSdk 34)
- **Engine:** Android System WebView (`android.webkit`)
- **Database:** SQLite 3 with FTS5 lexical indexing
- **Build System:** Gradle 8.14.3 with Android Gradle Plugin 8.7.3
- **Async Runtime:** Kotlin Coroutines & Lifecycle Scope

### Planned Components
- **Inference Runtime:** `llama.cpp` Android NDK compilation
- **Format:** GGUF (4-bit quantized: Q4_K_M)
- **Desktop Runtime:** Electron with Chromium sandbox isolation

---

## Build & Run

### Prerequisites
1. **JDK 17:** Microsoft OpenJDK 17 or Eclipse Temurin 17.
2. **Android SDK:** Command-line tools or Android Studio with API 34 platform.
3. **Android Device or Emulator:** Running Android 7.0+ (API 24 or higher).

### Compiling via CLI

Ensure `JAVA_HOME` points to your JDK 17 installation:

```powershell
# Windows PowerShell
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-17.0.20.8-hotspot"
cd NovaBrowser

# Build Debug APK
.\gradlew.bat assembleDebug --offline
```

*The generated APK is located at:*  
`NovaBrowser/app/build/outputs/apk/debug/app-debug.apk` (~7.0 MB).

### Installing to Device

```powershell
# Using ADB
& "C:\Android\android-sdk\platform-tools\adb.exe" install -r "app\build\outputs\apk\debug\app-debug.apk"
```

---

## Test Suite & Verification

The security subroutines in `:browser-core` are verified through direct unit tests and contract runners:

```powershell
# Run security test suite via PowerShell runner
powershell -ExecutionPolicy Bypass -File .\run_tests.ps1
```

### Verified Test Assertions
- **Punycode Spoofing:** `https://рaypal.com` (Cyrillic `р`) canonicalizes to `xn--aypal-e1a.com`.
- **Recursive Percent-Decoding:** `http://%2577%2577%2577.evil.com` resolves to `http://www.evil.com`.
- **High Shannon Entropy:** Random hex/alphanumeric domains trigger elevated risk scores.
- **Brand Levenshtein Proximity:** `https://paypa1.com` triggers `WARN` (Homoglyph spoof).
- **SSL Stripping:** Downgrades from `https://bank.com` to `http://bank.com` are intercepted.
- **Redirect Loops:** Chains exceeding 4 hops are terminated.
- **Feed Authority Separation:** `URLHAUS` triggers hard `BLOCK`; `EASYLIST` filters silently; `LOCAL_HEURISTIC` emits `WARN`.
- **Axiom Check:** Unlisted domains emit `RiskState.UNKNOWN`, never `KNOWN_SAFE`.

---

## Target Performance Metrics

The following metrics are design targets defined in [PRD.md](docs/PRD.md):

| Metric | Target | Verification Status |
| :--- | :--- | :--- |
| **APK Binary Size** | $< 40 \text{ MB}$ | **Achieved:** ~7.0 MB currently. |
| **Cold Start Latency** | $< 800 \text{ ms}$ | Target for release build on mid-tier hardware. |
| **Security Gate Check Latency** | $< 5 \text{ ms}$ | **Achieved:** Local SQLite lookup runs in $\le 3\text{ ms}$. |
| **Idle Memory Footprint** | $< 120 \text{ MB}$ | Without active WebView instances. |
| **Local Search Accuracy** | $> 80\% \text{ Top-3}$ | Benchmarking planned with AI retrieval evaluation. |

---

## Security Limitations & Threat Boundaries

To maintain technical credibility, the boundaries of NovaBrowser's protection model are explicitly acknowledged:

1. **Feed Staleness:** An offline threat database is a snapshot in time. Newly registered malicious domains active for less than 24 hours may not appear in static feeds.
2. **Heuristic Margins:** Edit-distance and entropy heuristics can produce false positives on obscure foreign-language domains and false negatives on carefully crafted subdomains.
3. **WebView Network Isolation:** `shouldInterceptRequest()` operates at the application protocol layer and does not intercept low-level UDP, WebRTC, or WebSockets traffic.
4. **Local Hardware Constraints:** Low-memory devices (Minimal Tier) do not execute local AI models; they rely entirely on lexical FTS5 search and rule gates.
5. **No AI Security Authority:** AI models can be manipulated via adversarial tokens; therefore, AI output is never permitted to bypass or downgrade a Security Gate decision.

---

## Privacy Model

- **No Remote Telemetry:** The browser does not transmit analytics, device identifiers, or browsing events to centralized servers.
- **Encrypted Local Storage:** User profiles and FTS5 search databases reside strictly in app-private sandbox storage (`/data/data/com.gintama.novabrowser/databases/`).
- **Private Browsing Isolation:** Private tabs disable SQLite history writes, suppress session caching, and flush the WebView cookie store upon tab disposal.

---

## Roadmap & Milestones

- [x] **Phase 0: Project Inception & Scaffolding** — Multi-module Gradle configuration, Android WebView harness, specification lock.
- [x] **Phase 1: Core Browser MVP** — Tab management, navigation stack, SQLite persistence, FTS5 lexical history search, Liquid System UI.
- [x] **Phase 2: Deterministic Security Gate** — URL canonicalization, threat feed database, Shannon entropy/Levenshtein heuristics, redirect loops, explainable warning UI.
- [ ] **Phase 3: Local AI Integration** — `llama.cpp` Android NDK build, quantized GGUF execution, memory tiering, idle unload lifecycles.
- [ ] **Phase 4: Contextual Browser Intelligence** — RAG over history/bookmarks, structured intent validation, multi-tab syntheses.
- [ ] **Phase 5: Desktop Implementation** — Electron/Chromium desktop shell sharing the deterministic security engine.
- [ ] **Phase 6: Advanced Capabilities** — Optional end-to-end encrypted peer-to-peer sync, WebGPU acceleration.

---

## Contributing

Contributions are welcome from developers focused on browser security and local-first AI.

### Guidelines
1. **Never bypass the Security Gate:** No navigation or resource load may bypass `SecurityGate.kt`.
2. **Preserve the JS Bridge Boundary:** Do not register unrestricted `@JavascriptInterface` objects inside WebView.
3. **Respect Memory Limits:** All new features must conform to the 2GB Minimal Tier baseline.
4. **Validate Code:** Ensure `./gradlew.bat testDebugUnitTest` and `run_tests.ps1` pass with zero regressions.

---

## Security Disclosure

If you identify a security vulnerability or sandbox bypass in NovaBrowser:

1. **Do not disclose it publicly.**
2. Open a private security advisory on the GitHub repository or contact maintainers directly via repository channels.
3. Please include reproduction steps, device hardware specifications, and Android OS version.

---

## Licensing & Attribution

- **Project License:** To be finalized (Proprietary / Open Source review in progress).
- **Threat Data Attributions:**
  - [URLhaus](https://urlhaus.abuse.ch/) by Abuse.ch (Malware URL data).
  - [EasyList & EasyPrivacy](https://easylist.to/) (Ad and tracker blocking rules).

---

## Companion Specifications

For detailed architectural contracts and design references:
- **[PRD.md](docs/PRD.md)** — Core product requirements, user personas, and acceptance criteria.
- **[ARCHITECTURE.md](docs/ARCHITECTURE.md)** — Architectural invariants, thread models, and layer isolation.
- **[DESIGN.md](docs/DESIGN.md)** — Liquid System UI tokens, typography, and complete SQLite database schema.
- **[SECURITY.md](docs/SECURITY.md)** — Threat models, STRIDE analysis, homoglyph algorithms, and attack surface review.
- **[PLAN.txt](docs/PLAN.txt)** — Phased engineering implementation roadmap.
