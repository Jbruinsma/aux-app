import { ref, watch } from 'vue'

// The player's background takes the most prominent color of the playing song's cover. The cover is drawn onto a
// small canvas and its pixels are read, which browsers only allow for images served with CORS headers (see the R2
// CORS request in ../backend/BACKEND_REQUESTS.md). Anything that goes wrong just means no color: the player falls back
// to its plain background, the same as for a song without a cover.

const SAMPLE_SIZE = 32
// Pixels this close to black, white or gray don't count as color
const DARK = 30
const LIGHT = 230
const GRAY_SPREAD = 28
// Below this share of colorful pixels the cover is treated as black and white, and its average is used instead
const MIN_COLORFUL = 0.05
// The picked color is kept to a middle lightness, so it never washes out on white or disappears on black
const MIN_LIGHTNESS = 0.3
const MAX_LIGHTNESS = 0.65

/**
 * Picks the most prominent color from RGBA pixel data (as from `getImageData().data`). Pixels are grouped into
 * 4-bit-per-channel buckets; each bucket scores its pixel count, weighted toward saturated colors so a strong accent
 * beats a large gray area. Returns a `#rrggbb` string, or null when there are no opaque pixels.
 */
export function pickProminentColor(pixels) {
  const buckets = new Map()
  let colorful = 0
  let total = 0
  let sumR = 0
  let sumG = 0
  let sumB = 0
  for (let i = 0; i < pixels.length; i += 4) {
    if (pixels[i + 3] < 128) continue
    const r = pixels[i]
    const g = pixels[i + 1]
    const b = pixels[i + 2]
    total++
    sumR += r
    sumG += g
    sumB += b
    const max = Math.max(r, g, b)
    const min = Math.min(r, g, b)
    if (max < DARK || min > LIGHT || max - min < GRAY_SPREAD) continue
    colorful++
    const key = ((r >> 4) << 8) | ((g >> 4) << 4) | (b >> 4)
    const bucket = buckets.get(key) ?? { n: 0, r: 0, g: 0, b: 0, score: 0 }
    bucket.n++
    bucket.r += r
    bucket.g += g
    bucket.b += b
    bucket.score += 0.35 + (max - min) / max
    buckets.set(key, bucket)
  }
  if (!total) return null
  let best = null
  for (const bucket of buckets.values()) if (!best || bucket.score > best.score) best = bucket
  const rgb = best && colorful > total * MIN_COLORFUL
    ? [best.r / best.n, best.g / best.n, best.b / best.n]
    : [sumR / total, sumG / total, sumB / total]
  return toHex(clampLightness(rgb, MIN_LIGHTNESS, MAX_LIGHTNESS))
}

function clampLightness([r, g, b], lo, hi) {
  r /= 255
  g /= 255
  b /= 255
  const max = Math.max(r, g, b)
  const min = Math.min(r, g, b)
  const d = max - min
  let l = (max + min) / 2
  let h = 0
  let s = 0
  if (d) {
    s = d / (1 - Math.abs(2 * l - 1))
    if (max === r) h = ((g - b) / d) % 6
    else if (max === g) h = (b - r) / d + 2
    else h = (r - g) / d + 4
    h *= 60
    if (h < 0) h += 360
  }
  l = Math.min(hi, Math.max(lo, l))
  const c = (1 - Math.abs(2 * l - 1)) * s
  const x = c * (1 - Math.abs(((h / 60) % 2) - 1))
  const m = l - c / 2
  const [r1, g1, b1] = h < 60 ? [c, x, 0] : h < 120 ? [x, c, 0] : h < 180 ? [0, c, x] : h < 240 ? [0, x, c] : h < 300 ? [x, 0, c] : [c, 0, x]
  return [(r1 + m) * 255, (g1 + m) * 255, (b1 + m) * 255]
}

function toHex(rgb) {
  return `#${rgb.map(v => Math.round(Math.min(255, Math.max(0, v))).toString(16).padStart(2, '0')).join('')}`
}

// Covers are cached for a year (immutable), so a copy saved without CORS headers never goes away by itself. Changing
// this value gives every cover a new URL that nothing has cached yet. v1 was cached before the R2 CORS policy existed
const CORS_PARAM = 'aux-cors=2'

// Cover URL -> Promise of its color (or null), so each cover is read once per page load
const cache = new Map()

/**
 * Loads a cover and returns its prominent color, or null if it can't be read (no CORS headers yet, a failed load, or
 * no canvas support).
 *
 * The image is requested with CORS_PARAM added. Covers shown in plain `<img>` tags are fetched without an Origin header,
 * so their cached copies (in the browser and at Cloudflare's edge) have no CORS headers. A separate URL keeps the CORS
 * copy in its own cache entry. R2 ignores the query string when it looks up the object.
 */
export function coverColor(url) {
  if (!url) return Promise.resolve(null)
  if (!cache.has(url)) cache.set(url, readColor(url))
  return cache.get(url)
}

function readColor(url) {
  if (typeof Image === 'undefined' || typeof document === 'undefined') return Promise.resolve(null)
  return new Promise((resolve) => {
    const img = new Image()
    img.crossOrigin = 'anonymous'
    img.decoding = 'async'
    img.onload = () => {
      try {
        const canvas = document.createElement('canvas')
        canvas.width = SAMPLE_SIZE
        canvas.height = SAMPLE_SIZE
        const ctx = canvas.getContext('2d', { willReadFrequently: true })
        if (!ctx) return resolve(null)
        ctx.drawImage(img, 0, 0, SAMPLE_SIZE, SAMPLE_SIZE)
        resolve(pickProminentColor(ctx.getImageData(0, 0, SAMPLE_SIZE, SAMPLE_SIZE).data))
      } catch {
        // A tainted canvas (no CORS headers) throws on getImageData
        resolve(null)
      }
    }
    img.onerror = () => resolve(null)
    img.src = withCorsParam(url)
  })
}

function withCorsParam(url) {
  // data: and blob: URLs, and same-origin covers (dev uploads under /uploads), can be read without CORS
  if (/^(data|blob):/.test(url)) return url
  if (typeof location !== 'undefined' && new URL(url, location.href).origin === location.origin) return url
  return `${url}${url.includes('?') ? '&' : '?'}${CORS_PARAM}`
}

/** A ref that holds the prominent color of whatever cover URL `source` returns, or null while loading or unreadable. */
export function useCoverColor(source) {
  const color = ref(null)
  let request = 0
  watch(source, (url) => {
    const mine = ++request
    if (!url) {
      color.value = null
      return
    }
    coverColor(url).then((hex) => {
      if (mine === request) color.value = hex
    })
  }, { immediate: true })
  return color
}

/** For tests */
export function clearCoverColorCache() {
  cache.clear()
}
