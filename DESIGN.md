# NovaBrowser — Design Document (Data + UI + Tool Contracts)

**Version:** 1.2 (Synchronized with Codebase)  
**Status:** Active Implementation  
**Companion Docs:** [PRD.md](PRD.md) / [PLAN.txt](PLAN.txt) / [ARCHITECTURE.md](ARCHITECTURE.md) / [SECURITY.md](SECURITY.md)

---

## 1. Concrete SQLite Database Schema (`nova_browser.db` v2)

NovaBrowser persists all local data in a single, high-performance SQLite database (`NovaDatabaseHelper.kt`) version 2. The schema contains 12 tables structured with strict foreign keys, indices, and constraints.

### 1.1 Core Browser Tables

```sql
-- 1. History Table
CREATE TABLE IF NOT EXISTS history (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    url TEXT NOT NULL,
    title TEXT,
    domain TEXT NOT NULL,
    visited_at INTEGER NOT NULL,
    summary TEXT,
    embedding BLOB,
    extracted_text_meta TEXT
);
CREATE INDEX IF NOT EXISTS idx_history_domain ON history(domain);
CREATE INDEX IF NOT EXISTS idx_history_visited_at ON history(visited_at);

-- 2. History FTS5 Full-Text Virtual Table (BM25 token search)
CREATE VIRTUAL TABLE IF NOT EXISTS history_fts USING fts5(
    title, url, summary, content='history', content_rowid='id'
);

-- 3. Bookmarks Table
CREATE TABLE IF NOT EXISTS bookmarks (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    url TEXT NOT NULL,
    title TEXT,
    folder TEXT,
    created_at INTEGER NOT NULL
);

-- 4. Sessions (Tab Persistence) Table
CREATE TABLE IF NOT EXISTS sessions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    tab_id TEXT NOT NULL,
    url TEXT,
    title TEXT,
    is_private INTEGER DEFAULT 0,
    last_active_at INTEGER
);

-- 5. Downloads Table
CREATE TABLE IF NOT EXISTS downloads (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    url TEXT NOT NULL,
    filename TEXT,
    mime_type TEXT,
    status TEXT CHECK(status IN ('pending','safe','quarantined','blocked','completed')),
    risk_reason TEXT,
    created_at INTEGER NOT NULL
);
```

### 1.2 Security & Threat Feed Tables

```sql
-- 6. Security Rules Table
CREATE TABLE IF NOT EXISTS security_rules (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    rule_type TEXT CHECK(rule_type IN ('domain','url','tracker','malware')),
    pattern TEXT NOT NULL,
    source TEXT CHECK(source IN ('URLHAUS','EASYLIST','EASYPRIVACY','LOCAL_HEURISTIC')),
    severity TEXT CHECK(severity IN ('block','warn','info')),
    updated_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_security_pattern ON security_rules(pattern);

-- 7. Threat Snapshot Metadata Table
CREATE TABLE IF NOT EXISTS snapshot_meta (
    feed_source TEXT PRIMARY KEY,
    last_updated_at INTEGER NOT NULL,
    rule_count INTEGER
);
```

### 1.3 Shields, AdBlock & Permission Tables

```sql
-- 8. AdBlock Site Rules Table (Per-site exceptions)
CREATE TABLE IF NOT EXISTS adblock_site_rules (
    domain TEXT PRIMARY KEY,
    adblock_enabled INTEGER NOT NULL DEFAULT 1,
    cosmetic_enabled INTEGER NOT NULL DEFAULT 1,
    updated_at INTEGER NOT NULL
);

-- 9. Site Shields Settings Table (Brave-style granular per-site controls)
CREATE TABLE IF NOT EXISTS site_shields_settings (
    domain TEXT PRIMARY KEY,
    shields_enabled INTEGER NOT NULL DEFAULT 1,
    adblock_enabled INTEGER NOT NULL DEFAULT 1,
    cosmetic_enabled INTEGER NOT NULL DEFAULT 1,
    javascript_enabled INTEGER NOT NULL DEFAULT 1,
    block_third_party_cookies INTEGER NOT NULL DEFAULT 1,
    updated_at INTEGER NOT NULL
);

-- 10. Site Permissions Table
CREATE TABLE IF NOT EXISTS site_permissions (
    domain TEXT NOT NULL,
    permission TEXT NOT NULL,
    granted INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    PRIMARY KEY (domain, permission)
);

-- 11. Broken Site Reports Table
CREATE TABLE IF NOT EXISTS broken_site_reports (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    url TEXT NOT NULL,
    domain TEXT NOT NULL,
    reported_at INTEGER NOT NULL
);
```

