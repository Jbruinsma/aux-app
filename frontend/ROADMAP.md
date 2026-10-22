# Frontend roadmap

Work top to bottom: finish P0 before picking up P1. Tick each box as you finish it. When every box in a task is
ticked, delete the task (git history keeps it). Exact request and response shapes are in the OpenAPI docs
(`../backend/docs/index.html`); check them before wiring up an endpoint. If something the frontend needs isn't there,
add it to `../backend/BACKEND_REQUESTS.md`.

Last updated 2026-10-22.

______________________________________________________

## P0: Do now

### 1. Load and save play preferences from the server

Shuffle and repeat move from localStorage to the server so they follow the user to every device.

**Files:** `src/stores/music.js`, `src/App.vue`
**API:** `GET /api/users/me/last-playback`, `PUT /api/users/me/play-preferences`

- [ ] Read `playPreferences` from the `last-playback` response and apply it to the player.
- [ ] Apply the preferences *after* `reset()` in `restoreLastPlayback`, since `reset()` turns shuffle and repeat off.
- [ ] Apply the preferences when `musicPiece` is null too. `last-playback` now always returns 200, and
      `restoreLastPlayback` returns early when there's no piece.
- [ ] Replace the `repeatOn` boolean with a repeat mode: `OFF`, `PLAYLIST` or `PIECE`.
- [ ] Make the repeat button cycle `OFF` → `PLAYLIST` → `PIECE` → `OFF`, with a distinct icon for each.
- [ ] Keep today's repeat behaviour (`startRepeat`) as `PIECE`.
- [ ] Build `PLAYLIST`: when the last piece ends and `nextCursor` is null, call `/play` again with the current shuffle
      setting. For a single piece, play it again.
- [ ] Send `PUT /api/users/me/play-preferences` on every shuffle or repeat change. Update the player straight away; don't
      wait for the response.
- [ ] Remove shuffle and repeat from the `aux-player-settings-{id}` localStorage key. Keep volume there.

**Done when:** shuffle and repeat set on one device are already set after logging in on another, and volume is not.

### 2. Add the live event store

One connection to the server that keeps the header and the Dashboard up to date without polling.

**Files:** new `src/stores/events.js`, `src/App.vue`
**API:** `GET /api/users/me/events`

- [ ] Create a Pinia store that opens the stream after login and closes it on logout.
- [ ] Read the stream with `fetch` and `response.body.getReader()`. Do **not** use `EventSource`: it can't send the
      token, and the token must never go in the URL.
- [ ] Keep one field in the store per event (`invites`, `recents`, `top-artists`, `library`, `uploads`,
      `notifications`). Replace the whole field each time its event arrives.
- [ ] Ignore keep-alive lines that start with `:`.
- [ ] Reconnect when the stream drops, backing off to about 30 seconds on repeated failures.
- [ ] On a 401 when reconnecting, log the user out the same way `src/utils/api.js` does.

**Done when:** the stream stays connected across page changes, recovers after the network drops, and closes on logout.

### 3. Move the Dashboard onto the event store

The backend has removed `GET /api/users/recent-listening`, `/top-artists` and `/library`, so the Recent listening,
Top artists and Library rows fail until this ships.

**Files:** `src/views/Dashboard.vue`
**Needs:** task 2

- [ ] Replace the `recent-listening`, `top-artists`, `library` and uploads fetches in `onMounted` with the store.
- [ ] Show each row as loading until its event arrives, and as an error if the stream can't connect.
- [ ] `recents` and `top-artists` now carry up to 20 items. Show the first 4 in the Dashboard rows and all of them on
      each one's own tab.
- [ ] Keep `GET /api/music-pieces` in `AddMusic.vue`; that one isn't going away.

**Done when:** Dashboard makes none of those four REST calls, and a new play shows up in Recent listening without a
reload.

### 4. Move the notification bell onto the event store

**Files:** `src/components/NotificationBell.vue`, `src/components/AppHeader.vue`, `src/views/PlaylistDetail.vue`
**Needs:** task 2
**API:** `GET /api/notifications`, `PUT /api/notifications/read-all`, `PUT /api/notifications/read`

