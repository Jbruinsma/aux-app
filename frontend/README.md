# Aux frontend

Vue 3 app built with Vite. Needs Node.js 22.12 or newer. Recommended IDE: [WebStorm](https://www.jetbrains.com/webstorm/).

## Run

```bash
npm install
npm run dev
```

The dev server sends `/api` and `/uploads` to the shared backend at `http://150.136.105.240:5000`. To use a backend
running on your machine, create `.env.local` (gitignored) with `AUX_BACKEND=local` and restart `npm run dev`.

## Other commands

```bash
npm test        # Vitest
npm run lint    # ESLint, fixes what it can
npm run build   # production build in dist/
```

What to work on next is in [`ROADMAP.md`](./ROADMAP.md).