### 1.4 AI Retrieval Index Table

```sql
-- 12. AI Page Index Table (Nullable embeddings for low-memory tiers)
CREATE TABLE IF NOT EXISTS ai_page_index (
    history_id INTEGER NOT NULL REFERENCES history(id) ON DELETE CASCADE,
    chunk_index INTEGER NOT NULL,
    chunk_text TEXT NOT NULL,
    chunk_embedding BLOB,
    PRIMARY KEY (history_id, chunk_index)
);
```

### Schema Invariants
- **Non-Destructive Migrations:** Migration from v1 to v2 (`migrateV1ToV2`) creates missing tables with `CREATE TABLE IF NOT EXISTS`, preserving all existing bookmarks, history records, and tabs.
- **Private Browsing Isolation:** In-memory isolation ensures that private tabs (`isPrivate = true`) perform **zero inserts or updates** into `history`, `sessions`, or `ai_page_index`.
- **Nullable Embeddings:** All BLOB vector columns are explicitly nullable so that devices on the Minimal RAM tier never fail schema operations when AI is inactive.

---

## 2. Nova QuadView UI & Layout Contracts

Nova QuadView provides a multi-tab spatial layout supporting up to four live concurrent sessions.

### 2.1 QuadSlot Definition
The workspace defines four physical display slots:
```kotlin
enum class QuadSlot(val index: Int, val label: String) {
    SLOT_A(0, "A"),
    SLOT_B(1, "B"),
    SLOT_C(2, "C"),
    SLOT_D(3, "D");
}
```

### 2.2 QuadViewState Model
The presentation state is cleanly decoupled from tab state:
```kotlin
data class QuadViewState(
    var isQuadActive: Boolean = false,
    var activeSlot: QuadSlot = QuadSlot.SLOT_A,
    var focusedSlot: QuadSlot? = null,
    var slotATabId: String? = null,
    var slotBTabId: String? = null,
    var slotCTabId: String? = null,
    var slotDTabId: String? = null
)
```

### 2.3 Visual Layout Hierarchy
```
QuadView Workspace (workspaceRoot: FrameLayout)
└── quadGridHost (LinearLayout: vertical in portrait, horizontal in landscape)
    ├── rowTopQuad (LinearLayout: weight 1.0)
    │   ├── paneSlotA (QuadPaneView: weight 1.0)
    │   └── paneSlotB (QuadPaneView: weight 1.0)
    └── rowBottomQuad (LinearLayout: weight 1.0)
        ├── paneSlotC (QuadPaneView: weight 1.0)
        └── paneSlotD (QuadPaneView: weight 1.0)
```

### 2.4 QuadPaneView Component Anatomy
Each `QuadPaneView` encapsulates:
1. **Pane Header:**
   - Slot Badge (`A`, `B`, `C`, `D`) with active glow accent (`#3B82F6` for standard, `#8B5CF6` for private).
   - Domain / Title label (`tvPaneTitle`).
   - Focus Mode expand/restore button (`btnPaneFocus`).
   - Pane options menu button (`btnPaneMenu`).
   - Direct close button (`btnPaneClose`).
2. **WebView Container:**
   - Native `FrameLayout` (`paneWebViewContainer`) where `NovaWebView` instances are dynamically attached via view reparenting.
3. **Empty State View:**
   - Visible when slot is unassigned, presenting an interactive prompt (`+ Assign Tab`) to select an open tab or launch a new URL.

