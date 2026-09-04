# NovaBrowser — Design Document (Data + UI + Tool Contracts)

Companion docs: `PRD.md` / `PLAN.txt` / `ARCHITECTURE.md` / `SECURITY.md`

---

## 1. Data Model (SQLite)

One physical SQLite DB, logically separated by table group.

### Browser DB

```sql
-- history
CREATE TABLE history (
  id INTEGER PRIMARY KEY,
  url TEXT NOT NULL,
  title TEXT,
  domain TEXT NOT NULL,
  visited_at INTEGER NOT NULL,       -- epoch ms
  summary TEXT,                      -- nullable, filled by AI tier if available
  embedding BLOB,                    -- nullable, only on tiers with semantic search
  extracted_text_meta TEXT           -- optional, short extracted metadata
);
CREATE INDEX idx_history_domain ON history(domain);
CREATE INDEX idx_history_visited_at ON history(visited_at);
-- FTS for lexical search available at every tier:
CREATE VIRTUAL TABLE history_fts USING fts5(title, url, summary, content='history', content_rowid='id');

-- bookmarks
CREATE TABLE bookmarks (
  id INTEGER PRIMARY KEY,
  url TEXT NOT NULL,
  title TEXT,
  folder TEXT,
  created_at INTEGER NOT NULL
);

-- tabs / sessions
CREATE TABLE sessions (
  id INTEGER PRIMARY KEY,
  tab_id TEXT NOT NULL,
  url TEXT,
  title TEXT,
  is_private INTEGER DEFAULT 0,
  last_active_at INTEGER
);

-- downloads
CREATE TABLE downloads (
  id INTEGER PRIMARY KEY,
  url TEXT NOT NULL,
  filename TEXT,
  mime_type TEXT,
  status TEXT CHECK(status IN ('pending','safe','quarantined','blocked','completed')),
  risk_reason TEXT,
  created_at INTEGER NOT NULL
);
```

### Security DB

```sql
CREATE TABLE security_rules (
  id INTEGER PRIMARY KEY,
  rule_type TEXT CHECK(rule_type IN ('domain','url','tracker','malware')),
  pattern TEXT NOT NULL,
  source TEXT CHECK(source IN ('URLHAUS','EASYLIST','EASYPRIVACY','LOCAL_HEURISTIC')),
  severity TEXT CHECK(severity IN ('block','warn','info')),
  updated_at INTEGER NOT NULL
);
CREATE INDEX idx_security_pattern ON security_rules(pattern);

CREATE TABLE snapshot_meta (
  feed_source TEXT PRIMARY KEY,
  last_updated_at INTEGER NOT NULL,
  rule_count INTEGER
);
```

### AI Index

```sql
CREATE TABLE ai_page_index (
  history_id INTEGER PRIMARY KEY REFERENCES history(id),
  chunk_index INTEGER NOT NULL,
  chunk_text TEXT NOT NULL,
  chunk_embedding BLOB           -- nullable below Standard tier
);
```

**Design notes**
- `embedding`/`chunk_embedding` columns are always nullable — schema must not force semantic-search-tier data onto Minimal-tier devices.
- FTS5 lexical search is the baseline available at every device tier; it is the fallback, not an afterthought.
- No table stores raw page HTML — only extracted/short text metadata, to keep storage bounded.

---

## 2. Tool Contracts (Browser Controller API)

**Rule:** the LLM never directly executes a browser action. It emits a structured, schema-validated call; the Browser Controller validates permissions and parameters, then executes.

```jsonc
// Example: history search intent
{
  "intent": "search_history",
  "query": "cricket",
  "time_range": "yesterday",
  "action": "open",       // optional follow-up action
  "result_count": 1
}
```

### Defined tools (MVP set)

