export const BANNER_RATIO = 3
export const MIN_ZOOM = 1
export const MAX_ZOOM = 3
export const DEFAULT_CROP = Object.freeze({ cx: 0.5, cy: 0.5, zoom: 1 })

const clamp = (value, min, max) => Math.min(max, Math.max(min, value))

function layout(crop, imageWidth, imageHeight) {
  const zoom = clamp(crop.zoom, MIN_ZOOM, MAX_ZOOM)
  const scale = Math.max(1 / imageWidth, 1 / BANNER_RATIO / imageHeight) * zoom
  const width = imageWidth * scale
  const height = imageHeight * scale
  const left = clamp(0.5 - crop.cx * width, 1 - width, 0)
  const top = clamp(0.5 / BANNER_RATIO - crop.cy * height, 1 / BANNER_RATIO - height, 0)
  return { zoom, scale, width, height, left, top }
}

export function clampCrop(crop, imageWidth, imageHeight) {
  const { zoom, width, height, left, top } = layout(crop, imageWidth, imageHeight)
  return { cx: (0.5 - left) / width, cy: (0.5 / BANNER_RATIO - top) / height, zoom }
}

export function panCrop(crop, dx, dy, imageWidth, imageHeight) {
  const { width, height } = layout(crop, imageWidth, imageHeight)
  return clampCrop({ ...crop, cx: crop.cx - dx / width, cy: crop.cy - dy / height }, imageWidth, imageHeight)
}

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
