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

**Most needed.** Until this exists the frontend can only show the caller's own lists, filled from
`GET /api/users/me/friends` (people who follow each other), and other people's counts can't be opened. Every row needs
a real `pfpUrl` (null only when the user has no picture) and the current `username`, because each row links to
`/{username}`: a stale or wrong username lands on "This user isn't on Aux".

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

## Albums

**Frontend:** the artist page (`ArtistPage.vue`, `AlbumDialog.vue`), `src/utils/albums.js` (`ALBUMS_MOCKED`; albums are
kept in the browser, and the album cover only shows on the artist page, until this exists). Roadmap task 38.

An artist's songs can be grouped into albums. Each album has a name, a square cover and an ordered track list; a song
is on at most one album, and songs on none are singles. Each track either uses the album cover or keeps its own.
Anyone signed in can make an album for any artist (artists are community-made too); only its creator can edit or
delete it.

### GET /api/artists/{artistId}/albums

Public; a token is optional. Oldest album first. Tracks the caller can't play on their own (someone else's private
song) are left out.

```json
{
  "albums": [
    {
      "albumId": "al_4kD9x",
      "name": "Blonde",
      "coverUrl": "https://…/album-cover/….webp",
      "canEdit": true,
      "tracks": [
        { "musicPiece": { "musicPieceId": "m_92kd0Z", "name": "Nights", "coverUrl": "…", "artistSummary": { … }, "uploader": { … }, "…": "the rest of MusicPieceOverview" }, "useAlbumCover": true }
      ]
    }
  ]
}
```

`canEdit` is true for the album's creator. `404 ARTIST_NOT_FOUND`.

### POST /api/artists/{artistId}/albums

Multipart, signed in:

- `name` (required): 1 to 100 chars, trimmed.
- `coverImage` (required): same rules as a song cover; the server crops it to a square WebP.
- `musicPieceIds` (required, repeat the field): 1 or more, in track order. Each must be credited to this artist and
  playable by the caller. A song already on another album moves to this one.
- `ownCoverIds` (repeat the field): the subset of `musicPieceIds` that keep their own cover. The rest use the album
  cover.

Returns the album, as in the list above. `400 INVALID_FIELD` (with `parameter`) for a bad name, an empty track list, or
a song credited to another artist; the usual image errors for the cover; `404 MUSIC_PIECE_NOT_FOUND`.

### PUT /api/albums/{albumId}

Creator only (`403 NOT_ALBUM_CREATOR`). Same fields as POST, all optional: a missing `coverImage` keeps the cover, and
sending `musicPieceIds` replaces the whole track list (with `ownCoverIds` read against it). Returns the album.
`404 ALBUM_NOT_FOUND`.

### DELETE /api/albums/{albumId}

Creator only. `204`; its songs become singles. `404 ALBUM_NOT_FOUND`.

### `albumId` on POST /api/music-pieces/create

Upload straight onto an album (the Upload page's Album field, and an album's "Add a song" on the artist page):

- `albumId` (optional): an album of the piece's `artistId`; the new piece goes at the end of its tracks.
  `404 ALBUM_NOT_FOUND`, `400 INVALID_FIELD` if the album belongs to another artist.
- `useAlbumCover` (optional, default `true` when `albumId` is sent): use the album cover. With it, `coverImage` is no
  longer required; if both are sent, keep `coverImage` as the piece's own cover so it can be switched back.

Anyone uploading a song credited to the album's artist can add it this way, not only the album's creator. Until this
exists the frontend sends the album cover as `coverImage` and adds the track in the browser.

### `album` on MusicPieceOverview, and the album cover everywhere

- Add `album: { albumId, name } | null` to `MusicPieceOverview`, so the frontend can say which album a song is on.
- When a track uses the album cover, send the album's `coverUrl` as the piece's `coverUrl` everywhere songs appear
  (player, playlists, search, recents, events), so the cover follows the song without the frontend looking it up. The
  piece's own cover should be kept, not overwritten, so turning `useAlbumCover` off brings it back.

Question: should the uploader of a song be able to take it off someone else's album, or to stop it using that
album's cover? The frontend currently only lets the album's creator change either.

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