- [ ] Show the `notifications` event in the bell, and load older pages with `GET /api/notifications?cursor=`.
- [ ] Handle each type: `PLAYLIST_INVITE` (with Accept and Decline), `INVITE_ACCEPTED` and `TRACKS_ADDED`.
- [ ] Link each notification to its playlist using `playlistOwnerUsername`, **not** `actor.username`.
- [ ] Show `unreadCount` on the badge, and mark notifications read when the bell opens.
- [ ] Remove the 60-second polling, the refresh on focus and the localStorage list of seen invites.
- [ ] On the `403 PLAYLIST_INVITE_PENDING` page in `PlaylistDetail.vue`, read the invite from the store's `invites`
      field instead of calling `GET /api/playlists/invites` again.

**Done when:** a new invite appears in the bell within a second, with no polling in the network tab.

## P1: Do next

### 5. Turn on real playlist deletion

The endpoint is live; the UI still runs on a mock.

**Files:** `src/utils/playlist.js`, `src/views/PlaylistDetail.vue`

- [ ] Delete `PLAYLIST_DELETE_MOCKED` and the mock branch in `deletePlaylist`.
- [ ] Remove the "Preview only" notice in `confirmDelete`.
- [ ] On 404, treat the playlist as already deleted: close the dialog, reset the player if it was playing that
      playlist, and go to the Dashboard.
- [ ] Check what other users see after a delete: members and savers get the not-found page, a player on that playlist
      stops instead of retrying, and the invite leaves the bell.

### 6. Skip private pieces

**Files:** `src/stores/music.js`

- [ ] When `/stream` returns 404 `MUSIC_PIECE_NOT_FOUND`, skip to the next piece. The notification already shows.
      The `/play` queue now leaves out pieces the caller can't play, so this only happens when a piece becomes
      unavailable mid-session (see task 12).

### 7. Play Recently played cards on click

**Files:** `src/views/Dashboard.vue`

- [ ] On click, call `playPlaylist(playlistId, { start: musicPieceId, shuffle: shuffleOn })`.
- [ ] When `playlistId` is null, play the piece on its own with `playPieces([musicPiece])`.
- [ ] When the queue call fails with 404 or 403, fall back to playing the piece on its own.

### 8. Add Leave playlist

**Files:** `src/views/PlaylistDetail.vue`
**API:** `DELETE /api/playlists/{playlistId}/membership`

- [ ] Show the PlaylistDetail menu to listeners, not only editors.
- [ ] Add a Leave playlist item for editors and listeners.

### 9. Finish the empty states

**Files:** `src/views/Dashboard.vue`

- [ ] Go through every Dashboard section with a brand-new account; none may show a blank area.
- [ ] Add a next step where one fits, such as Create playlist or Upload music.
- [ ] Give the loading and error states the same `EmptyState` look; they're plain text now.

### 10. Restyle My uploads

**Files:** `src/views/Dashboard.vue`

- [ ] Use the same cover cards and drag-to-scroll row as Recent listening.

### 11. Show who uploaded each piece

Every music piece the backend returns now has an `uploader` field: `{ userId, pfpUrl, username }`, the same shape as a
playlist's owner. It's on `last-playback`, the `/play` queue, `/pieces` pages, the playlist overview, the `recents` and
`uploads` events, `GET /api/music-pieces` and the upload response. Nothing breaks without this task; the field just
goes unused.

**Files:** `src/stores/music.js`, `src/components/BottomPlayer.vue`, `src/views/PlaylistDetail.vue`,
`src/views/Dashboard.vue`, `src/utils/dashboardPreview.js`

- [ ] Copy `uploader` onto the current piece in `toCurrent` in `src/stores/music.js`.
- [ ] Show the uploader's picture and username in `BottomPlayer.vue`, linked to their profile (`/:username`).
- [ ] Show the uploader on each track row in `PlaylistDetail.vue`. Shared playlists mix uploads from several members.
- [ ] Show the uploader on the Recent listening cards in `Dashboard.vue`.
- [ ] Skip it on My uploads and in `AddMusic.vue`: the uploader is always the caller there.
- [ ] Load `pfpUrl` through `resolveCoverURL`, like the other profile pictures. It can be a relative path such as
      `/default_profile_picture.svg`.
