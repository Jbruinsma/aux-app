# Backend requests

What the frontend needs that isn't in `docs/openapi.json` yet, most needed first. Where a page already uses an
endpoint before it exists, it's mocked in the frontend and the flag to switch it off is noted. All endpoints below need
`Authorization: Bearer <token>` unless marked public, and errors use the usual `AuxServerError` body.

The Dashboard and the header get their data from the live event stream (`GET /api/users/me/events`), so changes to
that data are asked for on the event, not on the REST endpoint it mirrors.

Last checked against `main` on 2026-11-20.

Sections are referred to by title, not number, from the frontend's code and `ROADMAP.md`, so they stay findable when
finished ones are removed. These were built on the frontend (Mo, 2026-11-13) against mocked data and are listed most
visible first; the frontend switches each one on by turning off the flag named in it.

______________________________________________________

## Who can see your follows (`followsVisibility`)

**Frontend:** Settings → Profile → "Who can see your followers", `src/utils/follows.js` (`FOLLOWS_VISIBILITY_MOCKED`;
the choice is kept in the browser until this exists), `FollowListDialog.vue`, `PublicProfile.vue`. Roadmap task 29.

The follower and following lists (`GET /api/users/{username}/followers` and `.../following`) are live, but anyone can
open anyone's. They should return `403 FOLLOWS_HIDDEN` when the user's follows visibility leaves the caller out; the
user themselves can always see their own lists.

### PUT /api/users/me/follows-visibility

```json
{ "followsVisibility": "FRIENDS" }
```

Returns `{ "followsVisibility": "FRIENDS" }`. Values: `EVERYONE` (default), `FRIENDS` (people who follow each other
with the user), `NO_ONE` (only the user). `400 INVALID_FIELD` on anything else. Also add `followsVisibility` to
`GET /api/users/settings`. It decides `canViewFollows` and `403 FOLLOWS_HIDDEN`.

### `canViewFollows` on GET /api/users/profile/{username}

A boolean: whether the caller may open those lists. The profile shows the counts either way, but only makes them
clickable when this is true. The frontend treats a missing field as true.

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

## Album track order (`discNumber`, `trackNumber`)

**Frontend:** `ArtistPage.vue`, `src/utils/albumOrder.js` (`ALBUM_ORDER_FROM_CATALOG`). Roadmap task 39.

Today `GET /api/albums/{albumId}/music-pieces` is most played first, so in practice an album plays in upload order. It
should play in the album's real tracklist order, with "Disc 1" / "Disc 2" dividers when the album has more than one
disc. Until this exists the frontend looks every album up in Apple's public catalog from each viewer's browser and
sorts the songs itself; it switches that off on its own as soon as pieces carry `trackNumber`.

### `discNumber` and `trackNumber` on MusicPieceOverview

Both integers, 1-based, null when unknown (and always null on a single). Add them wherever `MusicPieceOverview` is
returned. `GET /api/albums/{albumId}/music-pieces` sorts by `discNumber`, then `trackNumber`, nulls last, then the
order it uses today. The frontend shows a divider per disc when the album's songs are on more than one.

### How the numbers get filled

1. **From the MP3's tags at upload.** `UploadService` already reads the ID3v2 header before it strips the tags. Read
   `TRCK` (`"3"` or `"3/12"`) and `TPOS` (`"1"` or `"1/2"`) there and keep the first number of each. Only when the
   piece goes on an album; a missing `TPOS` with a `TRCK` means disc 1.
