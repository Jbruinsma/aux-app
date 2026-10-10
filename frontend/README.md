# Unchained

This template should help get you started developing with Vue 3 in Vite.

## Recommended IDE Setup

[VSCode](https://code.visualstudio.com/) + [Volar](https://marketplace.visualstudio.com/items?itemName=Vue.volar) (and disable Vetur).

## Customize configuration

See [Vite Configuration Reference](https://vite.dev/config/).

## Project Setup

```sh
npm install
```

### Compile and Hot-Reload for Development

```sh
npm run dev
```

### Compile and Minify for Production

```sh
npm run build
```

### Run Frontend Tests

```sh
npm test
```

The notification regression tests cover successful and rejected actions, account
verification, preview-only deletion, playlist saving, navigation, and dismissal timers.

### Lint with [ESLint](https://eslint.org/)

```sh
npm run lint
```

### Theme preferences

Settings → Appearance offers System, Light, and Dark. Explicit choices are stored
in `localStorage` under `aux-theme`; System removes the override and follows device
changes live. The theme is applied to `<html>` before the app mounts. Storage
restrictions do not prevent changing the current tab's appearance.

### Playback migration

Startup reads `GET /api/users/me/last-playback` once per authenticated session.
A 204 response leaves the player empty. A 200 response supplies track metadata and
an optional playlist ID; it does not include an audio URL, queue, or seek position.
The last track is displayed paused, with unavailable playback controls disabled.

The retired playlist play/shuffle and playback-update calls are guarded and send
no requests. Phase 2 must implement playable URLs, queues, and listening-event
recording against the current API before enabling those paths. This change does
not claim to restore audio playback.
