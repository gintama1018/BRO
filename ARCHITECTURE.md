# NovaBrowser — System Architecture

**Version:** 1.2 (Synchronized with Codebase)  
**Status:** Active Implementation  
**Companion Docs:** [PRD.md](PRD.md) (Product Requirements) / [PLAN.txt](PLAN.txt) (Roadmap) / [DESIGN.md](DESIGN.md) (Data & UI Contracts) / [SECURITY.md](SECURITY.md) (Threat Model)

---

## 1. Design Philosophy

> *Do not ask:* "How do we put a giant cloud AI inside a browser?"  
> *Ask:* "How little local compute do we need to make the browser feel fast, intelligent, and secure?"

NovaBrowser enforces a strict, auditable separation of responsibilities:

```
Browser UI & Shell   -> Render tabs, omnibox, shields, and Nova QuadView
Browser Manager      -> Tab lifecycle, session ownership, view reparenting
Deterministic Gate   -> 100% auditable allow/block/warn navigation decisions
Ad & Shield Engine   -> Fast-path subresource filtering + cosmetic CSS hiding
WebView Runtime      -> Sandboxed web execution via Android system WebView
Local Database       -> SQLite persistence (12 tables), lexical FTS5 BM25 search
AI Intelligence      -> Hardware RAM tiering (implemented); tiny-LLM inference (in progress)
Browser Controller   -> Validated execution of user and system navigation intents
```

### The Non-Negotiable Invariant
**AI is never the security authority.**

```
UNAUDITABLE / UNSAFE:  URL -> LLM -> "looks safe to me" -> Open
DETERMINISTIC & SAFE:  URL -> DeterministicSecurityGate -> ALLOW / WARN / BLOCK
```

No probabilistic language model is permitted in the navigation allow/block pipeline. All security gating is deterministic, testable, and offline-functional.

---

## 2. High-Level Layered Architecture

```
+-------------------------------------------------------------------------+
|                              BROWSER UI LAYER                           |
|  - MainActivity & Navigation Stack       - Omnibox & Search Suggestions |
|  - Nova QuadView (2x2 Multi-Tab Grid)   - Site Shields Interstitial    |
|  - Security Warning Screen (Explainable) - Reader Mode & Offline Pages  |
+------------------------------------+------------------------------------+
                                     |
                          Navigation / Tab Intent
                                     v
+-------------------------------------------------------------------------+
|                     BROWSER & SESSION MANAGEMENT                        |
|  - TabManager (Sole source of truth for BrowserTab lifecycle)          |
|  - BrowserController (Coordinates security, database, and visits)       |
|  - ExternalSchemeHandler (Guarded intents: tel, mailto, market, etc.)   |
+------------------------------------+------------------------------------+
                                     |
                           Candidate Navigation
                                     v
+-------------------------------------------------------------------------+
|                      DETERMINISTIC SECURITY GATE                        |
|  1. UrlCanonicalizer (Punycode xn--, recursive percent-decoding, ports) |
|  2. ThreatFeedManager (URLhaus malware dataset fast binary match)       |
|  3. HeuristicsEngine (Shannon entropy for DGA, Levenshtein typosquats)  |
|  4. RedirectTracker (Max 4 hops, SSL downgrade / stripping detection)   |
+------------------+-----------------------------------+------------------+
                   |                                   |
              [ BLOCK / WARN ]                      [ ALLOW ]
                   |                                   |
                   v                                   v
        SecurityWarningActivity             +----------------------+
        (Explainable Interstitial)          |  AD & TRACKER SHIELD |
                                            |  - AdBlockEngine     |
                                            |  - CosmeticEngine    |
                                            |  - SiteShieldManager |
                                            +----------+-----------+
                                                       |
                                              Interception Filter
                                                       v
+-------------------------------------------------------------------------+
|                            WEB ENGINE RUNTIME                           |
|  - NovaWebView (Hardened AndroidX WebKit wrapper)                       |
|  - NovaWebClient (shouldOverrideUrlLoading, shouldInterceptRequest)    |
|  - NovaChromeClient (progress, title, fullscreen, permissions)          |
|  - Hardened JS Boundary (Zero privileged native APIs exposed)           |
+------------------------------------+------------------------------------+
                                     |
                          Page Events & Persistence
                                     v
+-------------------------------------------------------------------------+
|                          LOCAL STORAGE & RETRIEVAL                      |
|  - NovaDatabaseHelper (SQLite v2: 12 tables + FTS5 full-text search)    |
|  - History FTS5 (BM25 token ranking with graceful LIKE fallback)        |
|  - Bookmarks & HTML import/export (BookmarkHtmlManager)                 |
|  - Download Quarantine (.nova_quarantine/ with SHA-256 integrity)       |
|  - Private Browsing Isolation (Zero disk persistence, biometric lock)   |
+------------------------------------+------------------------------------+
                                     |
                          Parallel Analysis Path
                                     v
+-------------------------------------------------------------------------+
|                         LOCAL AI SUBSYSTEM                              |
|  - DeviceTierDetector (RAM-based: Minimal <=2GB, Light 3-4GB, Std 6GB+) |
|  - AiEngine Contract (Dormant during Phase 1 & 2 to prevent RAM bloat)  |
|  - Native Inference Runtime (llama.cpp / quantized GGUF - Planned/Roadmap)|
|  - Contextual Page Summarization & Local Embeddings (Planned/Roadmap)   |
+-------------------------------------------------------------------------+
```

