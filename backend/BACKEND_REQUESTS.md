# Backend requests

What the frontend needs that isn't in `docs/openapi.json` yet, most needed first. Where a page already uses an
endpoint before it exists, it's mocked in the frontend and the flag to switch it off is noted. All endpoints below need
`Authorization: Bearer <token>` unless marked public, and errors use the usual `AuxServerError` body.

The Dashboard and the header get their data from the live event stream (`GET /api/users/me/events`), so changes to
that data are asked for on the event, not on the REST endpoint it mirrors.

Last checked against `main` on 2026-11-14.

Sections are referred to by title, not number, from the frontend's code and `ROADMAP.md`, so they stay findable when
finished ones are removed. These were built on the frontend (Mo, 2026-11-13) against mocked data and are listed most
visible first; the frontend switches each one on by turning off the flag named in it.

______________________________________________________

## Follower and following lists

**Frontend:** `FollowListDialog.vue`, `PublicProfile.vue`, `src/utils/follows.js` (`FOLLOWS_MOCKED`). Roadmap task 28.

### GET /api/users/{username}/followers?cursor=
### GET /api/users/{username}/following?cursor=

Public-ish: a token is optional, but `isFollowing`/`followsMe` need one. Newest follow first, 50 per page, with the
same opaque cursor as the other paged lists.

```json
{
  "users": [
    { "userId": "u_8f2kD1", "username": "arjay", "pfpUrl": "https://…/pfp/….webp", "isFollowing": true, "followsMe": false }
  ],
  "nextCursor": "…"
}
```

- `isFollowing`: the caller follows this user. `followsMe`: this user follows the caller. Both false without a token.
  The frontend shows "Friends" when both are true.
- `403 FOLLOWS_HIDDEN` when the user's follows visibility (next section) leaves the caller out. The user themselves can
  always see their own lists.
- `404 USER_NOT_FOUND` for an unknown username.

### `canViewFollows` on GET /api/users/profile/{username}

A boolean: whether the caller may open those lists. The profile shows the counts either way, but only makes them
clickable when this is true. The frontend treats a missing field as true.

______________________________________________________

## Who can see your follows (`followsVisibility`)

**Frontend:** Settings → Profile → "Who can see your followers", `src/utils/follows.js` (`FOLLOWS_MOCKED`; the choice
is kept in the browser until this exists). Roadmap task 29.

### PUT /api/users/me/follows-visibility

```json
{ "followsVisibility": "FRIENDS" }
```

Returns `{ "followsVisibility": "FRIENDS" }`. Values: `EVERYONE` (default), `FRIENDS` (people who follow each other
with the user), `NO_ONE` (only the user). `400 INVALID_FIELD` on anything else. Also add `followsVisibility` to
`GET /api/users/settings`. It decides `canViewFollows` and `403 FOLLOWS_HIDDEN` above.

______________________________________________________

## Follow notifications (`NEW_FOLLOWER`)

**Frontend:** `NotificationBell.vue` already renders it. Roadmap task 30.

A new `type` on `NotificationResponse` (`GET /api/notifications` and the `notifications` event), sent to the user who
was followed:

```json
{
  "notificationId": "n_3kD9",
  "type": "NEW_FOLLOWER",
  "actor": { "userId": "u_8f2kD1", "username": "arjay", "pfpUrl": null },
  "followingBack": false,
  "playlist": null,
  "playlistOwnerUsername": null,
  "isRead": false,
  "createdAt": "2026-11-14T15:04:05Z"
}
```

- `followingBack`: whether the recipient already follows the actor (so they're now friends). Only on `NEW_FOLLOWER`.
- `playlist` and `playlistOwnerUsername` are null for this type; today they're always set, so their schemas need to
  allow null.
- Following, unfollowing and following again shouldn't stack up notifications: one per follower, moved to the top
  (and marked unread) when they follow again.

______________________________________________________

## Another user's uploads

**Frontend:** `PublicProfile.vue`, `src/utils/music.js` (`USER_UPLOADS_MOCKED`). Roadmap task 32.

### GET /api/users/{username}/music-pieces?cursor=

A `MusicPiecePage` (`{ musicPieces: [MusicPieceOverview], nextCursor }`), 50 per page, newest upload first: the
pieces the user uploaded that the caller can play. That's public ones, plus private ones when the caller is that user.
Each piece should carry `playCount` (see "Play counts on song lists"), which the profile uses to put the most played
artist and song first. `404 USER_NOT_FOUND` for an unknown username. Today only your own uploads are listable
(`GET /api/music-pieces`).

______________________________________________________

## Every artist, paged

**Frontend:** `ArtistsPage.vue`, `src/utils/artist.js` (`ARTIST_DIRECTORY_MOCKED`). Roadmap task 31.

### GET /api/artists/all?cursor=

Public. `{ "artists": [ArtistSummary], "nextCursor": "…" }`, 50 per page, sorted by name (case-insensitive). The
Artists tab shows it when nothing is typed. `GET /api/artists?q=` stays as the search: it requires `q` and stops at 20.

______________________________________________________

## Play counts on song lists

**Frontend:** `TrackRows.vue` already shows "N plays" and the play count bar when pieces carry `playCount`. Roadmap
task 33.

Add `playCount` (every counted listen, all time, as on `MusicPieceDetails`) to `MusicPieceOverview` wherever songs are
listed: `GET /api/artists/{artistId}/music-pieces`, `GET /api/users/{username}/music-pieces` (above), playlist tracks
(`GET /api/playlists/{username}/{playlistId}` and `.../pieces`) and music search results. Leave it out, or null, where
a count isn't worth the query; the frontend hides the bar when it's missing.

______________________________________________________

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
