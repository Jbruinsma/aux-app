# Backend requests

What the frontend needs that isn't in `docs/openapi.json` yet, most needed first. Where a page already uses an
endpoint before it exists, it's mocked in the frontend and the flag to switch it off is noted. All endpoints below need
`Authorization: Bearer <token>` unless marked public, and errors use the usual `AuxServerError` body.

The Dashboard and the header get their data from the live event stream (`GET /api/users/me/events`), so changes to
that data are asked for on the event, not on the REST endpoint it mirrors.

Last checked against `main` on 2026-11-12.

______________________________________________________

## 1. An artist's songs

For the artist page (`/artist/:artistId`, roadmap task 15), which is built. For now it finds the artist's songs by
running `GET /api/core/search?category=MUSIC` for the artist's name and keeping the results with that `artistId`
(`fetchArtistPieces` in `frontend/src/utils/artist.js`). That works, but it scans up to 200 search results per page
view, comes back sorted by name, and has no `uploader` on the pieces. A real endpoint would fix all three.

### GET /api/artists/{artistId}/music-pieces?cursor=

Public; a token is optional. Pieces credited to the artist: all public ones, plus the caller's own private ones.
Pages of 50, sent back with the opaque `cursor` the way `GET /api/playlists/{playlistId}/pieces` works.

Response `200`:
```json
{
  "musicPieces": [ /* MusicPieceOverview, with uploader */ ],
  "nextCursor": "…"
}
```
`nextCursor` is null on the last page. `404 ARTIST_NOT_FOUND` when the artist doesn't exist.

Questions:
- Order: newest first, or most played first? The frontend would prefer most played (like a "Popular" list), with
  newest as the tie-break.
- Playback: the frontend plays these with `/stream` and no `playlistId`, the same as My uploads. That works for public
  pieces and the caller's own. Is that right, or should there be an artist play queue like `/playlists/{id}/play`?

## 2. Delete account

### DELETE /api/users/me

Mocked: `ACCOUNT_MOCKED` in `frontend/src/utils/account.js` (only `deleteAccount` still uses it). Used by Settings → Account →
Delete your account, after a 6-second confirmation.

Deletes the caller's account and wipes their data: profile details, picture and banner, playlists (as
`DELETE /api/playlists/{playlistId}` does), memberships and invites, uploaded MP3s including the files in R2, favorites,
follows and play history. The token stops working afterwards.

Response `204`, no body.

Questions:
- Should it require the current password, like email and password changes now do? If so, the body would be
  `{ "currentPassword": "…" }` and a wrong one a `403 WRONG_PASSWORD` (not 401: the frontend logs out on 401).
- What happens to the caller's songs that sit in other people's playlists: removed from them, or kept?

## 3. Dashboard

### People who hear it too: done, as an event

Shipped as the `matches` event on `GET /api/users/me/events` instead of a REST endpoint, so the Dashboard section
updates live. Body: `MatchesResponse`, the shape asked for here plus `sharedTags` (up to 3 curated tags, for matches
on tags alone). Profiles also carry `compatibility`, `sharedArtists` and `sharedTags`. Frontend work is in
`frontend/ROADMAP.md`; how the score works is in `docs/compatibility.html`.

### Changes to existing events

- `recents` event: add the playlist owner's username next to `playlistId` (for example `playlistOwnerUsername`, null
  when `playlistId` is null), as notifications already do. Playlist page URLs need the owner's username, so recent
  songs can't link to the playlist they were played from yet. (Clicking a recent song already plays it from that
  playlist; only the link is missing.)

## 4. Music piece page

For opening a details page with a Play button when an upload is clicked (frontend roadmap, Blocked list). Nothing is
built yet.

### GET /api/music-pieces/{musicPieceId}

Public; a token is optional. Only `PUT` exists on this path today.

Response `200`: a `MusicPieceOverview` (with `uploader` and `tags`) plus:
```json
{
  "isPublic": true,
  "durationSeconds": 214,
  "playCount": 1280,
  "createdAt": "2026-10-03T21:28:57Z"
}
```
`isPublic` only matters to the uploader; it can be left out for everyone else. `404 MUSIC_PIECE_NOT_FOUND` when it
doesn't exist or is private and the caller can't play it.

Question: should `playCount` be public, or only shown to the uploader?

## 5. Smaller fixes

- **Search response has examples but no schema.** `GET /api/core/search` documents its `200` body only through one
  example per category, so the docs' schema view doesn't list the fields, their types or which can be null
  (`UserSearchResult`, `MusicPieceSearchResult` and `PlaylistSearchResult` exist in code). Adding the schema would help;
  the frontend uses this for dashboard search and for finding people to invite.

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