### 2.5 Pane Context Menu Actions
- **Focus Mode (Expand / Restore 2×2 Grid):** Toggles between balanced grid and 100% dominant view.
- **Swap with Slot...:** Swaps the current tab with any other slot (A, B, C, D) with immediate layout re-binding.
- **Reload Tab:** Invokes `tab.webView.reload()` in-place.
- **Open as Single Tab:** Sets the current slot's tab as the active primary tab and exits QuadView workspace.
- **Close Tab in Slot:** Closes the tab and returns the slot to an empty state.

---

## 3. Browser Controller Tool Contracts

All navigation and browser modifications route through `BrowserController.kt`. The controller validates parameters and guarantees security gate enforcement.

### 3.1 Controller API Definitions

| Method | Parameters | Return Type | Description & Gating |
|---|---|---|---|
| `evaluateNavigation` | `rawInput: String, isRedirect: Boolean, searchTemplate: String` | `Pair<String, SecurityDecision>` | Sanitizes input and evaluates against `DeterministicSecurityGate`. **Mandatory gatekeeper.** |
| `onPageVisited` | `url: String, title: String?, isPrivate: Boolean` | `Unit` | Writes to `history` and `history_fts` on `Dispatchers.IO`. Ignored if `isPrivate == true`. |
| `searchHistory` | `query: String` | `List<HistoryItem>` | Executes FTS5 BM25 prefix search (`"word"*`) with `LIKE` fallback. |
| `toggleBookmark` | `url: String, title: String?, onResult: (Boolean) -> Unit` | `Unit` | Adds or removes bookmark idempotently. |
| `isBookmarked` | `url: String` | `Boolean` | Fast indexed lookup in `bookmarks` table. |
| `clearHistory` | *None* | `Unit` | Wipes both `history` and `history_fts` in a single SQLite transaction. |

### 3.2 Non-Bypassable Navigation Invariant
Any tool call or AI-originated navigation (such as `open_url(url)`) must pass through `BrowserController.evaluateNavigation()`. An AI model or controller script can never issue a direct `.loadUrl()` to `NovaWebView` without passing through the `DeterministicSecurityGate`.

---

## 4. UI Design System & Tokens (Liquid System)

### 4.1 Color Palette
- **Background Canvas (Deep Dark):** `#0B0E14`
- **Surface Elevation 1 (Cards & Toolbars):** `#121824`
- **Surface Elevation 2 (Panes & Dialogs):** `#1A2333`
- **Border / Outline:** `#222E42`
- **Accent Primary (Action / Active Tab):** `#3B82F6` (Electric Blue)
- **Accent Private Mode:** `#8B5CF6` (Electric Violet)
- **Security - Blocked / Danger:** `#EF4444` (Crimson)
- **Security - Warning / Typosquat:** `#F59E0B` (Amber)
- **Security - Safe / Verified:** `#10B981` (Emerald)

### 4.2 Explainable Warning Interstitial Layout (`SecurityWarningActivity`)
```
+-------------------------------------------------------------+
| [!] CRITICAL SECURITY WARNING                               |
|                                                             |
| URL: https://paypa1.com/login                               |
| Risk Score: 85/100                                          |
|                                                             |
| Threat Findings:                                            |
| * Levenshtein distance 1 to protected brand: paypal.com     |
| * High Shannon entropy in domain labels (potential DGA)     |
| * Threat Feed: LOCAL_HEURISTIC                              |
|                                                             |
| [ Return to Safety (Recommended) ]                          |
|                                                             |
| [ Advanced: Override & Proceed Anyway (Not Recommended) ]    |
+-------------------------------------------------------------+
```

### 4.3 Motion & Interaction Philosophy
- Smooth layout weight animations when transitioning between 2×2 grid and Focus Mode (`NovaMotion.kt`).
- Haptic feedback on slot selection, tab closure, and long-press contextual menus.
- Zero jank: Layout updates occur on the main thread while database queries and security heuristics run asynchronously on background coroutines.
