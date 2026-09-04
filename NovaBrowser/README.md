# NovaBrowser (Android)

> **Security-first, local-first browser for Android.**  
> *A browser that keeps you safe by default and remembers your browsing for you — entirely on your device.*

---

## Architecture Overview

```text
NovaBrowser/
├── app/                  # Android UI (Tabs, Address Bar, WebView, History, Bookmarks, Settings)
├── browser-core/         # Deterministic Security Gate, SQLite DB, Navigation, Controller
├── ai/                   # Tiered Local AI Engine (llama.cpp / GGUF stubbed for Phase 3)
├── docs/                 # PRD.md, ARCHITECTURE.md, DESIGN.md, SECURITY.md, PLAN.txt
├── .gitignore
└── README.md
```

## Current Milestone: Phase 1 (Core Browser MVP)

- **WebView Engine:** Android system-provided WebView (zero Chromium bloat).
- **Navigation:** Canonical URL input, back/forward/reload, progress tracking.
- **Tab Management:** Multi-tab session handling (create, switch, close, state restoration).
- **Storage (SQLite):**
  - Full schema matching `DESIGN.md` (`history`, `bookmarks`, `sessions`, `downloads`, `security_rules`, `snapshot_meta`).
  - FTS5 full-text lexical search for history.
- **Private Browsing Mode:** Ephemeral session without history, cache, or cookie persistence.
- **Settings:** Low-Memory Mode toggle stub (first-class citizen for 2GB RAM devices).

---

## Build Requirements

- **JDK:** 17+
- **Android SDK:** API 34+ (`compileSdk = 34`, `minSdk = 24`, `targetSdk = 34`)
- **Build System:** Gradle with Kotlin DSL (`build.gradle.kts`)
