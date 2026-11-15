# Frontend roadmap

Work top to bottom. Only unfinished work is listed: delete each item as you finish it, and delete a task once none of
its items are left. Git history keeps what's done. Exact request and response shapes are in the OpenAPI docs
(`../backend/docs/index.html`); check them before wiring up an endpoint. If something the frontend needs isn't there,
add it to `../backend/BACKEND_REQUESTS.md`.

Last updated 2026-11-15.

______________________________________________________

## P1: Do next

### 9. Finish the empty states

**Files:** `src/views/Dashboard.vue`

- [ ] Go through every Dashboard section with a brand-new account; none may show a blank area.

### 28. Follower and following lists

**Files:** new `src/components/FollowListDialog.vue`, `src/views/PublicProfile.vue`, `src/utils/follows.js`
**API:** `GET /api/users/{username}/followers`, `.../following` (not in the docs yet: "Follower and following lists" in `../backend/BACKEND_REQUESTS.md`)

- [ ] Turn off `FOLLOWS_MOCKED` in `src/utils/follows.js` once the list endpoints are in the docs.

### 29. Who can see your follows

**Files:** `src/views/Settings.vue`, `src/utils/follows.js`
**API:** `PUT /api/users/me/follows-visibility`, `followsVisibility` on `GET /api/users/settings` ("Who can see your follows")

- [ ] Turn off `FOLLOWS_MOCKED` (the choice is kept in this browser until then).

### 30. Follow notifications

**Files:** `src/components/NotificationBell.vue`
**API:** `NEW_FOLLOWER` on `GET /api/notifications` and the `notifications` event ("Follow notifications")

- [ ] Check it against real notifications once the backend sends `NEW_FOLLOWER`.

### 31. Artists tab

**Files:** new `src/views/ArtistsPage.vue`, new `src/components/ArtistGrid.vue`, `src/components/AppHeader.vue`,
`src/router/index.js`, `src/views/UploadMusic.vue`, `src/views/ArtistPage.vue`
**API:** `GET /api/artists?q=`, `GET /api/artists/favorites/{username}`; listing every artist needs "Every artist, paged"

- [ ] List every artist when nothing is typed: turn off `ARTIST_DIRECTORY_MOCKED` in `src/utils/artist.js` once
      `GET /api/artists/all` is in the docs.

### 32. Uploads on profiles

