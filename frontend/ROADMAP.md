# Frontend roadmap

Work top to bottom: finish P0 before picking up P1. Tick each box as you finish it. Keep finished tasks
in place, all boxes ticked, so the team can see what's done. Exact request and response shapes are in the OpenAPI docs
(`../backend/docs/index.html`); check them before wiring up an endpoint. If something the frontend needs isn't there,
add it to `../backend/BACKEND_REQUESTS.md`.

Last updated 2026-11-12.

______________________________________________________

## P0: Do now

### 1. Load and save play preferences from the server

Shuffle and repeat move from localStorage to the server so they follow the user to every device.

**Files:** `src/stores/music.js`, `src/App.vue`
**API:** `GET /api/users/me/last-playback`, `PUT /api/users/me/play-preferences`

- [x] Read `playPreferences` from the `last-playback` response and apply it to the player.
- [x] Apply the preferences *after* `reset()` in `restoreLastPlayback`, since `reset()` turns shuffle and repeat off.
- [x] Apply the preferences when `musicPiece` is null too. `last-playback` now always returns 200, and
      `restoreLastPlayback` returns early when there's no piece.
- [x] Replace the `repeatOn` boolean with a repeat mode: `OFF`, `PLAYLIST` or `PIECE`.
- [x] Make the repeat button cycle `OFF` → `PLAYLIST` → `PIECE` → `OFF`, with a distinct icon for each.
- [x] Keep today's repeat behaviour (`startRepeat`) as `PIECE`.
- [x] Build `PLAYLIST`: when the last piece ends and `nextCursor` is null, call `/play` again with the current shuffle
      setting. For a single piece, play it again.
- [x] Send `PUT /api/users/me/play-preferences` on every shuffle or repeat change. Update the player straight away; don't
      wait for the response.
- [x] Remove shuffle and repeat from the `aux-player-settings-{id}` localStorage key. Keep volume there.

**Done when:** shuffle and repeat set on one device are already set after logging in on another, and volume is not.

### 2. Add the live event store

One connection to the server that keeps the header and the Dashboard up to date without polling.

**Files:** new `src/stores/events.js`, `src/App.vue`
**API:** `GET /api/users/me/events`

- [x] Create a Pinia store that opens the stream after login and closes it on logout.
- [x] Read the stream with `fetch` and `response.body.getReader()`. Do **not** use `EventSource`: it can't send the
      token, and the token must never go in the URL.
- [x] Keep one field in the store per event (`invites`, `recents`, `top-artists`, `library`, `uploads`,
      `notifications`). Replace the whole field each time its event arrives.
- [x] Ignore keep-alive lines that start with `:`.
- [x] Reconnect when the stream drops, backing off to about 30 seconds on repeated failures.
- [x] On a 401 when reconnecting, log the user out the same way `src/utils/api.js` does.

**Done when:** the stream stays connected across page changes, recovers after the network drops, and closes on logout.

### 3. Move the Dashboard onto the event store

The backend has removed `GET /api/users/recent-listening`, `/top-artists` and `/library`, so the Recent listening,
Top artists and Library rows fail until this ships.

**Files:** `src/views/Dashboard.vue`
**Needs:** task 2

- [x] Replace the `recent-listening`, `top-artists`, `library` and uploads fetches in `onMounted` with the store.
- [x] Show each row as loading until its event arrives, and as an error if the stream can't connect.
- [x] `recents` and `top-artists` now carry up to 20 items. Show the first 4 in the Dashboard rows and all of them on
      each one's own tab. *(Changed by Mo: the Dashboard has no tabs now, so the drag-to-scroll rows show all 20.)*
- [x] Keep `GET /api/music-pieces` in `AddMusic.vue`; that one isn't going away.

**Done when:** Dashboard makes none of those four REST calls, and a new play shows up in Recent listening without a
reload.

### 4. Move the notification bell onto the event store

**Files:** `src/components/NotificationBell.vue`, `src/components/AppHeader.vue`, `src/views/PlaylistDetail.vue`
**Needs:** task 2
**API:** `GET /api/notifications`, `PUT /api/notifications/read-all`, `PUT /api/notifications/read`