---

## 3. Project Module Structure

The project is structured into three clean Gradle modules:

```
NovaBrowser/
├── app/                  # Android Application Shell
│   ├── adblock/          # AdBlockEngine, CosmeticEngine
│   ├── backup/           # NovaBackupManager
│   ├── bookmarks/        # BookmarksActivity, BookmarkHtmlManager
│   ├── browser/          # NovaWebView, TabManager, WebDarkThemeManager, PwaShortcutManager
│   ├── diagnostics/      # NovaDiagnostics (In-app self-check runner)
│   ├── downloads/        # DownloadHandler, NovaDownloadEngine, MediaSnifferEngine
│   ├── history/          # HistoryActivity, HistoryAdapter
│   ├── media/            # TabMuteEngine
│   ├── notifications/    # NovaNotificationHelper
│   ├── offline/          # OfflinePageManager (MHTML archives)
│   ├── reader/           # ReaderActivity, ReaderExtractor, ReaderPreferences
│   ├── search/           # SearchEngineManager
│   ├── security/         # NovaBiometricHelper
│   ├── settings/         # SettingsActivity
│   ├── shields/          # SiteShieldManager, SiteShieldSettings
│   └── ui/               # MainActivity, SecurityWarningActivity, TabsAdapter
│       ├── motion/       # NovaMotion
│       ├── omnibox/      # OmniboxSuggestionsAdapter
│       └── quad/         # QuadViewManager, QuadPaneView, QuadSlot, QuadViewState
│
├── browser-core/         # Platform-Agnostic Core Engine
│   ├── controller/       # BrowserController
│   ├── db/               # NovaDatabaseHelper (12 SQLite tables, FTS5 migrations)
│   ├── model/            # TabSession, HistoryItem, BookmarkItem, DownloadItem
│   ├── navigation/       # UrlSanitizer, SearchEngine
│   └── security/         # DeterministicSecurityGate, UrlCanonicalizer, HeuristicsEngine,
│                         # RedirectTracker, ThreatFeedManager, AdblockParser
│
└── ai/                   # Local Intelligence Subsystem
    ├── DeviceTier.kt     # RAM inspection (MINIMAL, LIGHT, STANDARD)
    └── AiEngine.kt       # Tier contract and lifecycle management
```

---

## 4. Nova QuadView Presentation Architecture

Nova QuadView is a major spatial browsing innovation built on the architectural principle:  
**"Same browser sessions. New spatial arrangement."**

```
┌────────────────────────────────────────────────────────┐
│               Nova QuadView Workspace                  │
│                                                        │
│   ┌────────────────────────┬────────────────────────┐  │
│   │ Slot A [ACTIVE]        │ Slot B                 │  │
│   │ Tab: "GitHub Repo"     │ Tab: "Android Docs"    │  │
│   │ [Live NovaWebView #1]  │ [Live NovaWebView #2]  │  │
│   ├────────────────────────┼────────────────────────┤  │
│   │ Slot C                 │ Slot D                 │  │
│   │ Tab: "StackOverflow"   │ Tab: "Terminal Logs"   │  │
│   │ [Live NovaWebView #3]  │ [Live NovaWebView #4]  │  │
│   └────────────────────────┴────────────────────────┘  │
└────────────────────────────────────────────────────────┘
```