**Files:** `src/views/PublicProfile.vue`, `src/utils/music.js`
**API:** `GET /api/music-pieces` (yours), `GET /api/users/{username}/music-pieces` (anyone's, "Another user's uploads")

- [ ] Other people's profiles: turn off `USER_UPLOADS_MOCKED` in `src/utils/music.js` once the endpoint is in the docs.

### 33. Stream counts on every song

**Files:** `src/components/TrackRows.vue` (nothing to change)
**API:** `playCount` on `MusicPieceOverview` in lists ("Play counts on song lists")

- [ ] Check the counts show on the artist page, profile uploads, playlists and search once the backend adds
      `playCount` to song lists.

### 34. Build the Friends page

Replaces the Coming soon page at `/friends`. Friends are people you follow who follow you back. No mock yet: check the
style guide and ask before designing it.

**Files:** new `src/views/FriendsPage.vue`, new `src/utils/friends.js`, `src/router/index.js`
**API:** `GET /api/users/me/friends`

- [ ] List friends with their picture and username, each linked to their profile.
- [ ] Show what each friend is playing now, or else their last played song and when; clicking a song plays it.
- [ ] Mark who's online. The backend already sorts online friends first, then by most recent listen.
- [ ] Page with `limit` and `offset`, using `totalResults` to know when to stop.
- [ ] Empty state for no friends yet, pointing to profiles to follow people back.

**Done when:** a friend playing a public song shows it as playing now, and it clears when they pause.

### 38. Albums

Anyone signed in can add an album to an artist. The album's creator and the artist's creator can rename it, change its
cover or delete it. A piece in an album shows the album's cover unless its uploader picks their own. No mock yet: check
the style guide and ask before designing the new screens.

**Files:** new `src/utils/album.js`, new album view and route, `src/views/ArtistPage.vue`, `src/views/UploadMusic.vue`,
`src/components/EditPieceDialog.vue`, `src/views/PublicProfile.vue`, `src/router/index.js`
**API:** `GET`/`POST /api/artists/{artistId}/albums`, `GET`/`PUT`/`DELETE /api/albums/{albumId}`,
`GET /api/albums/{albumId}/music-pieces`, `GET /api/albums/top/{username}`; `albumId` on upload, and `albumId`,
`removeFromAlbum` and `useAlbumCover` on `PUT /api/music-pieces/{musicPieceId}`

- [ ] Artist page: an Albums section of album cards above the songs. Hide it when the list is empty.
- [ ] Album page: cover, name, artist, who created it, and its songs in album order (`arrangeAlbum` in
      `src/utils/albumOrder.js`, with its disc dividers), with Play album.
- [ ] Album page: Edit (rename, new cover) and Delete for editors (`canEdit`). Say that deleting keeps the songs as
      singles, and that a new cover changes it for every song using it, including other people's.
- [ ] Show `404 ALBUM_NOT_FOUND` as a not-found state. Albums with no song you can play are hidden this way.
- [ ] Edit piece dialog: change or remove the album, and switch between the album's cover and an uploaded one.
- [ ] Link the album name on song rows that carry `album`.
- [ ] Profile: replace the Top albums placeholder with `GET /api/albums/top/{username}`.

**Done when:** a song uploaded into an album shows the album's cover, changing the album's cover updates it, and
deleting the album leaves the song with the same cover.

### 39. Album track order

Album songs play in their tracklist order, with "Disc 1" / "Disc 2" dividers. For now each album is looked up in
Apple's public catalog from the viewer's browser; Apple's editions don't always match Spotify's (its "good kid,
m.A.A.d city" deluxe is one disc).

**Files:** `src/utils/albumOrder.js`, `src/views/ArtistPage.vue`, `src/components/AlbumDialog.vue`
**API:** `discNumber` and `trackNumber` on `MusicPieceOverview`, `PUT /api/albums/{albumId}/track-order` ("Album track
order")

- [ ] Once pieces carry `trackNumber`, check albums sort by it, then delete the catalog lookup
      (`fetchAlbumTracklist` and `ALBUM_ORDER_FROM_CATALOG`) and keep `arrangeAlbum`.
- [ ] Album Edit dialog: drag the songs into order with `PUT /api/albums/{albumId}/track-order`.

### 40. Songs up to 13 minutes

**Files:** `src/utils/music.js`, `src/utils/youtube.js`, `src/utils/music.test.js`
**API:** the upload and YouTube import limits ("Songs up to 13 minutes")

- [ ] Once the backend allows it, set `MAX_SECONDS` to 13 minutes and `MAX_MP3_BYTES` to 32MB, and change the "10
      minutes" and "25MB" messages.

## P2: Later

### 16. Autoplay (not decided)

- [ ] Decide whether to play recommended music when the queue ends with repeat `OFF`, like Apple Music's infinity
      button. The backend has nothing for it yet. Options are in `../backend/BACKEND_REQUESTS.md` (Open questions).

## Blocked: waiting on the backend

Don't start these until the endpoint is in the OpenAPI docs.

- **Tasks 28 and 29:** follower and following lists, `canViewFollows`, and `followsVisibility`.
- **Task 30:** the `NEW_FOLLOWER` notification type.
- **Task 31:** a paged list of every artist.
- **Task 32:** `GET /api/users/{username}/music-pieces`.
- **Task 33:** `playCount` on song lists.
- **Task 39:** `discNumber`/`trackNumber` and the track order endpoint.
- **Task 40:** the 13 minute and 32MB upload limits.

All are in `../backend/BACKEND_REQUESTS.md`.