- [x] Show the `notifications` event in the bell, and load older pages with `GET /api/notifications?cursor=`.
- [x] Handle each type: `PLAYLIST_INVITE` (with Accept and Decline), `INVITE_ACCEPTED` and `TRACKS_ADDED`.
- [x] Link each notification to its playlist using `playlistOwnerUsername`, **not** `actor.username`.
- [x] Show `unreadCount` on the badge, and mark notifications read when the bell opens.
- [x] Remove the 60-second polling, the refresh on focus and the localStorage list of seen invites.
- [x] On the `403 PLAYLIST_INVITE_PENDING` page in `PlaylistDetail.vue`, read the invite from the store's `invites`
      field instead of calling `GET /api/playlists/invites` again.

**Done when:** a new invite appears in the bell within a second, with no polling in the network tab.

## P1: Do next

### 6. Skip private pieces

**Files:** `src/stores/music.js`

- [x] When `/stream` returns 404 `MUSIC_PIECE_NOT_FOUND`, skip to the next piece. The notification already shows.
      The `/play` queue now leaves out pieces the caller can't play, so this only happens when a piece becomes
      unavailable mid-session (see task 12).

### 7. Play Recently played cards on click

**Files:** `src/views/Dashboard.vue`

- [x] On click, call `playPlaylist(playlistId, { start: musicPieceId, shuffle: shuffleOn })`.
- [x] When `playlistId` is null, play the piece on its own with `playPieces([musicPiece])`.
- [x] When the queue call fails with 404 or 403, fall back to playing the piece on its own.

### 8. Add Leave playlist

**Files:** `src/views/PlaylistDetail.vue`
**API:** `DELETE /api/playlists/{playlistId}/membership`

- [x] Show the PlaylistDetail menu to listeners, not only editors.
- [x] Add a Leave playlist item for editors and listeners.

### 9. Finish the empty states

**Files:** `src/views/Dashboard.vue`

- [ ] Go through every Dashboard section with a brand-new account; none may show a blank area.
- [x] Add a next step where one fits, such as Create playlist or Upload music.
- [x] Give the loading and error states the same `EmptyState` look; they're plain text now.

### 10. Restyle My uploads

**Files:** `src/views/Dashboard.vue`

- [x] Use the same cover cards and drag-to-scroll row as Recent listening.

### 11. Show who uploaded each piece

Every music piece the backend returns now has an `uploader` field: `{ userId, pfpUrl, username }`, the same shape as a
playlist's owner. It's on `last-playback`, the `/play` queue, `/pieces` pages, the playlist overview, the `recents` and
`uploads` events, `GET /api/music-pieces` and the upload response. Nothing breaks without this task; the field just
goes unused.

**Files:** `src/stores/music.js`, `src/components/BottomPlayer.vue`, `src/views/PlaylistDetail.vue`,
`src/views/Dashboard.vue`, `src/utils/dashboardPreview.js`

- [x] Copy `uploader` onto the current piece in `toCurrent` in `src/stores/music.js`.
- [x] Show the uploader's picture and username in `BottomPlayer.vue`, linked to their profile (`/:username`).
- [x] Show the uploader on each track row in `PlaylistDetail.vue`. Shared playlists mix uploads from several members.
- [x] Show the uploader on the Recent listening cards in `Dashboard.vue`.
- [x] Skip it on My uploads and in `AddMusic.vue`: the uploader is always the caller there.
- [x] Load `pfpUrl` through `resolveCoverURL`, like the other profile pictures. It can be a relative path such as
      `/default_profile_picture.svg`.
- [x] Add `uploader` to the mock pieces in `src/utils/dashboardPreview.js` and in the `music`, `AddMusic` and
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

- [x] Gray out rows where `isAvailable` is false and label them "UNAVAILABLE". Keep the title, cover and uploader.
- [x] Don't let an unavailable row start playback.
- [x] Keep the remove-track action on unavailable rows for the owner and editors; the endpoint allows it.
- [x] When starting playback from a row, pass that row's piece as `start` only if it's available.
- [x] Add `isAvailable: true` to the mock pieces in `src/utils/dashboardPreview.js` and the tests.