### 4.1 Invariant: Layout Change $\neq$ Session Recreation
QuadView is strictly a **presentation layer**, not a separate browser engine.  
- `TabManager` remains the **sole source of truth** for all `BrowserTab` objects and their underlying `NovaWebView` instances.
- When entering QuadView, switching to Focus Mode, swapping panes, or returning to single-tab view, the active `NovaWebView` instances are **reparented** in the Android View hierarchy:
  ```kotlin
  // Detach from previous container
  (webView.parent as? ViewGroup)?.removeView(webView)
  // Attach into target QuadPaneView container
  paneContainer.addView(webView, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
  ```
- **Zero WebViews are destroyed, recreated, or reloaded.** JavaScript execution, scroll offsets, form inputs, audio playback, and DOM state remain 100% continuous.

### 4.2 Slot State vs Tab State Separation
- `QuadViewState` tracks slots via tab identifiers: `QuadSlot -> tabId` (e.g., `slotATabId = "tab_101"`).
- Tab resolution always routes through `tabManager.getTabById(id)`.
- If a tab is closed from within a QuadView pane, its slot transitions to `EMPTY`. Tapping an empty slot triggers `onPaneEmptySlotListener` presenting an assignment dialog to pick an existing tab or open a new one.

### 4.3 Responsive Spatial Layout
- **Portrait Orientation:** Two horizontal rows (`rowTopQuad` and `rowBottomQuad`), each containing two slots side-by-side.
- **Landscape Orientation:** Two vertical columns, each with two vertically stacked slots, preserving widescreen aspect ratios.
- **Focus Mode:** Expands any selected slot to dominant 100% screen weight while maintaining live background sessions in dormant slots, ready for instant restoration to 2×2 grid.

### 4.4 Privacy Boundary in QuadView
- QuadView workspaces **strictly enforce privacy segregation**.
- If QuadView is launched from a private tab, only private tabs can be assigned to slots B, C, and D. Standard tabs cannot be added to a private QuadView workspace, and vice-versa.

---

## 5. Deterministic Security Core

All navigation evaluation executes synchronously or on the `Dispatchers.IO` coroutine context prior to loading:

```
Raw Input
    │
    ▼
UrlSanitizer.sanitizeInput() ──────────► Search query or valid URL
    │
    ▼
DeterministicSecurityGate.evaluate()
    │
    ├── 1. UrlCanonicalizer.canonicalize()
    │      - Punycode IDN conversion (e.g. Cyrillic 'р' -> 'xn--aypal-uye')
    │      - Recursive percent-decoding (%2577 -> %77 -> w)
    │      - Port & schema normalization
    │
    ├── 2. ThreatFeedManager.check()
    │      - URLhaus malware domain dataset lookup
    │      - If matched: Emits RiskState.BLOCKED (Severity: BLOCK)
    │
    ├── 3. HeuristicsEngine.evaluate()
    │      - Shannon entropy check on domain labels (DGA detection)
    │      - Levenshtein distance check against protected brand list (homoglyphs)
    │      - If threshold exceeded: Emits RiskState.SUSPICIOUS (Severity: WARN)
    │
    ├── 4. RedirectTracker.evaluate()
    │      - Redirect hops tracked (max 4 allowed)
    │      - Protocol downgrade (HTTPS -> HTTP) detection
    │      - If loop or downgrade: Emits RiskState.SUSPICIOUS
    │
    └── 5. Axiom Evaluation:
           - If unlisted: Emits RiskState.UNKNOWN (Never false claim of KNOWN_SAFE)
```

---

## 6. Ad, Tracker & Shield Architecture

Subresource filtering operates directly inside `NovaWebClient.shouldInterceptRequest()`:

```
Subresource Request (Script, Image, IFrame, XHR)
    │
    ▼
SiteShieldManager.getSettings(documentHost)
    │
    ├── If shields disabled for site ────────► ALLOW request
    │
    ▼
AdBlockEngine.shouldBlock(requestUrl, documentHost)
    │
    ├── Fast-path domain label tree matching (O(labels) lookup)
    ├── Checks against EasyList & EasyPrivacy rules
    │
    ├── MATCHED ─────────────────────────────► BlockedWebResourceResponse (empty stream)
    │                                          Increment blockedTrackerCount
    ▼
ALLOW request (load from network / cache)
```

### Cosmetic Filtering
When `onPageFinished` fires, `CosmeticEngine` matches the document domain against embedded CSS selectors and injects an inline style block:
```javascript
(function() {
    const style = document.createElement('style');
    style.id = 'nova-cosmetic-shield';
    style.textContent = 'selector1, selector2 { display: none !important; }';
    document.head.appendChild(style);
})();
```

