import { afterEach, expect, it, vi } from 'vitest'
import { additionMessage, mp3Problem } from './music.js'

afterEach(() => vi.unstubAllGlobals())

function fakeAudio(duration) {
  vi.stubGlobal('URL', { ...URL, createObjectURL: () => 'blob:x', revokeObjectURL: () => {} })
  vi.stubGlobal('Audio', class {
    set src(_) { queueMicrotask(() => (duration === null ? this.onerror() : (this.duration = duration, this.onloadedmetadata()))) }
  })
}

const mp3 = (bytes = 1000, name = 'song.mp3', type = 'audio/mpeg') => {
  const file = new File(['x'], name, { type })
  Object.defineProperty(file, 'size', { value: bytes })
  return file
}

it.each([
  ['not an MP3', mp3(1000, 'cover.png', 'image/png'), 60, "That file isn't an MP3. Choose an MP3 file."],
  ['over 25MB', mp3(26 * 1024 * 1024), 60, 'That file is too big. MP3s can be up to 25MB.'],
  ['unreadable', mp3(), null, "We couldn't read that MP3. It may be damaged; try another file."],
  ['under 5 seconds', mp3(), 4, 'That song is too short. Songs need to be at least 5 seconds long.'],
  ['over 10 minutes', mp3(), 601, 'That song is too long. Songs can be up to 10 minutes long.'],
])('rejects an upload that is %s before sending it', async (_, file, seconds, message) => {
  fakeAudio(seconds)
  expect((await mp3Problem(file)).problem).toBe(message)
})

it('accepts an MP3 within the limits and reports its length, even without a MIME type', async () => {
  fakeAudio(212.4)
  expect(await mp3Problem(mp3(5_000_000, 'Track.MP3', ''))).toEqual({ problem: null, seconds: 212.4 })
})

it('sums up what was added and skipped', () => {
  expect(additionMessage({ addedCount: 1, failedCount: 0 })).toBe('Added 1 song.')
  expect(additionMessage({ addedCount: 3, failedCount: 1 })).toBe("Added 3 songs. 1 song couldn't be added (already in the playlist, private, or the playlist is full).")
  expect(additionMessage({ addedCount: 0, failedCount: 2 })).toBe("2 songs couldn't be added (already in the playlist, private, or the playlist is full).")
})