**Done when:** after an editor is removed from a private playlist, their private pieces show grayed out to everyone
else, can't be played, and the owner can remove them.

### 13. Edit music pieces

**Files:** `src/views/Dashboard.vue` (My uploads), new edit dialog
**API:** `PUT /api/music-pieces/{musicPieceId}` (multipart: `name`, `artistId`, `isPublic`, `coverImage`, `tags`, all
optional)

- [x] Add an Edit action to each piece in My uploads, opening a dialog with name, artist (reuse `ArtistPicker.vue`),
      public/private and cover.
- [x] Send only the fields that changed. Send the original cover image; the server crops it.
- [x] When switching a piece to private, warn that it becomes unavailable on public playlists and on playlists
      the uploader no longer collaborates on.
- [x] The response is the updated piece; the `uploads` event also refreshes the row.

**Done when:** an uploader can rename, re-artist, re-cover and change the visibility of a piece from My uploads.

### 14. Add tags to pieces and playlists

Pieces and playlists can now have up to 5 tags: curated genres and moods, or anything the user types. Tags are
optional, but nudge users to add them, since recommendations will use them later. Every music piece, the playlist
overview, and music and playlist search results now return `tags`, a list of lowercase strings that is empty when
there are none. Tag rules and `GET /api/tags` are in the OpenAPI docs.

**Files:** new `src/components/TagPicker.vue`, `src/views/UploadMusic.vue`, `src/views/CreatePlaylist.vue`,
`src/views/EditPlaylist.vue`, `src/views/PlaylistDetail.vue`, `src/views/Dashboard.vue`
**API:** `GET /api/tags?q=`, and the `tags` field on `POST /api/music-pieces/create`,
`PUT /api/music-pieces/{musicPieceId}`, `POST /api/playlists` and `PUT /api/playlists/{playlistId}`

- [ ] Build `TagPicker.vue` with chips and a text input. Before the user types, show suggestions from `GET /api/tags`
      with no `q`. As they type, pass the text as `q`. Let Enter add a tag that isn't suggested. Stop at 5 tags.
- [ ] Check tags the way the server does: lowercase, 2-24 letters, digits, spaces, `&` or `-`. Show a
      `400 INVALID_FIELD` on `tags` under the picker anyway, because only the server checks for profanity.
- [ ] Append one `tags` form field per tag. Never join tags with commas.
- [ ] Add the picker to UploadMusic and CreatePlaylist. Saving without tags must still work. Show a hint such as
      "Add tags to help people find this" while no tags are picked.
- [ ] Add the picker to EditPlaylist, for the owner only: an editor who sends `tags` gets
      `403 PLAYLIST_ACTION_FORBIDDEN`. Leave `tags` out when the tags didn't change. To remove every tag, send one
      empty `tags` value. The `PUT` response has no `tags`, so keep the list that was sent.
- [ ] Add the picker to the piece Edit dialog from task 13.
- [ ] Show the playlist's tags in the PlaylistDetail header, and each piece's tags in search results.
- [ ] When `tags` is empty, show an "Add tags" link on My uploads cards and on PlaylistDetail for the owner.
- [ ] Add `tags: []` to the mock pieces in `src/utils/dashboardPreview.js` and in the tests.

**Done when:** a user can tag a new upload and a new playlist, the tags show on the playlist page and in search, and
saving without tags still works.

### 17. Follow and unfollow from the profile

The follow endpoints are live, and `GET /api/users/profile/{username}` now returns `followersCount`, `followingCount`
and `uploadsCount`. The three counts are **strings**, not numbers: exact up to 999, then rounded down, such as `1.2K`,
`15.8K` or `3.4M`. `uploadsCount` only counts the pieces the caller can see.

**Files:** `src/views/PublicProfile.vue`
**API:** `PUT /api/users/{username}/follow`, `DELETE /api/users/{username}/follow`, `GET /api/users/profile/{username}`

