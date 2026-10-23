import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useEventsStore } from './events.js'
import { useUserStore } from './user.js'

const router = vi.hoisted(() => ({ currentRoute: { value: { fullPath: '/dashboard' } }, replace: vi.fn() }))
vi.mock('@/router/index.js', () => ({ default: router }))

const encoder = new TextEncoder()

// A response whose body sends `chunks`, then stays open unless `end` is true
function stream(chunks, { end = false } = {}) {
  return {
    ok: true,
    status: 200,
    body: new ReadableStream({
      start(controller) {
        for (const chunk of chunks) controller.enqueue(encoder.encode(chunk))
        if (end) controller.close()
      },
    }),
  }
}

const failure = (status, code) => ({ ok: false, status, json: async () => ({ errorDetails: { code } }) })

beforeEach(() => {
  setActivePinia(createPinia())
  useUserStore().login({ userId: 'u1', username: 'me', onboardingStep: 'DONE' }, 'secret-token')
  vi.spyOn(console, 'warn').mockImplementation(() => {})
})
afterEach(() => {
  useEventsStore().disconnect()
  vi.useRealTimers()
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

it('sends the token in the Authorization header, never in the URL', async () => {
  const fetch = vi.fn().mockResolvedValue(stream([]))
  vi.stubGlobal('fetch', fetch)
  useEventsStore().connect()
  await vi.waitFor(() => expect(fetch).toHaveBeenCalled())
  const [url, options] = fetch.mock.calls[0]
  expect(url).toMatch(/\/api\/users\/me\/events$/)
  expect(url).not.toContain('secret-token')
  expect(options.headers).toMatchObject({ Authorization: 'Bearer secret-token', Accept: 'text/event-stream' })
})

it('fills each field from its event, across split chunks, CRLF and an optional space, skipping pings', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(stream([
    'event:invites\ndata:{"invites":[]}\n\nevent:top-art',
    'ists\r\ndata: {"artists":[{"artistId":"a1"}]}\r\n\r\n:ping\n\n',
    'event:library\ndata:[{"playlistId":"p1"}]\n\nevent:library\ndata:[{"playlistId":"p2"}]\n\n',
  ])))
  const events = useEventsStore()
  events.connect()
  await vi.waitFor(() => expect(events.library).toEqual([{ playlistId: 'p2' }]))
  expect(events.invites).toEqual({ invites: [] })
  expect(events.topArtists).toEqual({ artists: [{ artistId: 'a1' }] })
  expect(events.recents).toBeNull()
  expect(events.status).toBe('open')
})

it('reconnects straight away when the server ends the stream, and backs off up to 30 seconds on failures', async () => {
  vi.useFakeTimers()
  const fetch = vi.fn()
    .mockResolvedValueOnce(stream(['event:recents\ndata:{"recents":[]}\n\n'], { end: true }))
    .mockRejectedValue(new TypeError('Failed to fetch'))
  vi.stubGlobal('fetch', fetch)
  const events = useEventsStore()
  events.connect()
  await vi.advanceTimersByTimeAsync(0)
  expect(fetch).toHaveBeenCalledTimes(2)
  expect(events.status).toBe('error')
  expect(events.recents).toEqual({ recents: [] })

  const waits = []
  for (let i = 0; i < 7; i++) {
    const before = fetch.mock.calls.length
    let waited = 0
    while (fetch.mock.calls.length === before) {
      await vi.advanceTimersByTimeAsync(500)
      waited += 500
    }
    waits.push(waited)
  }
  expect(waits).toEqual([1000, 2000, 4000, 8000, 16000, 30000, 30000])
})

it('logs the user out on a 401 and stops trying', async () => {
  vi.useFakeTimers()
  const fetch = vi.fn().mockResolvedValue(failure(401, 'INVALID_TOKEN'))
  vi.stubGlobal('fetch', fetch)
  const events = useEventsStore()
  events.connect()
  await vi.advanceTimersByTimeAsync(0)
  expect(useUserStore().loggedIn).toBe(false)
  expect(router.replace).toHaveBeenCalledWith({ name: 'Login', query: { redirect: '/dashboard' } })
  expect(events.status).toBe('idle')
  await vi.advanceTimersByTimeAsync(60_000)
  expect(fetch).toHaveBeenCalledTimes(1)
})

it('closes the stream and forgets its data on disconnect', async () => {
  let signal
  vi.stubGlobal('fetch', vi.fn((url, options) => {
    signal = options.signal
    return Promise.resolve(stream(['event:uploads\ndata:{"uploadedPiecesCount":0,"uploadedMusicPieces":[]}\n\n']))
  }))
  const events = useEventsStore()
  events.connect()
  events.connect()
  await vi.waitFor(() => expect(events.uploads).not.toBeNull())
  expect(fetch).toHaveBeenCalledTimes(1)
  events.disconnect()
  expect(signal.aborted).toBe(true)
  expect(events.uploads).toBeNull()
  expect(events.status).toBe('idle')
})