2. **From Apple's catalog when the tags don't have them** (YouTube imports never do, and many MP3s don't). Apple's
   iTunes Search API is public, needs no key, and is what the frontend uses now:
   - `GET https://itunes.apple.com/search?term=<artist>&entity=musicArtist&limit=5` finds the artist's `artistId`.
   - `GET https://itunes.apple.com/lookup?id=<artistId>&entity=album&limit=200` lists the artist's albums
     (`collectionId`, `collectionName`, `trackCount`, `collectionExplicitness`). Searching for the album by name
     directly misses some ("Blonde", "Currents" come back as singles and remixes only), so use it only as a fallback.
   - `GET https://itunes.apple.com/lookup?id=<collectionId>&entity=song` lists its songs with `discNumber`,
     `trackNumber` and `trackName`.
   - Apple allows about 20 calls a minute per IP, so do it once per album (three calls) when a song is added to it (or in the
     background), store the numbers, and don't look the album up again. The matching rules for titles and editions
     are in `src/utils/albumOrder.js` and can be ported as they are.
   - Apple's editions don't always match Spotify's: Apple's "good kid, m.A.A.d city (Deluxe Version)" is one disc of
     13 songs, while Spotify's deluxe has a second disc. The editor's order (next section) fixes those.
3. **Optional `trackNumber` and `discNumber` fields** on `POST /api/music-pieces/create` and `PUT
   /api/music-pieces/{id}` (multipart, like the other fields) override 1 and 2. Track 1-99, disc 1-20,
   `400 INVALID_FIELD` otherwise. Moving a piece to another album or making it a single clears both unless new ones
   are sent with the move.

### PUT /api/albums/{albumId}/track-order

For the album's editors (`canEdit`), including songs other people uploaded to the album: the frontend will let them
drag the songs into order in the album's Edit dialog.

```json
{ "tracks": [ { "musicPieceId": "m_92kd0Z", "discNumber": 1, "trackNumber": 1 } ] }
```

Returns the album's `{ "musicPieces": [MusicPieceOverview] }` in the new order. Pieces left out keep their numbers.
`403 NOT_ALBUM_EDITOR`, `404 ALBUM_NOT_FOUND`, `400 INVALID_FIELD` for a piece that isn't on the album, the same
disc and track twice, or numbers out of range.

______________________________________________________

## Songs up to 13 minutes

**Frontend:** `src/utils/music.js` (`MAX_SECONDS`, `MAX_MP3_BYTES`), `src/utils/youtube.js`. Roadmap task 40.

Raise the longest song from 10 to 13 minutes, for MP3 uploads and YouTube imports. "Sing About Me, I'm Dying of
Thirst" (12:03) is the song that doesn't fit today. The frontend checks the same limits before uploading, so it
changes its numbers and messages once this is live; until then it keeps rejecting songs over 10 minutes.

- `UploadService.MAX_TRACK_SECONDS`: `10 * 60` → `13 * 60`, and its message "Track must be at most 10 minutes".
- `YoutubeImportService`: the same message on its duration check (it already uses `MAX_TRACK_SECONDS`).
- **The 25MB file cap has to go up too, to 32MB.** A 320 kbps MP3 is about 2.4MB a minute, so 13 minutes is about
  31MB and anything past about 10½ minutes is already over 25MB. That's `spring.servlet.multipart.max-file-size` in
  `application.properties` and `YoutubeImportService.MAX_MP3_BYTES` (`--audio-quality 0` comes out near 25MB at 13
  minutes). The 2GB storage quota stays as it is.
- The play URL lasts an hour (`UploadService.SIGNED_URL_TTL`), which still covers 13 minutes; only its comment says 10.
- Docs that say 10 minutes or 25MB: `MusicPieceCreationDetails` (`mp3File`), `MusicPieceController` (create's
  description and its 413), `YoutubeImportController` (start import). The image 413s on the artist and playlist
  endpoints mention the 25MB request cap as well.

______________________________________________________

## Song titles and album names aren't masked

**Frontend:** nothing to change.

Song titles and album names should be stored exactly as typed, because real titles contain words the filter masks.
Remove `ProfanityFilter.mask(...)` from these four, keeping the trim and blank handling around it:

- `MusicPieceCreationDetails` (`name`)
- `MusicPieceDetailsUpdate` (`name`)
- `AlbumCreationDetails` (`albumName`)
- `AlbumDetailsUpdate` (`albumName`)

Also drop "Profanity is masked" from their `@Schema` descriptions, and "masks profanity" from the comment on
`YoutubeImportService.cleanTitle`. Everything else stays filtered: usernames, playlist names, artist names and tags.
Names already saved stay masked; their uploaders can rename them from the song's Edit dialog, and album editors from
the album's.

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
