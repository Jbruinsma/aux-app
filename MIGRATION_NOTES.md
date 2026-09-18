# Flask → FastAPI + SQLite migration notes

## Run it

```bash
pip install -r requirements.txt
uvicorn backend.app:app --host 0.0.0.0 --port 5000 --reload
```

Run from the repo root (not from inside `backend/`) — imports are `backend.*` throughout.

## What changed

- `backend/app.py`: `Flask` → `FastAPI`, `flask_cors.CORS` → `fastapi.middleware.cors.CORSMiddleware`
  (`allow_origins=["*"]`, matching Flask-Cors' permissive default).
- Every `@*.route(...)` → method-specific decorator (`@router.get/post`) on `APIRouter`s, mounted
  with the same `url_prefix`/`prefix` and identical paths.
- `request.get_json()` → a plain `dict` function parameter (FastAPI reads the JSON body into it
  automatically; no Pydantic model added, per the "no new models" constraint).
- `request.form` / `request.files` → `Form(...)` / `File(...)` / `UploadFile` parameters. All form
  fields default to `None` (never `Form(...)`/required), matching Flask's `request.form.get(...)`,
  which silently returns `None` on a missing field instead of erroring.
- `request.args.get(...)` → typed query parameters with the same string defaults.
- `jsonify(x), code` → `JSONResponse(x, status_code=code)`, used uniformly so every route keeps its
  original status code (including the many "error" payloads that still return `200`).
- `send_from_directory` → `fastapi.responses.FileResponse`, with an explicit `os.path.isfile` check
  added before it, since `FileResponse` (unlike Flask's helper) does not 404 on its own — this was
  necessary to keep the missing-file behavior identical, not a behavior change.
- **Persistence** (`backend/classes/user_manager.py`, new `backend/db.py`): the AVL tree + Windows-only
  pickle path (`C:\Users\...`, which never resolved on this machine — `load_user_manager()` was
  silently starting from an empty tree on every real run here) is replaced by SQLite. Tables:
  `users`, `playlists`, `music_pieces`, `playlist_shared_with`, `playlist_saved_by`,
  `user_playlists_added_to`, `user_saved_playlists` — mirroring the fields the AVL tree actually held
  (inspected via the checked-in `backend/users.pkl`). `UserManager` keeps the same public interface
  (`search`, `insert`, `delete`, `contains_key`, `update_key`, `in_order_traversal`) so the route code
  and the `User`/`Playlist`/`MusicPiece` business-logic classes are untouched; only the load/save
  backing store changed. `backend/instances.py` now calls `init_db()` (creates tables if missing)
  before the first load.
- The one-time contents of `backend/users.pkl` were migrated into `backend/aux.db` (verified
  byte-identical via round-trip `to_dict()` comparison). The old pickle file is left in place,
  untouched, in case you want to keep it as a backup; it's no longer read by the app.
- `requirements.txt`: dropped `Flask`, `Flask-Cors`; added `fastapi`, `uvicorn`, `python-multipart`
  (required by FastAPI for form/file parsing). Kept `Werkzeug` (only for `secure_filename`, which
  isn't Flask-specific) and `bcrypt` (unchanged).
- Added `.gitignore` for `backend/aux.db` and `__pycache__/` — none existed before, and the SQLite
  file is a runtime artifact like the old pickle file was.
- Fixed the two `from routes.X import ...` lines in `backend/app.py` to `from backend.routes.X import ...`,
  matching every other module's import style. This was necessary for the app to actually import when
  run as `backend.app:app` from the repo root (the stated run convention in `README.md`); it does not
  change any endpoint behavior.

## Preserved as-is (not "fixed", flagged instead)

- `users.py`'s `delete_user` and `Thread(target=..., args=username, daemon=True)` passes the raw
  username string as `args` instead of `(username,)`. Same pre-existing bug as before: the background
  cleanup thread will raise inside the thread (silently, since nothing joins it) rather than actually
  running `traverse_and_delete_user`. Left unchanged to not alter behavior.
- `add_friends_to_playlist` will `TypeError` if the JSON body has no `friends` key (`data.get('friends')`
  is `None`, then iterated). Unchanged.
- Playlist `create`/`edit` routes accept missing `uuid`/`owner`/`name`/`isPublic` form fields silently
  (`Form(None)` everywhere) exactly like Flask's `request.form.get(...)`, rather than FastAPI's default
  `422` on a missing required field.

## Possible subtle differences to watch for

- CORS: Flask-Cors' exact default header set may differ slightly from `CORSMiddleware`'s in edge cases
  (e.g. `Access-Control-Allow-Credentials` handling with a wildcard origin). Functionally equivalent for
  this app's same-origin-free setup, but not header-byte-identical in all cases.
- `debug=True` in the old `app.run(...)` also enabled Flask's interactive in-browser debugger on
  unhandled exceptions. `uvicorn --reload` (used in the `__main__` block and the run command above)
  only gives auto-reload, not that debugger. No equivalent was added.
- SQLite writes are "replace everything" on every `save_user_manager()` call (matching the old
  behavior of pickling the *entire* tree on every save) rather than targeted per-row updates. Same
  observable behavior, just worth knowing if you later want to make saves incremental.
