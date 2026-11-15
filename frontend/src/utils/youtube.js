import { fetchAPI, request } from '@/utils/api.js'
import { API_BASE_URL } from '@/utils/variables.js'

// YouTube import: testers and admins only, and only while the server has it on. Two phases: startYoutubeImport
// queues the video and returns a YoutubeImport; poll fetchYoutubeImport until `status` is READY or FAILED, then send
// its importId to uploadMusicPiece in place of mp3File. A READY import is kept for 30 minutes.
// YoutubeImport: { importId, status, videoId, title, durationSeconds, thumbnailUrl, error: { code, message } | null, expiresAt }

export const YOUTUBE_POLL_MS = 2000
export const YOUTUBE_PENDING = ['QUEUED', 'FETCHING_INFO', 'DOWNLOADING']

// What a pending import is doing, for the status line
export const YOUTUBE_STEPS = {
  QUEUED: 'Waiting for a free slot…',
  FETCHING_INFO: 'Checking the video…',
  DOWNLOADING: 'Getting the audio…',
}

// Messages keyed by the backend's error codes, from starting an import, a FAILED import's `error`, or the upload
export const YOUTUBE_ERRORS = {
  INVALID_YOUTUBE_URL: "That isn't a link to a YouTube video. Copy the link from the video's Share button.",
  YOUTUBE_IMPORT_FORBIDDEN: 'Importing from YouTube is only open to testers right now.',
  YOUTUBE_IMPORT_DISABLED: 'Importing from YouTube is turned off right now. Upload an MP3 instead.',
  IMPORT_LIMIT: "You've started a lot of imports. Wait for the ones running to finish, then try again.",
  IMPORT_BUSY: 'The server is busy with other imports. Try again in a minute.',
  AUDIO_TOO_SHORT: 'That video is too short. Songs need to be at least 30 seconds long.',
  AUDIO_TOO_LONG: 'That video is too long. Songs can be up to 10 minutes long.',
  AUDIO_TOO_LARGE: "That video's audio is over 25MB. Try a shorter video.",
  VIDEO_IS_LIVE: "Live streams and premieres can't be imported. Try again once it's over.",
  VIDEO_UNAVAILABLE: "That video is private, age-restricted or removed, so it can't be imported.",
  VIDEO_REJECTED: "That video can't be imported. Try another link.",
  IMPORT_BLOCKED: 'YouTube is blocking the server right now. Upload an MP3 instead.',
  IMPORT_TIMED_OUT: 'YouTube took too long to answer. Try again.',
  IMPORT_FAILED: "We couldn't get the audio from that video. Try again, or upload an MP3 instead.",
  UNSUPPORTED_AUDIO_TYPE: "We couldn't get the audio from that video. Try again, or upload an MP3 instead.",
  INVALID_AUDIO: "We couldn't get the audio from that video. Try again, or upload an MP3 instead.",
  IMPORT_NOT_FOUND: 'That import expired. Get the audio again.',
  IMPORT_NOT_READY: "The audio isn't ready yet. Wait for it to finish.",
  IMPORT_IN_USE: 'This song is already uploading.',
}

export function youtubeErrorMessage(code) {
  return YOUTUBE_ERRORS[code] ?? YOUTUBE_ERRORS.IMPORT_FAILED
}

// { available }: false for anyone who isn't a tester, or when the feature is off. Any failure counts as false
export async function fetchYoutubeImportAvailability() {
  try {
    const result = await fetchAPI(`${API_BASE_URL}/api/imports/youtube`, { notifyOnError: false })
    return result?.available === true
  } catch {
    return false
  }
}

export function startYoutubeImport(url) {
  return request('POST', `${API_BASE_URL}/api/imports/youtube`, { url }, { notifyOnError: false })
}

export function fetchYoutubeImport(importId) {
  return fetchAPI(`${API_BASE_URL}/api/imports/youtube/${encodeURIComponent(importId)}`, { notifyOnError: false })
}

// Cancels a running import or throws away an unused one. Best effort: an import nobody uses expires on its own
export function discardYoutubeImport(importId) {
  return request('DELETE', `${API_BASE_URL}/api/imports/youtube/${encodeURIComponent(importId)}`, undefined, { notifyOnError: false })
    .catch(() => null)
}
