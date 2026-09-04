# NovaBrowser — Product Requirements Document (PRD)

**Version:** 1.0
**Status:** Draft for build
**Owner:** Sonu / Team Gintama

---

## 1. Vision

NovaBrowser is a **security-first, local-first AI browser** for Android and desktop. It is not "Chrome with a chatbot bolted on." It is a browser where a deterministic security core protects navigation by default, and a small on-device AI layer makes the browser feel intelligent — without depending on cloud AI, without bloating RAM/storage, and without ever being the thing that decides whether a page is safe to open.

**One-line pitch:** *A browser that keeps you safe by default and remembers your browsing for you — entirely on your device.*

---

## 2. Problem Statement

- Mainstream browsers are either "dumb and safe" (no real assistant) or "AI-heavy and privacy-leaky" (cloud LLM sees your browsing).
- Users can't search their own history/tabs in natural language ("that GitHub page I saw yesterday about Android security").
- AI browser add-ons commonly treat the LLM as a semi-trusted decision-maker (e.g., asking an LLM "is this link safe?"), which is an unreliable and unauditable security model.
- Most "AI browsers" assume flagship hardware. Millions of Android users are on 2–4GB RAM devices where a bundled LLM either doesn't run or kills the rest of the phone's performance.

## 3. Goals

1. Ship a real, usable browser (tabs, history, bookmarks, downloads) — not a wrapper/demo.
2. Deterministic security gate on every navigation, download, and redirect — auditable, explainable, never bypassed by AI.
3. Local-first AI: history search, page Q&A, summarization — fully offline-capable.
4. Run acceptably on **low-memory Android devices** (2GB RAM class) via tiered AI and a strict "no bloat" resource budget.
5. Cross-platform core (Android now, desktop later) sharing security + AI + data logic.
6. Ship something judge-able / demo-able for hackathon evaluation without overpromising security guarantees we can't back.

## 4. Non-Goals (v1)

- Not building a custom rendering engine (use WebView / Chromium).
- Not claiming "zero malicious links can ever open" — explicitly false and we will not market it that way.
- Not shipping cloud AI as a requirement — cloud is optional, opt-in, off by default.
- Not building full cross-device sync in v1 (planned Phase 6).
- Not competing on general-purpose chat — the LLM's job is narrow (intent parsing, retrieval formatting, summarization), not open-ended conversation.

## 5. Target Users

| Persona | Need |
|---|---|
| Budget/low-end Android user | Fast, safe browsing that doesn't lag their phone |
| Privacy-conscious user | Browsing/AI data that never leaves the device by default |
| Power user / researcher | "Find that page I read last week" without manually digging through history |
| Hackathon judges / evaluators | A working, demo-able differentiator, not just "ChatGPT in an iframe" |

## 6. Core Features

### MVP (must-have for demo/production floor)
- [ ] Tabs, address bar, back/forward/reload
- [ ] Bookmarks, history, downloads
- [ ] Private browsing mode
- [ ] Navigation Security Gate (blocklist + heuristics + redirect policy) on **every** navigation
- [ ] Explainable block/warning screen (reason, not just "blocked")
- [ ] Local blocklist (URLhaus + EasyList/EasyPrivacy snapshot), offline-capable
- [ ] Local tiny-LLM "Ask Browser" bar: natural-language history search
- [ ] "Ask this page" — summarize / Q&A on current page via retrieval, not full-page dump
- [ ] Low-memory mode: auto-detects device RAM and adjusts AI tier or disables local LLM gracefully

### v1.x (near-term after MVP)
- [ ] Download protection (quarantine risky files)
- [ ] Redirect chain detection UI
- [ ] Bookmark/page semantic search (embeddings)
- [ ] Structured browser tool-calling (open_url, switch_tab, search_bookmarks, etc.)
- [ ] Desktop (Electron) port sharing the same security/AI core

### Later (Phase 6+)
- [ ] Encrypted opt-in cross-device sync
- [ ] Smart tab grouping
- [ ] Research mode / multi-page analysis
- [ ] Browser agent (multi-step tool use, still gated by Security Core)

## 7. Key Product Decisions (locked)

| Decision | Rationale |
|---|---|
| AI is never the final security authority | Deterministic, auditable code decides allow/block. AI only explains/searches/summarizes. See `SECURITY.md`. |
| Use existing rendering engine (WebView/Chromium) | Building an engine from scratch is out of scope and unnecessary for v1. |
| Local LLM is tiny (≤3B) and tiered by device RAM | Prevents bloat; see `ARCHITECTURE.md` §Low-Memory Strategy. |
| Retrieval + tool-calling over "dump everything into the model" | Keeps context small, keeps tiny models accurate, keeps latency low. |
| "Unknown" ≠ "Safe" | Never state a page is safe just because nothing matched a blocklist. |
| Cloud AI optional, off by default | Core privacy promise of the product. |

## 8. Success Metrics

- Cold start time on a 2GB RAM device: target **< 3s** to interactive.
- Peak RAM usage with AI idle: target **< 250MB** above baseline WebView browser.
- Peak RAM usage with local LLM active (0.5B tier): target **< 600MB** total app footprint.
- Security Gate decision latency: **< 50ms** (must not visibly delay navigation).
- History search: correct top-1 result for natural-language query in demo test set: **> 80%**.
- App install size (excluding downloaded models): **< 40MB** APK.

## 9. Constraints

- Must remain functional (browser + security) with local AI **completely disabled**.
- Must not require network access for core browsing security decisions once the local snapshot is downloaded.
- Must not bundle a full Chromium binary on Android (WebView only — Chromium bundling is a desktop-only, Electron-specific cost).
- Third-party threat feed licensing (EasyList/EasyPrivacy/URLhaus) must be reviewed before any commercial distribution — do not assume redistribution rights.

## 10. Risks & Assumptions

| Risk | Mitigation |
|---|---|
| Local LLM too slow/heavy on low-end devices | Tiered model strategy + hard fallback to non-AI lexical search (see ARCHITECTURE.md) |
| WebView navigation callbacks don't cover 100% of threat surface | Layer with resource interception; document known gaps, don't overclaim |
| Threat feed staleness while offline | Last-downloaded snapshot + clear "last updated" timestamp shown to user |
| Scope creep beyond hackathon timeline | MVP scope frozen in this PRD §6; anything else goes to backlog in `PLAN.txt` |
| JS bridge misuse exposing native APIs to untrusted web content | Hard boundary — no privileged API exposed to WebView JS; see SECURITY.md |

## 11. Out of Scope for This Document

Implementation details live in `ARCHITECTURE.md`, `DESIGN.md`, and `SECURITY.md`. Timeline/tasks live in `PLAN.txt`.
