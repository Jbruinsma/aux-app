# Backend requests

What the frontend needs that isn't in `docs/openapi.json` yet, most needed first. Where a page already uses an
endpoint before it exists, it's mocked in the frontend and the flag to switch it off is noted. All endpoints below need
`Authorization: Bearer <token>` unless marked public, and errors use the usual `AuxServerError` body.

The Dashboard and the header get their data from the live event stream (`GET /api/users/me/events`), so changes to
that data are asked for on the event, not on the REST endpoint it mirrors.

Last checked against `main` on 2026-11-12.

______________________________________________________

## 1. Playlist link for the restored player

For the mobile full player's "Go to playlist" option (mock: `frontend/mockups/mobile-player.html`). The playlist route
is `/playlist/{ownerUsername}/{playlistId}`, and the menu row shows the playlist's name. During a session the frontend
knows both from the page that started playback. After a reload the player is restored from
`GET /api/users/me/last-playback`, which only returns `playlistId`, so the link can't be built.

### GET /api/users/me/last-playback: add two fields

Add `playlistOwnerUsername` (as `LastPlayback` already has) and `playlistName` to `LastPlaybackResponse`. Both are
null whenever `playlistId` is null.

Response `200`:
```json
{
  "musicPiece": { /* MusicPieceOverview */ },
  "playlistId": "p_8sK2mQ",
  "playlistOwnerUsername": "justin",
  "playlistName": "Road trip",
  "playPreferences": { "shuffle": false, "repeat": "OFF" }
}
```

Until this ships, the frontend hides "Go to playlist" for a restored song and shows it again once a playlist is played.

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