- [ ] Enable the Follow button and remove the "Following is coming soon." hint and its `aria-describedby`.
- [ ] Follow sends `PUT`, and Following (unfollow) sends `DELETE`. Both return `204` with no body, and repeating either
      one does nothing, so a double click is safe.
- [ ] Flip `isFollowing` straight away, then load the profile again to get the new counts. Don't add or subtract 1 from
      `followersCount`: it's a string like `1.2K`. Put `isFollowing` back if the request fails.
- [ ] Handle `404 USER_NOT_FOUND` from `PUT` (the user changed their username or was deleted). `DELETE` never returns
      404.
- [ ] Show the counts as they come; don't format them again. Change the `?? 0` fallbacks in `stats` to `?? '0'` and
      remove the comment saying the counts aren't in the response yet.

**Done when:** following a user turns the button into Following and raises their follower count after the reload, and
unfollowing reverses both.

### 18. People who hear it too from the `matches` event

There won't be a `GET /api/users/matches`. The backend sends a `matches` event on the live stream instead, so the
Dashboard section stays current. The body has the same shape as the mock in `src/utils/matches.js`, plus `sharedTags`.
How the score works: `../backend/docs/compatibility.html`.

**Files:** `src/stores/events.js`, `src/views/Dashboard.vue`, `src/utils/matches.js`
**Needs:** task 2
**API:** `GET /api/users/me/events`, event `matches` (body `MatchesResponse`)

- [ ] Add a `matches` field to the event store and replace it whole on each `matches` event, like the others.
- [ ] Read the Dashboard section from the store instead of `fetchMatches`. Remove `fetchMatches`, `MATCHES_MOCKED` and
      `PREVIEW_MATCHES` from `src/utils/matches.js`; keep `compatibilityLevel` and `nameSeparator`.
- [ ] Expect the list to change while the page is open: it's sent again after a play by anyone, and after the user
      follows, unfollows, favorites or unfavorites an artist.
- [ ] `sharedArtists` is empty when two people match on tags alone (those scores stop at 39, Medium). Name the shared
      tags instead, for example "You both listen to r&b and chill."
- [ ] Users the caller follows never appear in the list, so a match disappears after they follow it.
- [ ] An empty `matches` means the user has no plays or favorite artists yet. Keep the "No matches yet" empty state.

**Done when:** the section shows real listeners with no mock flag, and a new play updates it without a reload.

### 19. Show compatibility on profiles

`GET /api/users/profile/{username}` now returns `compatibility` (0-100), `sharedArtists` and `sharedTags` (up to 3
each), comparing the signed-in caller with the profile owner.

**Files:** `src/views/PublicProfile.vue`
**API:** `GET /api/users/profile/{username}`

- [ ] Show the style guide's Compatibility score component: the five-segment meter, the level word, and "You both
      listen to …" from `sharedArtists` (or `sharedTags` when there are no shared artists). Use `compatibilityLevel`
      from `src/utils/matches.js`.
- [ ] Hide the component when `compatibility` is null: signed out, the caller's own profile, or either user has no
      plays or favorite artists yet.
- [ ] `0` is a real score: they share nothing. Show Low and leave out the "You both listen to" line.
- [ ] The score isn't live; it changes when the profile loads again.

**Done when:** another user's profile shows the meter with the same level and score as the matches list, and your own
profile shows none.

### 20. Count a listen at the server's threshold

Already done with the backend change, listed so the team knows the player changed.

**Files:** `src/stores/music.js`
**API:** `GET /api/music-pieces/{musicPieceId}/stream`, `POST /api/music-pieces/{musicPieceId}/plays`

- [x] `/stream` returns `minListenSeconds`: half the piece, at most 240 seconds. The player reports the listen once
      that many seconds have played (falling back to 30 if the field is missing).
- [x] Removed the short-piece special case from `finishListening`; the server's rule covers short pieces.

### 21. Show the real top list on the landing page

`GET /api/core/top` now returns this week's most-played songs, so the hardcoded albums can go.

