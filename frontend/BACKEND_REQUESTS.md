# Backend requests

What the frontend needs that isn't in `backend/docs/openapi.json` yet, most needed first. Where a page already uses an
endpoint before it exists, it's mocked in the frontend and the flag to switch it off is noted. All endpoints below need
`Authorization: Bearer <token>`, and errors use the usual `AuxServerError` body.

Last checked against `main` on 2026-10-18.

______________________________________________________

## 1. Delete a playlist

### DELETE /api/playlists/{playlistId}

Mocked: `PLAYLIST_DELETE_MOCKED` in `src/utils/playlist.js`. Used by ⋯ → Delete playlist on the playlist page, which
only the owner sees.

Owner only. Deletes the playlist along with its members, pending invites, saves and track list. The songs stay in their
uploaders' uploads and in any other playlist.

Response `204`, no body.

Errors: `404 PLAYLIST_NOT_FOUND` (missing, or not the caller's; same as the other owner-only endpoints).

## 2. Notifications

The header has a notification bell. Today it can only list pending playlist invites (`GET /api/playlists/invites`),
and which ones the user has seen is kept in the browser's localStorage, so the red badge comes back on another device
or in a private window. These two endpoints would make it a real notification feed.

### GET /api/notifications?limit={1-50}&cursor={cursor}

Newest first. `limit` defaults to 20; `cursor` works like the playlist pieces cursor (opaque, null when done).

Response `200`:
```json
{
  "notifications": [
    {
      "notificationId": "n_7Kq2",
      "type": "PLAYLIST_INVITE",
      "createdAt": "2026-10-18T14:03:11Z",
      "read": false,
      "actor": { "userId": "u_41x9Rb", "username": "justin", "pfpUrl": "https://…" },
      "playlist": { "playlistId": "p_7c2dK1", "playlistName": "Late Night Drive", "playlistCoverUrl": "https://…" },
      "permission": "EDITOR",
      "count": null
    }
  ],
  "unreadCount": 3,
  "nextCursor": null
}
```

Types to start with, each about a playlist (`playlist` always set):

| `type` | Sent to | When | Extra fields |
|---|---|---|---|
| `PLAYLIST_INVITE` | the invitee | someone invites them | `permission` (LISTENER or EDITOR) |
| `INVITE_ACCEPTED` | the owner | someone accepts their invite | |
| `TRACKS_ADDED` | the owner and other members | someone else adds songs | `count`: songs added |

A `PLAYLIST_INVITE` should drop out of the list once the invite is accepted, declined or revoked, so the bell never
shows Accept on an invite that's gone. `unreadCount` counts all unread, not just this page (the bell's red number).

### POST /api/notifications/read

Marks notifications read when the bell is opened. Body: `{ "notificationIds": ["n_7Kq2", "…"] }`, or `{}` for all of
the caller's notifications. Ids that aren't the caller's are ignored.

Response `204`, no body.

## 3. Delete account

### DELETE /api/users/me

Mocked: `ACCOUNT_MOCKED` in `src/utils/account.js` (only `deleteAccount` still uses it). Used by Settings → Account →
Delete your account, after a 6-second confirmation.

Deletes the caller's account and wipes their data: profile details, picture and banner, playlists (as in request 1),
memberships and invites, uploaded MP3s including the files in R2, favorites, follows and play history. The token stops
working afterwards.

Response `204`, no body.

Questions:
- Should it require the current password, like email and password changes now do? If so, the body would be
  `{ "currentPassword": "…" }` and a wrong one a `403 WRONG_PASSWORD` (not 401: the frontend logs out on 401).
- What happens to the caller's songs that sit in other people's playlists: removed from them, or kept?

## 4. Dashboard

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

### Changes to existing endpoints

- `GET /api/users/recent-listening` and `GET /api/users/top-artists`: take an optional `limit` (1-20, default 4). Each
  now has its own dashboard tab, and 4 items look sparse there.
- `GET /api/users/recent-listening`: add the playlist owner's username next to `playlistId` (for example
  `playlistOwnerUsername`, null when `playlistId` is null). Playlist page URLs need the owner's username, so recent
  songs can't link to the playlist they were played from yet.

## 5. Smaller fixes

- **Search response has examples but no schema.** `GET /api/core/search` documents its `200` body only through one
  example per category, so the docs' schema view doesn't list the fields, their types or which can be null
  (`UserSearchResult`, `MusicPieceSearchResult` and `PlaylistSearchResult` exist in code). Adding the schema would help;
  the frontend uses this for dashboard search and for finding people to invite.
- **Nice to have: invite details on 403.** When `GET /api/playlists/{username}/{playlistId}` returns
  `403 PLAYLIST_INVITE_PENDING`, the page makes a second call to `GET /api/playlists/invites` just to show the playlist's
  name, cover, who invited them and the permission. Including those in the error body (or a `GET
  /api/playlists/{playlistId}/invite`) would save that call.

## Open questions

- Should a password change end the user's other sessions? Tokens are stateless JWTs, so they stay valid until they
  expire.
- Should editors be able to reorder songs? If so, something like `PUT /api/playlists/{playlistId}/order` with
  `{ "musicPieceIds": [...] }` in the new order. Removing a song leaves a gap in `playlist_position` (cursors are
  positions, so renumbering would make a client holding one skip a song); a reorder would need its own answer for
  cursors handed out before it.
