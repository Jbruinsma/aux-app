# Backend requests

What the frontend needs that isn't in `docs/openapi.json` yet, most needed first. Where a page already uses an
endpoint before it exists, it's mocked in the frontend and the flag to switch it off is noted. All endpoints below need
`Authorization: Bearer <token>`, and errors use the usual `AuxServerError` body.

The Dashboard and the header get their data from the live event stream (`GET /api/users/me/events`), so changes to
that data are asked for on the event, not on the REST endpoint it mirrors.

Last checked against `main` on 2026-10-21.

______________________________________________________

## 1. Delete account

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

## 2. Dashboard

### GET /api/users/matches

For Dashboard → People who hear it too (`/dashboard/people-who-hear-it-too`), which shows an empty state until this
exists. Listeners who share the caller's taste, best match first.

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

### Changes to existing events

- `recents` event: add the playlist owner's username next to `playlistId` (for example `playlistOwnerUsername`, null
  when `playlistId` is null), as notifications already do. Playlist page URLs need the owner's username, so recent
  songs can't link to the playlist they were played from yet.

## 3. Smaller fixes

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
