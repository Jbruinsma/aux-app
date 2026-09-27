// Empty = same origin; the Vite dev server proxies /api and /uploads to the backend (see vite.config.js)
export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? ''
