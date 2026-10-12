# DocIndex frontend

Web UI for the Local Document Indexing and Search Engine (CSC2104, Object Oriented Programming mini project).

Plain HTML, CSS and JavaScript. There is no build step, so any static host can serve this folder, and the Java backend serves it too.

## Run it

**With the Java engine** (searches real folders on your computer):

```bat
cd backend
serve.bat
```

Open http://localhost:8080. The page finds the backend by itself and says "Java engine connected" in the Index panel.

**Demo only** (no Java needed, searches the built-in sample files in your browser):

```bash
cd frontend
python -m http.server 5173
```

Open http://localhost:5173.

The first load needs an internet connection for the fonts and icons (see Credits).

## Host it on Vercel

Vercel hosts static files, so the deployed site runs in demo mode. It cannot run the Java server.

1. Push the repository to GitHub.
2. On vercel.com choose **Add New, Project** and import the repository.
3. Set **Framework Preset** to `Other` and **Root Directory** to `frontend`.
4. Leave **Build Command** and **Output Directory** empty, then deploy.

No `vercel.json` is needed. The landing page opens first, and **Open app** leads into the search app.

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

## Demo mode and backend mode

`js/api.js` decides by itself where answers come from. When the app page loads on `localhost`, it asks `GET /api/health`.

- If the Java backend answers, the app runs in **backend mode** and every request goes to the endpoints below.
- Otherwise it runs in **demo mode**: the sample documents in `js/mock-data.js` are indexed and searched inside the browser, with the same rules as the Java engine.

On any address other than `localhost`, such as a Vercel site, it skips the check and uses demo mode. The landing page demo always uses the sample files.

To force one mode while developing, set `FORCE_MODE` at the top of `js/api.js` to `"demo"` or `"backend"`.

## API

These are the endpoints the Java backend implements. All answers are JSON.

| Request | Body or query | Response |
|---|---|---|
| `GET /api/health` | none | `{ "ok": true }` |
| `POST /api/index` | `{ "path": "C:\\notes" }` | `{ "path", "documents", "terms", "tookMs", "skipped" }` |
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
      "id": 6,
      "title": "threads-and-synchronization.txt",
      "path": "sample-docs/threads-and-synchronization.txt",
      "score": 3,
      "matches": { "java": 1, "thread": 2 },
      "snippet": "A thread is the smallest unit of execution in Java. ..."
    }
  ]
}
```

- `terms` is the query after tokenizing and removing stop words. The UI uses it to highlight matches, so the backend's tokenizer decides what gets highlighted.
- `score` is the sum of `matches`. Results come back sorted by `score`, highest first, and equal scores are ordered by file name.
- `mode: "all"` returns files containing every term (set intersection). `mode: "any"` returns files containing at least one (set union).
- `skipped` is how many files could not be read while indexing.
- The backend records history itself when it handles a search.

Errors are `{ "error": "message", "code": "CODE" }` with a non-2xx status:

| Code | Status | Meaning |
|---|---|---|
| `NOT_INDEXED` | 409 | A search came before any folder was indexed. The UI asks the user to index first. |
| `BAD_PATH` | 400 | The folder path is missing or is not a folder. |
| `EMPTY_QUERY` | 400 | The search had no words. |
| `METHOD_NOT_ALLOWED` | 405 | The address does not accept that kind of request. |
| `NOT_FOUND` | 404 | No such address. |
| `SERVER_ERROR` | 500 | Something unexpected went wrong. |

## Design notes

- One accent, a highlighter lime, used only as a background behind dark text.
- Light and dark themes follow the system setting.
- Motion only animates `transform` and `opacity`, and switches off under `prefers-reduced-motion`.

## Credits

- Geist and Geist Mono (SIL Open Font License), loaded from Google Fonts.
- Phosphor Icons (MIT), loaded from unpkg.