| Tool | Params | Notes |
|---|---|---|
| `search_history(query, time_range?, domain?)` | text query, optional filters | Always available (lexical); semantic ranking added on supporting tiers |
| `search_bookmarks(query, folder?)` | text query, optional folder | |
| `search_current_page(query)` | text query | Runs over the retrieval-chunked current page only |
| `open_url(url)` | absolute URL | **Must still pass the Security Gate** — tool-calling does not bypass navigation security |
| `open_history_item(history_id)` | id | Resolves to a URL, then goes through the same `open_url` path |
| `switch_tab(tab_id)` | id | |
| `summarize_page(history_id | current)` | | Retrieval-based, not full-page dump |
| `find_on_page(text)` | text | Deterministic, not LLM-routed at all |
| `create_bookmark(url, title?, folder?)` | | |

**Validation rules for every tool call:**
1. Schema-validate params before execution (reject malformed/unexpected fields).
2. Re-run `open_url`/navigation-producing tools through the full Security Gate — no exceptions for AI-originated navigation.
3. Never execute a tool call whose target action a webpage's own script tried to trigger — tool calls originate only from the trusted UI layer (see SECURITY.md §JS bridge boundary).
4. Log tool calls for the current session (for debugging/demo), not persisted long-term unless the user explicitly saves it as history (which it already would be, via normal navigation).

---

## 3. Core UX Flows

### 3.1 Natural-language history search

```
User (voice/text): "Maine woh GitHub wali website kal dekhi thi
                     jisme Android security ka article tha."
      |
      v
Local STT (if voice)
      |
      v
Intent parser (tiny LLM, or rule-based fallback on Minimal tier)
      |
      v
Structured query: { intent: search_history,
                     terms: ["GitHub","Android security"],
                     time: "yesterday" }
      |
      v
SQLite lexical (+ semantic where available) retrieval
      |
      v
Top matches -> tiny LLM formats result (or plain list on Minimal tier)
      |
      v
User sees likely matching page -> optional: open it
```

On **Minimal tier** (no LLM loaded), the same flow runs with a rule-based keyword+time parser instead of an LLM intent step — the feature must still work, just without flexible phrasing.

### 3.2 Blocked navigation (explainability)

```
+------------------------------------------+
|            ACCESS BLOCKED                 |
|                                            |
| suspicious-domain.example                  |
|                                            |
| Reason                                    |
| • Matched local malware threat data        |
| • Suspicious redirect chain detected        |
|                                            |
| Security database: last updated ...        |
|                                            |
| [Go Back]              [Advanced]           |
+------------------------------------------+
```

Risk states shown to the user: `BLOCKED`, `HIGH RISK`, `SUSPICIOUS`, `KNOWN SAFE`, `UNKNOWN`. **`UNKNOWN` is never displayed or treated as `KNOWN SAFE`.**

### 3.3 Main UI shape

```
+--------------------------------------------------+
|  NOVA                                         +  |
+--------------------------------------------------+
| [GitHub] [Docs] [YouTube] [Research]              |
+--------------------------------------------------+
|                                                    |
|                  WEB CONTENT                       |
|                                                    |
+--------------------------------------------------+
|  * Ask Browser                                     |
|  "Find the GitHub page I saw yesterday"             |
+--------------------------------------------------+
```

Browser modes: **Normal / Private / Secure / Research / AI-local-only.**

### 3.4 Settings surface (must exist, not optional)

- AI tier: Auto-detected / Minimal / Light / Standard (manual override)
- Cloud AI: **Off** (default) / Hybrid / opt-in cloud key
- Security feed: last updated timestamp, manual "update now"
- Data: clear history / clear AI index / export nothing-leaves-device confirmation copy

---

## 4. Visual/Interaction Notes

- Block/warning screens use a **reason list**, never a single unexplained sentence.
- The "Ask Browser" bar is persistent but never auto-expands into a full chat UI — it stays a narrow command bar, reinforcing "assistive," not "chatbot sidebar" (per PRD vision).
- Low Memory Mode, if forced on by tiering, should be visibly indicated (small badge/label) so users understand why AI features are limited, rather than silently degrading with no explanation.
