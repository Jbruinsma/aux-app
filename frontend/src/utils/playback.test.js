import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { request } from '@/utils/api.js'
import { nowPlayingReporter } from './playback.js'

vi.mock('@/utils/api.js', () => ({ fetchAPI: vi.fn(), request: vi.fn() }))

beforeEach(() => {
  vi.useFakeTimers()
  request.mockReset()
  request.mockResolvedValue(null)
})
afterEach(() => {
  vi.useRealTimers()
})

it('sends one report for a burst of audio events, with the latest state', async () => {
  let state = { musicPieceId: 'm1', positionSeconds: 41.8, isPlaying: false }
  const reporter = nowPlayingReporter(() => state)
  reporter.schedule()
  state = { ...state, isPlaying: true }
  reporter.schedule()
  await vi.advanceTimersByTimeAsync(300)
  expect(request).toHaveBeenCalledTimes(1)
  expect(request).toHaveBeenCalledWith('PUT', expect.stringContaining('/api/users/me/now-playing'), { musicPieceId: 'm1', positionSeconds: 41, isPlaying: true }, { notifyOnError: false })
})

it('sends nothing when signed out or after cancel, and swallows failures', async () => {
  const reporter = nowPlayingReporter(() => null)
  reporter.schedule()
  await vi.advanceTimersByTimeAsync(300)
  expect(request).not.toHaveBeenCalled()

  request.mockRejectedValue({ status: 500 })
  const failing = nowPlayingReporter(() => ({ musicPieceId: 'm1', positionSeconds: 0, isPlaying: true }))
  failing.schedule()
  await vi.advanceTimersByTimeAsync(300)
  expect(request).toHaveBeenCalledTimes(1)
  failing.schedule()
  failing.cancel()
  await vi.advanceTimersByTimeAsync(300)
  expect(request).toHaveBeenCalledTimes(1)
})