**Files:** `src/views/LandingPage.vue`
**API:** `GET /api/core/top` (public, no token)

- [ ] Replace the hardcoded `topAlbums` with a call to `GET /api/core/top` in `onMounted`, next to the
      `/api/core/users` call. Show the first 3.
- [ ] Load each cover through `resolveCoverURL`, which can't take null, so guard it like `src/stores/music.js` does
      (`piece.coverUrl ? resolveCoverURL(piece.coverUrl) : ''`).
- [ ] These are songs, not albums: change the lead to something like "The songs getting the most plays on Aux this
      week." and rename `album` in the template.
- [ ] Hide the Top section when the list is empty (nothing played that week) or the call fails, the same way the
      profile picture row hides.

**Done when:** the landing page shows this week's most-played songs from the server, and a fresh database shows no Top
section instead of a blank one.

### 22. Load the artist page's songs from the artist endpoint

**Files:** `src/utils/artist.js`, `src/views/ArtistPage.vue`
**API:** `GET /api/artists/{artistId}/music-pieces` (public, token optional)

- [ ] Replace the search workaround in `fetchArtistPieces` with the new endpoint. It's ordered most played first.
- [ ] Load more pages with `nextCursor`, the same way playlist track lists do.
- [ ] Show the uploader on each row now that the pieces carry `uploader`.

**Done when:** the artist page lists songs most played first, with no `/api/core/search` calls in the network tab.

### 23. Link Recently played to its playlist

**Files:** `src/views/Dashboard.vue`
**API:** `recents` event on `GET /api/users/me/events`

- [ ] Each recent now carries `playlistOwnerUsername` next to `playlistId`. Link the card's playlist to
      `/playlist/{playlistOwnerUsername}/{playlistId}`; show no link when `playlistId` is null.
- [ ] Add `playlistOwnerUsername` to the mock recents in `src/utils/dashboardPreview.js`.

**Done when:** a song played from a playlist links to that playlist from Recently played.

### 24. Delete account for real

**Files:** `src/utils/account.js`, `src/views/Settings.vue`
**API:** `DELETE /api/users/me`

- [ ] Turn off `ACCOUNT_MOCKED`.
- [ ] Ask for the current password before deleting, like Change email does, and send it as `currentPassword`. The mocked
      call sends no body today.
- [ ] Show `403 WRONG_PASSWORD` on the password field. It's a 403, not a 401, so `request` won't log the user out.
- [ ] On success, log out and go to the landing page.

**Done when:** deleting an account logs out, and logging back in with it fails.

### 25. Build the music piece page

**Files:** new view and route, `src/views/Dashboard.vue` (My uploads)
**API:** `GET /api/music-pieces/{musicPieceId}` (public, token optional)

- [ ] Add a details page with the cover, title, artist, uploader, tags, length, play count and upload date.
- [ ] Add a Play button that plays the piece on its own with `playPieces([musicPiece])`.
- [ ] Open the page when an upload is clicked.
- [ ] Show a not-found state on `404 MUSIC_PIECE_NOT_FOUND`.

**Done when:** clicking an upload opens its page, and Play starts it.

## P2: Later

### 15. Build the artist page

**API:** `GET /api/artists/{artistId}`, `PUT`/`DELETE .../favorite`, `PUT .../pfp`, `PUT .../banner`,
`GET /api/artists/favorites/{username}`

- [x] Artist page with a Favorite/Unfavorite button.
- [x] Picture and banner editing when `canEdit` is true.
- [x] Link artist names in track rows, search results and Top artists to the page.
- [x] Show favourite artists on the profile.

*The page lists an artist's songs by running a music search for their name. Moving it to the artist endpoint is task
22.*

### 16. Autoplay (not decided)

- [ ] Decide whether to play recommended music when the queue ends with repeat `OFF`, like Apple Music's infinity
      button. The backend has nothing for it yet. Options are in `../backend/BACKEND_REQUESTS.md` (Open questions).

## Blocked: waiting on the backend

Don't start these until the endpoint is in the OpenAPI docs.

Nothing is blocked right now.
