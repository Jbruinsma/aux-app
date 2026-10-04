# Backend requests

Endpoints the frontend needs that aren't in `backend/docs/openapi.json` yet. The frontend mocks them until they ship.

______________________________________________________

### DELETE /api/users/me

Deletes the logged-in user's account and wipes their data: profile details, picture and banner, playlists, uploaded
MP3s (including the files in R2), favorites, follows and play history. After this the token should stop working.

Response `204`, no body.

### Open questions

- Should deleting the account also require the current password? (Email and password changes now do.)
- When an account is deleted, what happens to its songs that sit in other people's playlists?
- Should a password change end the user's other sessions? Tokens are stateless JWTs now, so they'd stay valid until
  they expire.

## Playlist page

### DELETE /api/playlists/{playlistId}

Mocked in `src/utils/playlist.js`; set `PLAYLIST_DELETE_MOCKED = false` once it exists. Needs
`Authorization: Bearer <token>`.

Deletes a playlist the caller owns. The songs stay in the uploader's uploads.

Response `204`, no body. Errors: `404 PLAYLIST_NOT_FOUND` (missing, or not the caller's).

### DELETE /api/playlists/{playlistId}/pieces/{musicPieceId}

Not mocked yet; the edit playlist page only edits name, cover and visibility until this exists. Needs
`Authorization: Bearer <token>`.

Removes one music piece from a playlist the caller owns. The piece itself stays in the uploader's uploads and in any
other playlist.

Response `204`, no body. Errors: `404 PLAYLIST_NOT_FOUND` (missing, or not the caller's), `404 MUSIC_PIECE_NOT_FOUND`
(not in this playlist).

Open question: should the edit page also reorder songs? If so, something like `PUT /api/playlists/{playlistId}/order`
with `{ "musicPieceIds": [...] }` in the new order.

### Playback

The player (`src/stores/music.js`) still calls the old UnChained routes, which don't exist:
`GET /api/playlists/{owner}/{id}/play/{index}?shuffle=` and `GET /api/playlists/{owner}/{id}/play-shuffled/`.
It needs a way to get a playable (signed) MP3 URL for each song in a playlist. What shape do you want for that?

### Sharing

"Manage access" on the playlist page links to the old Add friends page. Is sharing a playlist with specific people
(view or edit) still planned? There's nothing for it in the API yet.
