# NovaBrowser

> A security-first, local-first browser architecture that keeps users safer by default and enables on-device contextual retrieval without cloud dependency.

[![Platform](https://img.shields.io/badge/Platform-Android%2015%2B%20(API%2035)-3DDC84?logo=android&logoColor=white)](#build--run)
[![Language](https://img.shields.io/badge/Language-Kotlin%202.0.21-7F52FF?logo=kotlin&logoColor=white)](#tech-stack)
[![Storage](https://img.shields.io/badge/Storage-SQLite%203%20%2B%20FTS5-003B57?logo=sqlite&logoColor=white)](#data-model--database-er-diagram)
[![Security](https://img.shields.io/badge/Security-Deterministic%20Gate%20(Offline)-10B981)](#deterministic-security-gate)
[![AdBlock](https://img.shields.io/badge/AdBlock-O(labels)%20Offline%20Engine-06B6D4)](#network-ad--tracker-blocking-engine)
[![Motion](https://img.shields.io/badge/Motion-NovaMotion%2060%2F120%20FPS-8B5CF6)](#novamotion-graphics--animation-framework)
[![UI System](https://img.shields.io/badge/Design-Liquid%20System-111827)](#visual-canvases--interface-showcase)
[![APK Size](https://img.shields.io/badge/APK%20Size-~7.0%20MB-blue)](#target-performance-metrics)

<p align="center">
  <img src="docs/assets/screens/core_mark.png" alt="NovaBrowser Core Mark" width="88" />
</p>

---

## Table of Contents

- [Overview](#overview)
- [Visual Canvases & Interface Showcase](#visual-canvases--interface-showcase)
  - [The 4 Core Canvases](#the-4-core-canvases)
  - [Canvas Interaction & Navigation Flow](#canvas-interaction--navigation-flow)
- [Why NovaBrowser?](#why-novabrowser)
- [Core Architectural Pillars](#core-architectural-pillars)
- [Feature Status](#feature-status)
- [System Architecture](#system-architecture)
  - [High-Level Layer Architecture](#high-level-layer-architecture)
  - [Component Boundaries & Isolation](#component-boundaries--isolation)
- [Deterministic Security Gate](#deterministic-security-gate)
  - [Security Gate Pipeline](#security-gate-pipeline)
  - [End-to-End Navigation Interception Flow](#end-to-end-navigation-interception-flow)
  - [Canonicalization Subroutines](#canonicalization-subroutines)
  - [Offline Heuristics & Homoglyph Math](#offline-heuristics--homoglyph-math)
  - [Android JS Bridge Hard Boundary](#android-js-bridge-hard-boundary)
- [Threat Feed Model & Categorization](#threat-feed-model--categorization)
- [Download Security & Quarantine Lifecycle](#download-security--quarantine-lifecycle)
- [Network Ad & Tracker Blocking Engine](#network-ad--tracker-blocking-engine)
  - [Subresource Interception & CDN Protection](#subresource-interception--cdn-protection)
  - [Batched Cosmetic Element Hiding](#batched-cosmetic-element-hiding)
  - [Shield Bottom Sheet & Per-Site Rules](#shield-bottom-sheet--per-site-rules)
- [NovaMotion Graphics & Animation Framework](#novamotion-graphics--animation-framework)
  - [Celestial Supernova Brand Identity](#celestial-supernova-brand-identity)
  - [Micro-Interactions & Fluid Transitions](#micro-interactions--fluid-transitions)
- [Security Diagnostics & Privacy Controls](#security-diagnostics--privacy-controls)
- [Local-First AI & Memory Management](#local-first-ai--memory-management)
  - [Contextual RAG Retrieval Pipeline](#contextual-rag-retrieval-pipeline)
  - [Zero-Cloud Telemetry & Prompt Injection Hardening](#zero-cloud-telemetry--prompt-injection-hardening)
- [Hardware & Device Capability Tiers](#hardware--device-capability-tiers)
- [Data Model & Database ER Diagram](#data-model--database-er-diagram)
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

## Visual Canvases & Interface Showcase

NovaBrowser implements the **Liquid System** visual specification: Apple-grade minimalism, restrained typography, squircle geometry, and a floating-island spatial layout.

### The 4 Core Canvases

| 1. Start Canvas ("Where to?") | 2. Live Browsing Canvas |
| :---: | :---: |
| <img src="docs/assets/screens/new_tab_canvas.png" alt="Start Canvas" width="360" /> | <img src="docs/assets/screens/live_browsing_canvas.png" alt="Live Browsing Canvas" width="360" /> |
| **Start Canvas (`layoutNewTabCanvas`)**<br/>• Hero Nova Core Mark squircle with pulsing status beacon.<br/>• Active glass search omnibox with On-Device AI badge.<br/>• 8 App Haven tiles (GitHub, arXiv, Linear, Notion, Docs, Figma, etc.).<br/>• Contextual Jump-Back-In sessions card.<br/>• Bottom floating island with spatial navigation. | **Live Browsing View**<br/>• 2px emerald reading progress indicator.<br/>• Floating security domain anchor pill with lock glyph.<br/>• Dedicated non-overlapping WebView container (`paddingBottom="76dp"`).<br/>• Contextual bottom "Ask Browser" query pill.<br/>• Clean gesture-friendly navigation controls. |

| 3. Fast Lexical History Search | 4. Explainable Security Warning |
| :---: | :---: |
| <img src="docs/assets/screens/ai_history_search.png" alt="Fast Lexical History Search" width="360" /> | <img src="docs/assets/screens/security_warning.png" alt="Security Warning Screen" width="360" /> |
| **Lexical History Search (`HistoryActivity`)**<br/>• Fast full-text keyword search with instant local processing.<br/>• Categorization filter chips (All, Dev, Papers, Repos).<br/>• Contextual retrieval powered by SQLite FTS5 BM25 ranking.<br/>• Offline BM25 lexical search (On-device LLM scheduled for Phase 3). | **Security Warning Screen (`SecurityWarningActivity`)**<br/>• Measured crimson optics with hazard shield.<br/>• Intercepted host card with real-time detection telemetry.<br/>• Hardware-verified threat breakdown (Homoglyph, Entropy).<br/>• Non-bypassable lock for verified URLHAUS malware. |

### Canvas Interaction & Navigation Flow

The user transitions seamlessly between the 4 spatial canvases through deterministic gestures and security signals:

```mermaid
flowchart TD
    Start["1. Start Canvas ('Where to?')<br/>• Hero Core Mark & Status Beacon<br/>• Glass Omnibox with AI Badge<br/>• 8 App Haven Tiles & Recent Sessions"]
    
    Gate{"Deterministic Security Gate<br/>• Punycode & Hex Normalization<br/>• URLHAUS & Threat Snapshots<br/>• Shannon & Levenshtein Check"}
    
    Live["2. Live Browsing Canvas<br/>• 2px Emerald Progress Indicator<br/>• Floating Secure Domain Pill<br/>• Safe-Area Clear Viewport<br/>• Bottom Nav Island"]
    
    Warn["4. Explainable Security Warning<br/>• Measured Crimson Optics<br/>• Deterministic Threat Telemetry<br/>• Rule ID & Risk Reason Breakdown"]
    
    History["3. Ask Browser & AI Search<br/>• Natural Language Query Pill<br/>• Category Filter Chips<br/>• FTS5 BM25 Lexical Ranking"]

    Start -->|"Enter URL / Tap Haven"| Gate
    Gate -->|"Clean (ALLOW)"| Live
    Gate -->|"Suspicious / Malware (WARN/BLOCK)"| Warn
    
    Warn -->|"Go Back to Safety"| Start
    Warn -.->|"User Override (Suspicious Only)"| Live
    
    Live -->|"Tap 'Ask Browser'"| History
    Live -->|"Close Tab / New Tab"| Start
    
    History -->|"Select Search Result"| Gate
    History -->|"Dismiss Modal"| Live

    classDef canvas fill:#0A0D14,stroke:#3B82F6,stroke-width:2px,color:#F9FAFB;
    classDef gate fill:#111827,stroke:#10B981,stroke-width:2px,color:#F9FAFB;
    classDef warn fill:#1F1315,stroke:#EF4444,stroke-width:2px,color:#FCA5A5;

    class Start,Live,History canvas;
    class Gate gate;
    class Warn warn;
```

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
| **NovaMotion Framework** | **Implemented** | 60/120 FPS hardware-accelerated animations, tactile spring feedback, hero celestial breathing loop, kinetic shield pulse, smooth cross-fades. |
| **Cyber-Celestial Logo** | **Implemented** | High-precision vector identity (`ic_nova_logo.xml`) with ambient radial glow aura and Android launcher integration. |
| **SQLite Storage & FTS5** | **Implemented** | Full relational schema (`history`, `bookmarks`, `sessions`, `downloads`, `security_rules`, `snapshot_meta`, `adblock_site_rules`, `broken_site_reports`, `site_permissions`). FTS5 BM25 search. |
| **URL Canonicalization** | **Implemented** | Punycode/IDN normalization, auth & port normalization, component-wise query sanitization. |
| **Offline Threat Feed Engine** | **Implemented** | Local ABP Filter Parser, 9,578 domain rules in memory hash index ($O(\text{labels})$ lookup), 50+ cosmetic selectors, per-site rules. |
| **Network Ad & Tracker Blocker**| **Implemented** | Subresource interception in `shouldInterceptRequest()`, media CDN whitelist preservation, per-tab/lifetime counters, zero telemetry. |
| **Cosmetic Element Hiding** | **Implemented** | Batched dynamic CSS injection (`display: none !important`) across 50+ selectors on `onPageFinished`. |
| **Privacy Guardrails** | **Implemented** | Third-party cookies blocked by default, HTTPS-only auto-upgrade, DNT/Sec-GPC header injection, clean link copying. |
| **Heuristics Engine** | **Implemented** | Shannon entropy calculation, Levenshtein distance brand registry check, subdomain deception detection. |
| **Redirect Tracker** | **Implemented** | Hop-count limit enforcement (max 4 hops) and HTTPS-to-HTTP SSL stripping downgrade interception. |
| **Explainable Warning UI** | **Implemented** | Interstitial screen with honest telemetry breakdown, sentinel status, and non-bypassable lock for verified malware. |
| **Download Quarantine Sandbox** | **Implemented** | Physical `.nova_quarantine/` sandbox isolation in app-private storage, streaming SHA-256 integrity digest, interactive user override release. |
| **Security Diagnostics Runner** | **Implemented** | Native 5-vector test suite in Settings evaluating live gate decisions, homoglyph detection, and adblock rules. |
| **Site Permissions Manager** | **Implemented** | SQLite-backed per-site permissions (camera, microphone, geolocation) with granular revocation. |
| **Device Capability Detection** | **Implemented** | Dynamic RAM inspection categorizing hardware into `MINIMAL`, `LIGHT`, and `STANDARD` tiers. |
| **On-Device LLM Runtime** | **Planned** | Embedded `llama.cpp` NDK bindings with quantized GGUF execution (Phase 3). |
| **Semantic Embedding Index** | **Planned** | Vector embeddings for history entries on Standard Tier devices (Phase 3). |

---

## System Architecture

### High-Level Layer Architecture

```mermaid
graph TD
    subgraph UI ["User Interface Layer (Liquid System)"]
        A["Top Chrome / Omnibox"]
        B["Start Canvas ('Where to?')"]
        C["Bottom Floating Island Nav"]
        D["History & Ask Browser Modal"]
    end

    subgraph Controller ["Browser Controller & Orchestration"]
        E["TabManager & Navigation State"]
        F["DownloadHandler & Quarantine"]
    end

    subgraph Security ["Deterministic Security Gate (Pure Kotlin :browser-core)"]
        G["UrlCanonicalizer: Punycode / Hex"]
        H["ThreatFeedManager: SQLite Feed Snapshots"]
        I["HeuristicsEngine: Shannon / Levenshtein"]
        J["RedirectTracker: Hop Counts & SSL Downgrades"]
    end

    subgraph Engine ["Isolated Web Runtime"]
        K["Android WebView Engine (Sandboxed Process)"]
        L["SecurityWarningActivity (Explainable Interstitial)"]
    end

    subgraph Storage ["Local Storage & Lexical Intelligence"]
        M[("SQLite Database + WAL")]
        N["FTS5 Full-Text Search Virtual Table"]
        O["DeviceTierDetector: RAM Budgeting"]
        P["Local Quantized LLM Runtime: Phase 3"]
    end

    A --> E
    B --> E
    C --> E
    D --> N

    E --> G --> H --> I --> J
    J -->|"ALLOW"| K
    J -->|"WARN / BLOCK"| L
    L -.->|"User Override (WARN only)"| K

    E <--> M
    N <--> M
    O -.->|"RAM Tier Constraints"| P
    P -->|"Structured Intent"| E

    classDef secure fill:#E6F9F0,stroke:#10B981,stroke-width:2px,color:#065F46;
    classDef warning fill:#FEE2E2,stroke:#EF4444,stroke-width:2px,color:#991B1B;
    classDef chrome fill:#F8F9FA,stroke:#111827,stroke-width:1px,color:#111827;
    classDef data fill:#EFF6FF,stroke:#3B82F6,stroke-width:1.5px,color:#1E40AF;

    class G,H,I,J secure;
    class L warning;
    class A,B,C,D,E,F,K chrome;
    class M,N,O,P data;
```

### Component Boundaries & Isolation

- **Browser UI (`:app`):** Renders chrome, omnibox, tab switchers, and start canvas. Enforces zero touch collisions and safe viewport window insets (`android:fitsSystemWindows="true"`).
- **Security Gate (`:browser-core`):** Pure Kotlin gate with zero Android framework dependencies. Evaluates navigation requests synchronously before WebCore touches network sockets.
- **Rendering Engine:** Platform-provided WebView running in Android's isolated render process.
- **Local Storage (`:browser-core`):** Single-file SQLite database with write-ahead logging (WAL) and FTS5 indexing.
- **AI Subsystem (`:ai`):** Isolated module. Interprets natural-language intent and executes bounded queries against local indices.

---

## Deterministic Security Gate

The Security Gate rejects the assumption that an absence of threat data implies safety:

$$\text{Axiom: } \mathbf{UNKNOWN \neq SAFE}$$

### Security Gate Pipeline

When a URL is submitted by a user, an external app, or an AI tool call, it must traverse a strict six-stage deterministic gate:

```mermaid
flowchart TD
    In([Raw Input URL]) --> S1[Stage 1: Canonicalization]
    
    subgraph S1_Details ["Canonicalization Subroutines"]
        S1 --> S1a[Punycode / IDN Normalization]
        S1a --> S1b[Recursive Percent-Decoding]
        S1b --> S1c[Auth & Port Sanitization]
    end
    
    S1c --> S2{Stage 2: Threat Feed Match?}
    S2 -->|"URLHAUS Match"| BlockAction["Action: Hard BLOCK<br/>RiskState: BLOCKED"]
    S2 -->|"No Feed Match"| S3[Stage 3: Offline Heuristics Engine]
    
    subgraph S3_Details ["Heuristics Subroutines"]
        S3 --> S3a[Shannon Entropy Analysis]
        S3a --> S3b[Levenshtein Distance Check]
        S3b --> S3c[Subdomain Deception Analysis]
    end
    
    S3c --> S4{Homoglyph / Typosquat Detected?}
    S4 -->|"Yes"| WarnAction["Action: Explainable WARN<br/>RiskState: HIGH_RISK"]
    S4 -->|"No"| S5[Stage 4: Redirect Chain Tracker]
    
    S5 --> S6{Hops > 4 OR HTTPS->HTTP?}
    S6 -->|"Loop or Downgrade"| WarnAction
    S6 -->|"Valid"| S7[Stage 5: Subresource Filter]
    
    S7 --> AllowAction["Action: ALLOW<br/>RiskState: KNOWN_SAFE or UNKNOWN"]
    
    BlockAction --> UI_Block["SecurityWarningActivity<br/>(Bypass Locked)"]
    WarnAction --> UI_Warn["SecurityWarningActivity<br/>(Proceed Allowed)"]
    AllowAction --> WebViewLoad["WebView.loadUrl"]

    classDef block fill:#FEE2E2,stroke:#EF4444,stroke-width:2px,color:#991B1B;
    classDef warn fill:#FEF3C7,stroke:#F59E0B,stroke-width:2px,color:#92400E;
    classDef allow fill:#E6F9F0,stroke:#10B981,stroke-width:2px,color:#065F46;

    class BlockAction,UI_Block block;
    class WarnAction,UI_Warn warn;
    class AllowAction,WebViewLoad allow;
```

### End-to-End Navigation Interception Flow

```mermaid
sequenceDiagram
    autonumber
    actor User
    participant Canvas as UI / Start Canvas
    participant Controller as BrowserController
    participant Gate as Deterministic SecurityGate
    participant Feed as ThreatFeedManager (SQLite)
    participant Engine as Android WebView (Render Process)
    participant Warning as SecurityWarningActivity

    User->>Canvas: Submits URL or Taps Link
    Canvas->>Controller: loadUrl(rawUrl)
    Controller->>Gate: verify(rawUrl)
    Gate->>Gate: UrlCanonicalizer (Punycode, Port, Hex Decoded)
    Gate->>Feed: Query Local Threat Snapshots
    
    alt Known Malware (URLHAUS Match)
        Feed-->>Gate: Threat Match [MALWARE]
        Gate-->>Controller: SecurityDecision.BLOCK
        Controller->>Warning: Launch Interstitial (Bypass Locked)
        Warning-->>User: Intercepted Host + Malware Proof
    else Typosquat / Homoglyph / Shannon Entropy Anomaly
        Gate-->>Controller: SecurityDecision.WARN
        Controller->>Warning: Launch Interstitial (Explainable Override Allowed)
        Warning-->>User: Risk Breakdown + "Proceed to Site" Button
        opt User Chooses Override
            User->>Warning: Tap "Proceed to Site"
            Warning->>Controller: loadUrlConfirmed(url)
            Controller->>Engine: webView.loadUrl(url)
        end
    else Verified Clean
        Gate-->>Controller: SecurityDecision.ALLOW
        Controller->>Engine: webView.loadUrl(canonicalUrl)
        Engine-->>User: Render Clean Web Page
    end
```

### Canonicalization Subroutines

1. **Punycode / IDN Normalization:** Converts internationalized domain names (e.g. Cyrillic `рaypal.com`) to ASCII Compatible Encoding (`xn--aypal-uye.com`).
2. **Recursive Percent-Decoding:** Resolves obfuscated multi-stage encoded payloads (e.g. `%2577%2577%2577.evil.com` -> `www.evil.com`).
3. **Port & Credential Stripping:** Strips embedded basic-auth user credentials (`user:pass@host`) and eliminates default redundant port declarations (`:80` for HTTP, `:443` for HTTPS).

### Offline Heuristics & Homoglyph Math

- **Shannon Entropy:** Detects machine-generated domains (DGA / C2):
  $$H(X) = -\sum_{i=1}^{n} P(x_i) \log_2 P(x_i)$$
- **Levenshtein Distance:** Compares domain labels against a protected brand registry:
  $$\text{dist}(\text{host}, \text{target}) \le 2 \implies \text{High Risk Typosquat}$$

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

Automated file downloads pose significant risk on mobile devices. NovaBrowser isolates dangerous executables into an app-private quarantine sandbox before allowing them into public storage:

```mermaid
flowchart TD
    D_Start([Download Triggered]) --> D_MIME{MIME / Extension Inspection}
    
    D_MIME -->|"Safe MIME: .pdf, .png, .txt"| D_Safe[Write to Public Downloads Folder]
    D_Safe --> D_DB_Safe[(Record Marked COMPLETED in SQLite)]
    
    D_MIME -->|"Executable / Script: .apk, .dex, .sh"| D_Sandbox["Isolate in App-Private Storage:<br/>context.cacheDir/quarantine/"]
    D_Sandbox --> D_DB_Quar[(Record Marked QUARANTINED in SQLite)]
    D_DB_Quar --> D_Prompt[Display Quarantine Warning Dialog]
    
    D_Prompt -->|"User Chooses Delete"| D_Purge[Purge File from Sandbox Storage]
    D_Prompt -->|"Controlled User Override"| D_Release[Move File to Public Downloads]

    classDef alert fill:#FEE2E2,stroke:#EF4444,stroke-width:2px,color:#991B1B;
    classDef safe fill:#E6F9F0,stroke:#10B981,stroke-width:2px,color:#065F46;

    class D_Sandbox,D_DB_Quar,D_Prompt,D_Purge alert;
    class D_Safe,D_DB_Safe,D_Release safe;
```

---

## Network Ad & Tracker Blocking Engine

NovaBrowser integrates a high-throughput, offline network blocking engine decoupled from the navigation security gate:

### Subresource Interception & CDN Protection
- **Decoupled Architecture:** Subresource requests (scripts, images, beacons, iframes) in `TabManager.onSubresourceCheck` bypass the heavy canonicalization/heuristic pipeline and evaluate directly against `AdBlockEngine.isAdOrTracker()`.
- **$O(\text{labels})$ Fast-Path Hash Index:** 9,578 domain rules (`blocklist_domains.txt`) loaded into an in-memory `HashSet<String>`. Checks require at most 2–4 hash lookups per subresource request.
- **Media CDN Safe-Guard:** Essential streaming CDN hosts (such as `googlevideo.com`) are explicitly preserved so video and audio playback remain unbroken while tracking pixels and banner networks are blocked.
- **Zero-Cloud Leak Telemetry:** All evaluation is 100% on-device. Counters (`blockedAdsCount`, lifetime totals) are maintained locally in memory and `SharedPreferences`.

### Batched Cosmetic Element Hiding
- **Dynamic CSS Injection:** Injects `<style id="nova-adblock-cosmetic">` during `onPageFinished` with 50+ curated display/visibility suppressing selectors:
  ```css
  .ad-banner, .adsbygoogle, [id^="google_ads_"], .sponsored-post { 
      display: none !important; 
      visibility: hidden !important; 
      height: 0 !important; 
  }
  ```
- **Batched Execution:** Partitioned into optimized 150-selector batches to avoid DOM parsing stalls.

### Shield Bottom Sheet & Per-Site Rules
- **Interactive Bottom Sheet (`dialog_adblock_shield.xml`):** Tapping the omnibox shield badge or bottom island shield button surfaces live per-page and lifetime blocked counters, rule telemetry, and instant per-site toggles.
- **SQLite Rule Persistence:** Per-site allowlisting / cosmetic overrides are persisted in SQLite (`adblock_site_rules`), allowing users to toggle protection for sites with strict anti-adblock walls.
- **Broken Site Reporting:** One-tap reporting button logs problematic site layouts to `broken_site_reports` for ruleset curation.

---

## NovaMotion Graphics & Animation Framework

NovaBrowser features a custom motion graphics framework ([NovaMotion.kt](NovaBrowser/app/src/main/java/com/gintama/novabrowser/ui/motion/NovaMotion.kt)) engineered for fluid 60/120 FPS performance:

### Celestial Supernova Brand Identity
- **Vector Core Mark (`ic_nova_logo.xml`):** Deep space obsidian squircle base (`#090D16`), dual neon orbital rings (Neon Cyan `#06B6D4` + Electric Violet `#8B5CF6`), 8-point faceted supernova starcore with central singularity beacon (`#FFFFFF` / `#38BDF8`), and emerald security satellite (`#10B981`).
- **Hero Breathing & Hover Loop:** Start Canvas logo continuously floats in an ambient sine cycle (`translationY: 0 -> -7dp -> 0`) while an ambient radial glow aura (`bg_hero_glow.xml`) pulses gently (`alpha: 0.30 -> 0.85`), bringing the start canvas to life.

### Micro-Interactions & Fluid Transitions
- **Tactile Spring Physics:** Physics-based scale compression on touch (`scale: 0.92x`, 90ms) and bouncy spring overshoot release (`scale: 1.0x`, `OvershootInterpolator(2.8f)`) applied to all bottom island dock buttons, toolbar buttons, and shortcut tiles.
- **Kinetic Shield Pop:** The omnibox shield badge executes an energetic bounce pop (`scale: 1.32x`) whenever an ad or tracker is intercepted on the active page.
- **Cinematic Canvas ↔ WebView Cross-Fade:** Abrupt visibility toggles are replaced with hardware-accelerated cross-fades with Y-translation (`FastOutSlowInInterpolator`).
- **Smooth Progress Bar:** Web page loading is animated with `ObjectAnimator` and `FastOutSlowInInterpolator`, completing with a soft alpha fade-out at 100%.
- **Rolling Numerical Count-Up Ticker:** Lifetime neutralized tracker stats roll up smoothly on canvas appearance (`0 -> 9,578`).

---

## Security Diagnostics & Privacy Controls

NovaBrowser provides direct transparency and controls for user privacy:

- **In-App Security Diagnostics:** Settings includes a built-in test runner evaluating 5 real vectors (Clean URLs, Homoglyph spoofing, Malware hosts, SSL stripping, and AdBlock rules) and surfaces an on-device diagnostic report dialog.
- **Site Permissions Manager:** SQLite-backed permission manager (`site_permissions`) tracking camera, microphone, and geolocation authorizations, with an instant "Revoke All" action.
- **Granular Browsing Data Clearing:** Interactive multi-choice dialog enabling selective erasure of History, Cookies, Cache, and Web Storage.
- **Copy Clean Link:** One-tap action stripping tracking query parameters (`utm_*`, `fbclid`, `gclid`, `igshid`, `msclkid`, `mc_eid`).
- **Desktop Mode Toggle:** Quick User-Agent switching between Chrome Desktop and Mobile UA.
- **Find in Page:** Embedded native search bar connecting to WebView's `findAllAsync` with live match counters (`1/7`) and slide-down/slide-up animations.

---

## Local-First AI & Memory Management

The AI engine in NovaBrowser operates on the principle of **Zero-Cloud Telemetry**. 

### Contextual RAG Retrieval Pipeline

Instead of dumping full DOM structures or history tables into an LLM context window, NovaBrowser employs a targeted RAG pipeline:

```mermaid
sequenceDiagram
    autonumber
    actor User
    participant Bar as Ask Browser Query Bar
    participant Intent as Intent & Entity Parser
    participant FTS as SQLite FTS5 (Lexical Index)
    participant Rank as BM25 Ranker
    participant LLM as Tiny Local LLM (0.5B - 1.5B)
    participant UI as Result Card Showcase

    User->>Bar: Submits: "Where was that Rust memory safety paper?"
    Bar->>Intent: Tokenize & Strip Stopwords
    Intent->>Intent: Extracted: ["rust", "memory", "safety", "paper"]
    Intent->>FTS: MATCH 'rust OR memory OR safety'
    FTS-->>Rank: Return Candidate Rows + Metadata
    Rank->>Rank: Score by BM25 + Recency Weight
    Rank->>LLM: Provide Top-3 Lexical Candidate Snippets
    LLM->>LLM: Format Contextual Summary & Key Highlights
    LLM-->>UI: Render Interactive Card (98% Relevance Gauge)
    UI-->>User: Display Card with Instant One-Tap Navigation
```

### Zero-Cloud Telemetry & Prompt Injection Hardening

Webpage content is treated as untrusted data. When summarizing or querying active pages:
1. **Isolated Payload Blocks:** Webpage text is encapsulated in delimiters to prevent instruction hijacking.
2. **Deterministic Intent Validation:** The local LLM never executes commands directly; it only emits structured JSON intents validated by `BrowserController`.
3. **Hardware Air-Gap:** All inference occurs locally on CPU/NPU without network socket transmission.

---

## Hardware & Device Capability Tiers

To prevent out-of-memory (OOM) faults on diverse Android hardware, memory budgets are strictly enforced:

```mermaid
graph TD
    Boot[Device Boot / RAM Inspection] --> TierCheck{Available System RAM}
    
    TierCheck -->|"RAM <= 2GB"| T_Min["Minimal Tier<br/>Budget: &lt; 50MB<br/>No LLM - Lexical FTS5 Only"]
    TierCheck -->|"RAM 3GB - 4GB"| T_Light["Light Tier<br/>Budget: &lt; 350MB<br/>0.5B - 1.5B Q4_K Model"]
    TierCheck -->|"RAM >= 6GB"| T_Std["Standard Tier<br/>Budget: &lt; 800MB<br/>3.0B Q4_K Model + Vector RAG"]
    
    T_Light --> Lifecycle[Lifecycle Memory Controller]
    T_Std --> Lifecycle
    
    Lifecycle -->|Idle > 120s| Evict[Evict Model from RAM]
    Lifecycle -->|onTrimMemory CRITICAL| Emergency[Immediate Runtime Destruction]

    classDef min fill:#FEF3C7,stroke:#F59E0B,stroke-width:1.5px,color:#92400E;
    classDef light fill:#EFF6FF,stroke:#3B82F6,stroke-width:1.5px,color:#1E40AF;
    classDef std fill:#E6F9F0,stroke:#10B981,stroke-width:1.5px,color:#065F46;

    class T_Min min;
    class T_Light light;
    class T_Std std;
```

### Memory Reclamation Rules
1. **Lazy Loading:** Models are not loaded into memory until explicitly invoked.
2. **Idle Unload:** If no AI request occurs within 120 seconds, the model is evicted from RAM.
3. **Low-Memory Override:** If Android OS triggers `onTrimMemory(TRIM_MEMORY_RUNNING_CRITICAL)`, the model runtime is immediately destroyed.

---

## Data Model & Database ER Diagram

The SQLite database (`nova_browser.db`) implements Write-Ahead Logging (WAL) and foreign-key constraints across seven primary entities:

```mermaid
erDiagram
    HISTORY ||--o{ HISTORY_FTS : "indexes"
    HISTORY {
        int id PK
        string url
        string title
        int visit_time
        int visit_count
        int is_private
    }

    HISTORY_FTS {
        string title
        string url
    }

    BOOKMARKS {
        int id PK
        string url
        string title
        int created_at
    }

    SESSIONS {
        int id PK
        string tab_id
        string url
        string title
        int last_active
    }

    DOWNLOADS {
        int id PK
        string url
        string file_path
        string status
        int timestamp
    }

    SECURITY_RULES {
        int id PK
        string pattern
        string feed_type
        string rule_action
    }

    SNAPSHOT_META {
        int id PK
        string feed_type
        string version
        int rule_count
    }

    ADBLOCK_SITE_RULES {
        string domain PK
        int adblock_enabled
        int cosmetic_enabled
        int updated_at
    }

    BROKEN_SITE_REPORTS {
        int id PK
        string url
        int timestamp
    }

    SITE_PERMISSIONS {
        int id PK
        string origin
        string permission_type
        int granted
        int updated_at
    }
```

*For exact data types, migration indices, and table DDL, consult [DESIGN.md](DESIGN.md).*

---

## Repository Structure

```text
NovaBrowser/
├── app/                                 # Android Application Module
│   ├── src/main/assets/                 # Bundled AdBlock Rules & Cosmetic Selectors
│   │   ├── blocklist_domains.txt        # 9,578 Ad & Tracker Domain Hashes
│   │   └── cosmetic_selectors.txt       # 50+ Cosmetic Element Hiding Selectors
│   ├── src/main/java/com/gintama/novabrowser/
│   │   ├── adblock/                     # AdBlockEngine ($O(labels) fast lookup & CSS generator)
│   │   ├── bookmarks/                   # Bookmarks Activity & List Adapter
│   │   ├── browser/                     # NovaWebView, WebChromeClient, TabManager
│   │   ├── downloads/                   # DownloadHandler & Physical Quarantine Sandbox
│   │   ├── history/                     # History Activity & FTS5 Query UI
│   │   ├── settings/                    # Settings, Diagnostics Runner, Site Permissions
│   │   └── ui/                          # MainActivity, SecurityWarningActivity, TabsAdapter
│   │       └── motion/                  # NovaMotion (60/120 FPS Animation Engine)
│   └── src/main/res/                    # Liquid System Layouts, Anim, Drawables, Styles
│       ├── anim/                        # slide_down_in, slide_up_out, pop_in, fade_in/out
│       └── drawable/                    # ic_nova_logo (Vector), bg_hero_glow, bg_glass_*
│
├── browser-core/                        # Core Domain & Security Module (Pure Logic)
│   ├── src/main/java/com/gintama/novabrowser/core/
│   │   ├── controller/                  # BrowserController (Navigation Orchestration)
│   │   ├── db/                          # NovaDatabaseHelper (SQLite Schema, FTS5 & DAOs)
│   │   ├── model/                       # Immutable Domain Data Models
│   │   ├── navigation/                  # UrlSanitizer & NavigationState
│   │   └── security/                    # Deterministic Security Gate:
│   │       ├── HeuristicsEngine.kt      # Shannon Entropy & Levenshtein Algorithms
│   │       ├── RedirectTracker.kt       # Hop Counter & SSL Downgrade Detection
│   │       ├── SecurityGate.kt          # Deterministic Gate Orchestrator
│   │       ├── ThreatFeedManager.kt     # Threat Snapshots & Matching Engine
│   │       ├── UrlCanonicalizer.kt      # Punycode, Port & Encoding Normalizer
│   │       ├── abp/                     # ABP Filter Rule Parser & Rule Set
│   │       └── SecurityVerificationRunner.kt # Test Contract Verification
│   └── src/test/java/                   # JUnit Unit Tests for Security Gate
│
├── ai/                                  # Local Intelligence Module (Dormant Phase 3)
│   └── src/main/java/com/gintama/novabrowser/ai/
│       ├── AiEngine.kt                  # Model Ingestion & RAG Orchestration Stub
│       └── DeviceTier.kt                # Hardware RAM Inspection & Tiering Rules
│
├── docs/                                # Media Assets & Screen Showcases
│   └── assets/screens/                  # Liquid System Approved Screen Mockups
│
├── ARCHITECTURE.md                      # Comprehensive System & Layer Architecture
├── DESIGN.md                            # UI/UX Tokens, Schemas & Component Specifications
├── PLAN.txt                             # Phased Engineering Roadmap
├── PRD.md                               # Product Requirements & Acceptance Criteria
├── SECURITY.md                          # Threat Models, STRIDE Analysis & Attack Surface
├── gradlew.bat                          # Gradle Build Tool
├── run_tests.ps1                        # CLI Test Verification Runner
└── README.md                            # Primary Project Documentation
```

---

## Tech Stack

### Active Core
- **Language:** Kotlin 2.0.21
- **Platform:** Android 15 (compileSdk 35, minSdk 24, targetSdk 35)
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
cmd.exe /c "set JAVA_HOME=C:\Program Files\Microsoft\jdk-17.0.20.8-hotspot&& gradlew.bat assembleDebug --offline"
```

*The generated APK is located at:*  
`NovaBrowser/app/build/outputs/apk/debug/app-debug.apk` (~7.0 MB).

### Installing to Device

```powershell
# Using ADB
& "C:\Android\android-sdk\platform-tools\adb.exe" install -r "NovaBrowser\app\build\outputs\apk\debug\app-debug.apk"
```

---

## Test Suite & Verification

The security subroutines and adblock filters across `:browser-core`, `:app`, and `:ai` are verified through automated test runners:

```powershell
# Run full test suite across all modules
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-17.0.20.8-hotspot"
cd NovaBrowser
.\gradlew.bat test

# Or run standalone CLI verification runner
powershell -ExecutionPolicy Bypass -File .\run_tests.ps1
```

### Verified Test Assertions
- **Punycode Spoofing:** `https://рaypal.com` (Cyrillic `р`) canonicalizes to `xn--aypal-uye.com`.
- **Recursive Percent-Decoding:** `http://%2577%2577%2577.evil.com` resolves to `http://www.evil.com`.
- **High Shannon Entropy:** Random hex/alphanumeric domains trigger elevated risk scores.
- **Brand Levenshtein Proximity:** `https://paypa1.com` triggers `WARN` (Homoglyph spoof).
- **SSL Stripping:** Downgrades from `https://bank.com` to `http://bank.com` are intercepted.
- **Redirect Loops:** Chains exceeding 4 hops are terminated.
- **Feed Authority Separation:** `URLHAUS` triggers hard `BLOCK`; `EASYLIST` filters silently; `LOCAL_HEURISTIC` emits `WARN`.
- **Axiom Check:** Unlisted domains emit `RiskState.UNKNOWN`, never `KNOWN_SAFE`.
- **AdBlock Fast-Path Check:** Subresources evaluate in $O(\text{labels})$ hash lookups; CDN media streams are preserved.
- **In-App Self-Diagnostics:** 5 live vectors validated via `SettingsActivity` diagnostic runner.

---

## Target Performance Metrics

The following metrics are design targets defined in [PRD.md](PRD.md):

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
- **App-Private Sandboxed Storage:** User profiles and FTS5 search databases reside strictly in app-private sandbox storage (`/data/data/com.gintama.novabrowser/databases/`), protected by Android OS application sandboxing with backup disabled (`allowBackup="false"`).
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

- **Project License:** Apache License 2.0 (see [LICENSE](LICENSE)).
- **Threat Data Attributions:**
  - [URLhaus](https://urlhaus.abuse.ch/) by Abuse.ch (Malware URL data).
  - [EasyList & EasyPrivacy](https://easylist.to/) (Ad and tracker blocking rules).

---

## Companion Specifications

For detailed architectural contracts and design references:
- **[PRD.md](PRD.md)** — Core product requirements, user personas, and acceptance criteria.
- **[ARCHITECTURE.md](ARCHITECTURE.md)** — Architectural invariants, thread models, and layer isolation.
- **[DESIGN.md](DESIGN.md)** — Liquid System UI tokens, typography, and complete SQLite database schema.
- **[SECURITY.md](SECURITY.md)** — Threat models, STRIDE analysis, homoglyph algorithms, and attack surface review.
- **[PLAN.txt](PLAN.txt)** — Phased engineering implementation roadmap.
