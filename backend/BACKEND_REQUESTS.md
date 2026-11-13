# Backend requests

What the frontend needs that isn't in `docs/openapi.json` yet, most needed first. Where a page already uses an
endpoint before it exists, it's mocked in the frontend and the flag to switch it off is noted. All endpoints below need
`Authorization: Bearer <token>` unless marked public, and errors use the usual `AuxServerError` body.

The Dashboard and the header get their data from the live event stream (`GET /api/users/me/events`), so changes to
that data are asked for on the event, not on the REST endpoint it mirrors.

Last checked against `main` on 2026-11-12.

______________________________________________________

## 1. Explore: songs for a tag

For the genre pages on Explore (`/explore/tag/:tag`, frontend roadmap task 27). Mocked: `TAG_PIECES_MOCKED` in
`frontend/src/utils/explore.js` shows sample songs, labeled as a preview.

### GET /api/tags/{tag}/music-pieces?cursor=

Public pieces carrying the tag (matched case-insensitively), most played first, with newest as the tie-break. Pages of
50, sent back with the opaque `cursor` the way `GET /api/playlists/{playlistId}/pieces` works. A token is optional;
leave out the caller's own private pieces like everyone else's, since the page is for discovery.

Response `200`:
```json
{
  "musicPieces": [ /* MusicPieceOverview, with uploader */ ],
  "nextCursor": "…"
}
```
`nextCursor` is null on the last page. An unknown tag is an empty list, not a 404.

Questions:
- "Most played": all time, or the last 30 days like Discover's fallback? The frontend would prefer recent plays, so the
  page changes over time.

## 2. Explore: playable top songs

For Top this week on Explore (frontend roadmap task 27). `GET /api/core/top` returns `TopMusicPiece` with only `name`,
`coverUrl` and `artistSummary`, so the rows can't play and can't show the style guide's play count bar. The frontend
already plays a row and draws the bar when these fields are present.

### GET /api/core/top: add three fields

Add to each `TopMusicPiece` (or return `MusicPieceOverview` plus `playCount`):
- `musicPieceId`: public id, to play it with `/stream`.
- `uploader`: `PlaylistOwner`, as on `MusicPieceOverview`.
- `playCount`: integer, the plays counted for the list this week.

The landing page uses the same endpoint and only reads the three fields it has today, so it's unaffected.

## Open questions

- Should a password change end the user's other sessions? Tokens are stateless JWTs, so they stay valid until they
  expire.
- Should editors be able to reorder songs? If so, something like `PUT /api/playlists/{playlistId}/order` with
  `{ "musicPieceIds": [...] }` in the new order. Removing a song leaves a gap in `playlist_position` (cursors are
  positions, so renumbering would make a client holding one skip a song); a reorder would need its own answer for
  cursors handed out before it.
- **Autoplay** (frontend roadmap task 16, not decided). If the team wants Apple Music-style autoplay when a queue ends
  with repeat `OFF`, the frontend could build it on `GET /api/music-pieces/{musicPieceId}/discover` (seeded with the
  last song played), with no new endpoint. A dedicated `GET /api/users/me/autoplay?after={musicPieceId}` that leaves
  out recently played songs would give better results. Which does the backend prefer, if any?