### Known Technical Boundary
In-stream video advertisements delivered via server-side ad insertion (SSAI) from the same CDN host as legitimate content (e.g. YouTube CDN media chunks) cannot be filtered using domain-level blocking without breaking core media playback. This limitation is acknowledged by design.

---

## 7. Download Quarantine Architecture

```
Incoming Download Request
    │
    ▼
MIME & File Extension Classification
    │
    ├── Safe (Plain text, standard images, audio/video) ──► Save directly to Downloads/
    │
    ▼ Risky (.apk, .dex, .sh, .exe, .js, .bat)
Stream to App-Private Sandbox: .nova_quarantine/{UUID}.quarantine
    │
    ├── Calculate SHA-256 integrity hash on the fly
    ├── Insert record in downloads table (status = 'quarantined')
    │
    ▼
Present Interstitial Dialog to User
    - Display filename, size, threat reason, and SHA-256 fingerprint
    │
    ├── User selects [DELETE] ──► Wipe sandbox file, mark 'blocked'
    └── User confirms [PROCEED] ─► Sanitize filename, move to public Downloads/
```

---

## 8. Database Architecture

Storage is centralized in `NovaDatabaseHelper` (`nova_browser.db`, SQLite version 2), featuring 12 tables:

1. **`history`**: Visited URLs, titles, domains, timestamps, summaries, and nullable embedding BLOBs.
2. **`history_fts`**: FTS5 virtual table for lexical full-text token search (`"query"*` prefix matching) ranked via `bm25(history_fts) ASC`.
3. **`bookmarks`**: User bookmarks with optional folder hierarchy.
4. **`sessions`**: Tab persistence records across app restarts.
5. **`downloads`**: Download history, quarantine states (`pending`, `safe`, `quarantined`, `blocked`, `completed`), and threat reasons.
6. **`security_rules`**: Cached security patterns with severity and source tags.
7. **`snapshot_meta`**: Threat feed version metadata, rule counts, and update timestamps.
8. **`ai_page_index`**: Chunked text and vector embeddings for local RAG.
9. **`adblock_site_rules`**: Domain-specific adblock and cosmetic toggles.
10. **`broken_site_reports`**: User-reported compatibility issues.
11. **`site_permissions`**: Domain-level Android permissions (camera, microphone, location).
12. **`site_shields_settings`**: Granular per-domain shield preferences (shields, adblock, cosmetic, JavaScript, cookies).

---

## 9. Local AI & Low-Memory Strategy

NovaBrowser's AI subsystem (`:ai`) is designed for extreme memory discipline:

### 9.1 Hardware Device Tiering
Evaluated at runtime via `DeviceTierDetector`:

| Tier | RAM Threshold | Capabilities & Behavioral Strategy |
|---|---|---|
| **`MINIMAL`** | $\le 2.2\text{ GB}$ | **No LLM loaded.** 100% lexical search via SQLite FTS5. Zero AI memory overhead. |
| **`LIGHT`** | $2.2\text{ GB} - 5.6\text{ GB}$ | On-demand 0.5B–1.5B quantized GGUF model via `llama.cpp`. Unloaded after 2-minute idle timeout. |
| **`STANDARD`** | $\ge 5.6\text{ GB}$ | Up to 3B quantized GGUF model + local embeddings for semantic history and page retrieval. |

### 9.2 Current Status vs Roadmap
- **Implemented Today:** Device tier detection (`DeviceTierDetector.kt`) and AI engine contract stub (`AiEngine.kt`).
- **Phase 3 Roadmap:** `llama.cpp` Android NDK build, quantized GGUF model execution, contextual page summarization, and local embeddings.

---

## 10. Verification & Build Architecture

- **Toolchain:** Java 17 (`hotspot`), Android SDK 35 (compile & target), Min SDK 24.
- **Automated Test Suites:**
  - `:browser-core:testDebugUnitTest`: Tests URL canonicalization, homoglyphs, entropy, redirect loops, adblock parsing, database migrations.
  - `:app:testDebugUnitTest`: Tests QuadView state, tab lifecycle, download quarantine, biometrics, shields, reader mode, dark theme.
  - `:ai:testDebugUnitTest`: Tests device tier detection.
- **Verification Command:**
  ```powershell
  cmd.exe /c "set JAVA_HOME=C:\Program Files\Microsoft\jdk-17.0.20.8-hotspot&& gradlew.bat test --offline"
  ```
  *Result: 121 actionable Gradle tasks pass with zero regressions.*
