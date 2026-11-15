import { afterEach, expect, it, vi } from 'vitest'
import { cleanTitle, fetchLyrics, lineAt, parseLrc } from './lyrics.js'

afterEach(() => vi.unstubAllGlobals())

const reply = (status, body) => ({ ok: status < 400, status, json: async () => body })

it('reads LRC lines, repeated stamps and gaps, in time order', () => {
  const lines = parseLrc("[ar:Frank Ocean]\n[00:03.72] 'Round your city\n[00:20.00][01:10.5] Chorus\n[00:13.40] \nno stamp")
  expect(lines).toEqual([
    { time: 3.72, text: "'Round your city" },
    { time: 13.4, text: '' },
    { time: 20, text: 'Chorus' },
    { time: 70.5, text: 'Chorus' },
  ])
  expect([lineAt(lines, 1), lineAt(lines, 3.72), lineAt(lines, 19.9), lineAt(lines, 500)]).toEqual([-1, 0, 1, 3])
})

it('drops file endings and video tags from titles', () => {
  expect(cleanTitle('Nights (Official Audio).mp3')).toBe('Nights')
  expect(cleanTitle('Pink + White [Lyrics]')).toBe('Pink + White')
  expect(cleanTitle('Self Control (feat. Yung Lean)')).toBe('Self Control (feat. Yung Lean)')
})

it('matches by length, then falls back to search within 3 seconds, timed lyrics first', async () => {
  const fetch = vi.fn()
    .mockResolvedValueOnce(reply(404, { code: 404 }))
    .mockResolvedValueOnce(reply(200, [
      { duration: 250, syncedLyrics: '[00:01.00] wrong edit', plainLyrics: 'wrong edit' },
      { duration: 309, syncedLyrics: null, plainLyrics: 'untimed' },
      { duration: 305.5, syncedLyrics: '[00:01.00] timed', plainLyrics: 'timed' },
    ]))
  vi.stubGlobal('fetch', fetch)
  const lyrics = await fetchLyrics({ musicPieceId: 'm1', title: 'Nights.mp3', artist: 'Frank Ocean', duration: 307.4 })
  expect(fetch.mock.calls[0][0]).toBe('https://lrclib.net/api/get?track_name=Nights&artist_name=Frank+Ocean&duration=307')
  expect(fetch.mock.calls[1][0]).toBe('https://lrclib.net/api/search?track_name=Nights&artist_name=Frank+Ocean')
  expect(lyrics.lines).toEqual([{ time: 1, text: 'timed' }])

  // Cached for the session
  await fetchLyrics({ musicPieceId: 'm1', title: 'Nights.mp3', artist: 'Frank Ocean', duration: 307.4 })
  expect(fetch).toHaveBeenCalledTimes(2)
})

it('finds nothing when no copy is close enough in length', async () => {
  vi.stubGlobal('fetch', vi.fn()
    .mockResolvedValueOnce(reply(404, {}))
    .mockResolvedValueOnce(reply(200, [{ duration: 200, syncedLyrics: '[00:01.00] no', plainLyrics: 'no' }])))
  expect(await fetchLyrics({ musicPieceId: 'm2', title: 'Song', artist: 'A', duration: 180 })).toBeNull()
})
