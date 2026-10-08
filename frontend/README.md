# DocIndex frontend

Web UI for the Local Document Indexing and Search Engine (CSC2104, Object Oriented Programming mini project).

Plain HTML, CSS and JavaScript. There is no build step, so the Java backend can serve this folder as static files.

## Run it

```bash
cd frontend
python -m http.server 5173
```

Open http://localhost:5173. The first load needs an internet connection for the fonts and icons (see Credits).

## Pages

| File | What it is |
|---|---|
| `index.html` | Landing page. The search box in the hero is a working demo on the sample files. |
| `app.html` | The search app: index a folder, search it, see ranked results and history. |

## Structure

```
css/base.css      tokens, type, nav, buttons, motion rules (shared)
css/landing.css   landing page
css/app.css       search app
js/shared.js      small helpers: escaping, highlighting, count-up
js/mock-data.js   six sample documents used in demo mode
js/api.js         the only file that talks to a data source
js/landing.js     scroll reveals and the hero demo
js/app.js         indexing, searching, results, history
```

## Demo mode and the Java backend

`js/api.js` has one switch:

```js
const USE_MOCK = true;
```

While it is `true`, the browser answers every request itself from `js/mock-data.js`, so the UI works before the backend exists. When the Java backend is ready, set it to `false`. The same calls then go to the endpoints below, and no other file needs to change. The landing-page demo keeps using the sample files either way.

### API contract the backend should follow

| Request | Body or query | Response |
|---|---|---|
| `POST /api/index` | `{ "path": "C:\\notes" }` | `{ "path", "documents", "terms", "tookMs" }` |
| `GET /api/search` | `q` (text), `mode` (`all` or `any`) | see below |
| `GET /api/history` | none | `[ { "query", "mode", "at" } ]`, newest first, at most 10 |
| `DELETE /api/history` | none | `204 No Content` |

Search response:

```json
{
  "query": "java thread",
  "mode": "any",
  "terms": ["java", "thread"],
  "total": 4,
  "tookMs": 2,
  "results": [
    {
      "id": 2,
      "title": "threads-and-synchronization.txt",
      "path": "notes/threads-and-synchronization.txt",
      "score": 3,
      "matches": { "java": 1, "thread": 2 },
      "snippet": "A thread is the smallest unit of execution in Java. ..."
    }
  ]
}
```

- `terms` is the query after tokenizing and removing stop words. The UI uses it to highlight matches, so the backend's tokenizer decides what gets highlighted.
- `score` is the sum of `matches`. Results come back sorted by `score`, highest first.
- `mode: "all"` returns files containing every term (set intersection). `mode: "any"` returns files containing at least one (set union).
- Errors are `{ "error": "message", "code": "NOT_INDEXED" }` with a non-2xx status. The UI treats `NOT_INDEXED` as "ask the user to index first".
- The backend records history itself when it handles a search.

## Design notes

- One accent, a highlighter lime, used only as a background behind dark text.
- Light and dark themes follow the system setting.
- Motion only animates `transform` and `opacity`, and switches off under `prefers-reduced-motion`.

## Credits

- Geist and Geist Mono (SIL Open Font License), loaded from Google Fonts.
- Phosphor Icons (MIT), loaded from unpkg.
