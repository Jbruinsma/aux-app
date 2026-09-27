// Banners always show at 3:1. A crop says how the image sits in that frame:
// (cx, cy) is the point of the image at the frame's center, as 0–1 fractions of the image's width and height,
// and zoom is how far in from "just covers the frame" (1) it is.
export const BANNER_RATIO = 3
export const MIN_ZOOM = 1
export const MAX_ZOOM = 3
export const DEFAULT_CROP = Object.freeze({ cx: 0.5, cy: 0.5, zoom: 1 })

const clamp = (value, min, max) => Math.min(max, Math.max(min, value))

// Everything is measured in frame widths (the frame is 1 wide and 1/3 tall), so the same crop
// lays out identically in the large editor and the small profile card
function layout(crop, imageWidth, imageHeight) {
  const zoom = clamp(crop.zoom, MIN_ZOOM, MAX_ZOOM)
  const scale = Math.max(1 / imageWidth, 1 / BANNER_RATIO / imageHeight) * zoom
  const width = imageWidth * scale
  const height = imageHeight * scale
  // Keep the image covering the frame: no gaps on any side
  const left = clamp(0.5 - crop.cx * width, 1 - width, 0)
  const top = clamp(0.5 / BANNER_RATIO - crop.cy * height, 1 / BANNER_RATIO - height, 0)
  return { zoom, scale, width, height, left, top }
}

// The nearest crop that leaves no gaps, e.g. after dragging past an edge or zooming out
export function clampCrop(crop, imageWidth, imageHeight) {
  const { zoom, width, height, left, top } = layout(crop, imageWidth, imageHeight)
  return { cx: (0.5 - left) / width, cy: (0.5 / BANNER_RATIO - top) / height, zoom }
}

// Moves the crop by a drag of (dx, dy), given in frame widths
export function panCrop(crop, dx, dy, imageWidth, imageHeight) {
  const { width, height } = layout(crop, imageWidth, imageHeight)
  return clampCrop({ ...crop, cx: crop.cx - dx / width, cy: crop.cy - dy / height }, imageWidth, imageHeight)
}

// Inline style for an <img> inside a position: relative, overflow: hidden, 3:1 box
export function bannerImageStyle(crop, imageWidth, imageHeight) {
  const { width, left, top } = layout(crop, imageWidth, imageHeight)
  return {
    position: 'absolute',
    maxWidth: 'none',
    width: `${width * 100}%`,
    height: 'auto',
    left: `${left * 100}%`,
    top: `${top * BANNER_RATIO * 100}%`,
  }
}

// The visible part of the original image in pixels, which is what the backend would crop to
export function bannerCropRect(crop, imageWidth, imageHeight) {
  const { scale, left, top } = layout(crop, imageWidth, imageHeight)
  return {
    x: Math.round(-left / scale),
    y: Math.round(-top / scale),
    width: Math.round(1 / scale),
    height: Math.round(1 / BANNER_RATIO / scale),
  }
}