- [ ] Add `uploader` to the mock pieces in `src/utils/dashboardPreview.js` and in the `music`, `AddMusic` and
      `PlaylistDetail` tests.

**Done when:** the player and playlist track rows show who uploaded the piece, and clicking the name opens their
profile.

### 12. Gray out unavailable pieces

Private pieces can now be shared inside private playlists. A private piece is playable by its uploader, and by
everyone on a private playlist while its uploader is still the owner or an accepted member. When the uploader is
removed or leaves, or the playlist goes public, the piece stays in the playlist but becomes unavailable.
Every music piece now has `isAvailable`. It is only ever false in a playlist's track list (the overview and
`/pieces`); the `/play` queue, `last-playback` and `recents` leave unavailable pieces out.

**Files:** `src/views/PlaylistDetail.vue`, `src/stores/music.js`

- [ ] Gray out rows where `isAvailable` is false and label them "UNAVAILABLE". Keep the title, cover and uploader.
- [ ] Don't let an unavailable row start playback.
- [ ] Keep the remove-track action on unavailable rows for the owner and editors; the endpoint allows it.
- [ ] When starting playback from a row, pass that row's piece as `start` only if it's available.
- [ ] Add `isAvailable: true` to the mock pieces in `src/utils/dashboardPreview.js` and the tests.

**Done when:** after an editor is removed from a private playlist, their private pieces show grayed out to everyone
else, can't be played, and the owner can remove them.

### 13. Edit music pieces

**Files:** `src/views/Dashboard.vue` (My uploads), new edit dialog
**API:** `PUT /api/music-pieces/{musicPieceId}` (multipart: `name`, `artistId`, `isPublic`, `coverImage`, all optional)

- [ ] Add an Edit action to each piece in My uploads, opening a dialog with name, artist (reuse `ArtistPicker.vue`),
      public/private and cover.
- [ ] Send only the fields that changed. Send the original cover image; the server crops it.
- [ ] When switching a piece to private, warn that it becomes unavailable on public playlists and on playlists
      the uploader no longer collaborates on.
- [ ] The response is the updated piece; the `uploads` event also refreshes the row.

**Done when:** an uploader can rename, re-artist, re-cover and change the visibility of a piece from My uploads.

## P2: Later

### 14. Build the artist page

**API:** `GET /api/artists/{artistId}`, `PUT`/`DELETE .../favorite`, `PUT .../pfp`, `PUT .../banner`,
`GET /api/artists/favorites/{username}`

- [ ] Artist page with a Favorite/Unfavorite button.
- [ ] Picture and banner editing when `canEdit` is true.
- [ ] Link artist names in track rows, search results and Top artists to the page.
- [ ] Show favourite artists on the profile.

### 15. Autoplay (not decided)

- [ ] Decide whether to play recommended music when the queue ends with repeat `OFF`, like Apple Music's infinity
      button. The backend has nothing for it yet.

## Blocked: waiting on the backend

Don't start these until the endpoint is in the OpenAPI docs.

- [ ] **Delete account.** Turn off `ACCOUNT_MOCKED` in `src/utils/account.js` once `DELETE /api/users/me` exists
      (`../backend/BACKEND_REQUESTS.md` §1).
- [ ] **People who hear it too.** Fill the Dashboard section once `GET /api/users/matches` exists
      (`../backend/BACKEND_REQUESTS.md` §2).
- [ ] **Follow.** Enable the profile's follow button once there's a follow endpoint. Not requested yet.
- [ ] **Landing page.** Replace the hardcoded top albums in `LandingPage.vue` once `GET /api/core/top` returns real
      data. Not requested yet.
- [ ] **Music piece page.** Open a details page with a Play button when an upload is clicked, once
      `GET /api/music-pieces/{musicPieceId}` exists. Not requested yet.
