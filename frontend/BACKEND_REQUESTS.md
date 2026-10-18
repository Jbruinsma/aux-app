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

The retired playlist play/shuffle and playback-update calls are now guarded in
`src/stores/music.js` and do not send requests. Phase 2 will replace those guards
with queue construction, playable URLs, and listening-event recording against the
current API. `GET /api/users/me/last-playback` is integrated for metadata display;
204 means no history, and the 200 response contains `musicPiece` plus an optional
`playlistId`, without an audio URL, queue, or seek position.
### Sharing

"Manage access" on the playlist page links to the old Add friends page. Is sharing a playlist with specific people
(view or edit) still planned? There's nothing for it in the API yet.

## Dashboard

### GET /api/users/matches

For Dashboard → People who hear it too (`/dashboard/people-who-hear-it-too`), which shows an empty state until this
exists. Listeners who share the caller's taste, best match first. Needs `Authorization: Bearer <token>`.

Response `200`:
```json
{
  "matches": [
    {
      "user": { "userId": "u_41x9Rb", "username": "justin", "pfpUrl": "https://…" },
      "compatibility": 87,
      "sharedArtists": [{ "artistId": "a_8f3k2Q", "artistName": "Tame Impala", "artistPfpUrl": null }]
    }
  ]
}
```
`compatibility` is 0-100 (the style guide's Low to Super levels). `sharedArtists`: up to 3. `matches` is empty when the
caller has played nothing.

### Questions

- Recent listening and Top artists each have their own page now. Could `GET /api/users/recent-listening` and
  `GET /api/users/top-artists` take a `limit` (say up to 20)? At 4 items the pages look sparse.
- Recent listening gives a `playlistId` but not the owner's username, which the playlist page URL needs. Could it
  include the owner's username so a recent song can link to its playlist?
