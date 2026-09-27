// Profile photo rules, matching what the backend enforces; checking here saves a round trip
export const MAX_PHOTO_BYTES = 5 * 1024 * 1024
export const MAX_PHOTO_PIXELS = 25_000_000
export const MIN_PHOTO_SIDE = 256

// Messages keyed by the backend's error codes, so server and client rejections read the same
export const PHOTO_ERRORS = {
  IMAGE_TOO_LARGE: 'That photo is too big. Use one under 5MB and 25 megapixels.',
  IMAGE_TOO_SMALL: 'That photo is too small. Use one at least 256 × 256 pixels.',
  UNSUPPORTED_IMAGE_TYPE: "That file isn't a JPEG or PNG. Choose a different photo.",
  INVALID_IMAGE: "We couldn't read that photo. Choose a different one.",
}

// Banner rules are proposed: the backend has no banner upload yet, so these may change when it does
export const MIN_BANNER_WIDTH = 600
export const MIN_BANNER_HEIGHT = 200

export const BANNER_ERRORS = {
  IMAGE_TOO_LARGE: 'That image is too big. Use one under 5MB and 25 megapixels.',
  IMAGE_TOO_SMALL: 'That image is too small. Use one at least 600 × 200 pixels.',
  UNSUPPORTED_IMAGE_TYPE: "That file isn't a JPEG or PNG. Choose a different image.",
  INVALID_IMAGE: "We couldn't read that image. Choose a different one.",
}

export async function bannerProblem(file) {
  if (!['image/jpeg', 'image/png'].includes(file.type)) return BANNER_ERRORS.UNSUPPORTED_IMAGE_TYPE
  if (file.size > MAX_PHOTO_BYTES) return BANNER_ERRORS.IMAGE_TOO_LARGE
  try {
    const { width, height } = await createImageBitmap(file)
    if (width * height > MAX_PHOTO_PIXELS) return BANNER_ERRORS.IMAGE_TOO_LARGE
    if (width < MIN_BANNER_WIDTH || height < MIN_BANNER_HEIGHT) return BANNER_ERRORS.IMAGE_TOO_SMALL
  } catch {
    return BANNER_ERRORS.INVALID_IMAGE
  }
  return null
}

// Returns a message explaining why the file can't be used, or null if it's fine
export async function photoProblem(file) {
  if (!['image/jpeg', 'image/png'].includes(file.type)) return PHOTO_ERRORS.UNSUPPORTED_IMAGE_TYPE
  if (file.size > MAX_PHOTO_BYTES) return PHOTO_ERRORS.IMAGE_TOO_LARGE
  try {
    const { width, height } = await createImageBitmap(file)
    if (width * height > MAX_PHOTO_PIXELS) return PHOTO_ERRORS.IMAGE_TOO_LARGE
    if (width < MIN_PHOTO_SIDE || height < MIN_PHOTO_SIDE) return PHOTO_ERRORS.IMAGE_TOO_SMALL
  } catch {
    return PHOTO_ERRORS.INVALID_IMAGE
  }
  return null
}
