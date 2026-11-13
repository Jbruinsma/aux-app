import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { useNotificationStore } from '@/stores/notification.js'
import { useUserStore } from '@/stores/user.js'
import { useMusicStore } from '@/stores/music.js'
import { useEventsStore } from '@/stores/events.js'
import { fetchAPI, request } from '@/utils/api.js'
import PublicProfile from './PublicProfile.vue'

const { route } = vi.hoisted(() => ({ route: { params: {} } }))
vi.mock('vue-router', () => ({ useRoute: () => route, useRouter: () => ({ push: vi.fn(), replace: vi.fn() }) }))
vi.mock('@/router/index.js', () => ({ default: { push: vi.fn(), replace: vi.fn() } }))
vi.mock('@/utils/api.js', () => ({ fetchAPI: vi.fn(), postToAPI: vi.fn(), request: vi.fn() }))

const profile = (overrides) => ({
  username: 'pal', pfpUrl: null, bannerUrl: null, profileDetails: null, playlists: [], isMe: false, isFollowing: false,
  followingMe: false, followersCount: '12', followingCount: '8', uploadsCount: '3', ...overrides,
})
const piece = (id, artistId, playCount) => ({
  musicPieceId: id, name: `Song ${id}`, coverUrl: null, artistSummary: { artistId, artistName: `Artist ${artistId}`, artistPfpUrl: null }, playCount,
})

// The profile, favorites and (on your own profile) uploads
function backend(data, uploads = []) {
  fetchAPI.mockImplementation(async (url) => {
    if (url.includes('/api/artists/favorites/')) return []
    if (url.includes('/api/music-pieces')) return { uploadedPiecesCount: uploads.length, uploadedMusicPieces: uploads }
    return data
  })
}

let wrapper
beforeEach(() => {
  setActivePinia(createPinia())
  useUserStore().login({ userId: 'u1', username: 'me', onboardingStep: 'DONE' }, 'token')
  route.params = { username: 'pal' }
  fetchAPI.mockReset()
  request.mockReset()
})
afterEach(() => {
  wrapper?.unmount()
  useNotificationStore().hide()
  vi.unstubAllGlobals()
})

async function render() {
  wrapper = mount(PublicProfile, {
    global: { stubs: { AppHeader: true, AppFooter: true, FollowListDialog: { template: '<div />', methods: { open: vi.fn() } }, RouterLink: { template: '<a><slot /></a>' } } },
  })
  await flushPromises()
}

it('follows and unfollows, says Follow back to a follower, and marks friends', async () => {
  backend(profile({ followingMe: true }))
  request.mockResolvedValue(null)
  await render()
  const button = () => wrapper.get('button[aria-pressed]')
  expect(button().text()).toBe('Follow back')
  expect(wrapper.get('.badge').text()).toBe('Follows you')
  await button().trigger('click')
  await flushPromises()
  expect(request).toHaveBeenCalledWith('PUT', expect.stringContaining('/api/users/pal/follow'), undefined, { notifyOnError: false })
  expect(button().text()).toBe('Following')
  expect(wrapper.get('.badge').text()).toBe('Friends')
})

it('opens the follower and following lists from the counts, unless the user hides them', async () => {
  backend(profile())
  await render()
  const counts = wrapper.findAll('.stats button.stat-link')
  expect(counts.map((count) => count.text())).toEqual(['12', '8'])

  backend(profile({ canViewFollows: false }))
  route.params = { username: 'pal2' }
  wrapper.unmount()
  await render()
  expect(wrapper.findAll('.stats button.stat-link')).toHaveLength(0)
  expect(wrapper.get('.stats').text()).toContain('12')
})

it('groups your uploads by artist, most played first, and plays them in that order', async () => {
  route.params = { username: 'me' }
  const uploads = [piece('1', 'a', 5), piece('2', 'b', 30), piece('3', 'a', 40)]
  backend(profile({ username: 'me', isMe: true }), uploads)
  await render()
  const groups = wrapper.findAll('.artist-group')
  expect(groups.map((group) => group.get('.group-name').text())).toEqual(['Artist a', 'Artist b'])
  expect(groups[0].text()).toContain('40 plays')
  const music = useMusicStore()
  const playPieces = vi.spyOn(music, 'playPieces').mockReturnValue(true)
  await groups[1].get('button.play').trigger('click')
  expect(playPieces).toHaveBeenCalledWith([uploads[2], uploads[0], uploads[1]], 2)
})

it("says other people's uploads are coming soon until the backend can list them", async () => {
  backend(profile())
  await render()
  expect(wrapper.text()).toContain('Uploads are coming soon')
  expect(fetchAPI.mock.calls.some(([url]) => url.includes('/music-pieces'))).toBe(false)
})

it('puts the Follow button back and explains when the user is gone', async () => {
  backend(profile())
  request.mockRejectedValue({ status: 404, code: 'USER_NOT_FOUND' })
  await render()
  await wrapper.get('button[aria-pressed]').trigger('click')
  await flushPromises()
  expect(wrapper.get('button[aria-pressed]').text()).toBe('Follow')
  expect(wrapper.get('[role="alert"]').text()).toContain("@pal isn't on Aux anymore")
})

it.each([
  [0, 'Low', 1], [19, 'Low', 1], [20, 'Medium', 2], [39, 'Medium', 2],
  [40, 'High', 3], [59, 'High', 3], [60, 'Very high', 4], [79, 'Very high', 4],
  [80, 'Super', 5], [100, 'Super', 5],
])('shows score %s with level %s and %s filled segments', async (score, label, filled) => {
  backend(profile({ compatibility: score, sharedArtists: [], sharedTags: [] }))
  await render()
  expect(wrapper.get('.compatibility-level').text()).toBe(label)
  expect(wrapper.get('.compatibility-meter').attributes('aria-label')).toBe(`${score} percent compatible`)
  expect(wrapper.get('.compatibility-percent').text()).toBe(`${score}%`)
  expect(wrapper.findAll('.compatibility-segment')).toHaveLength(5)
  expect(wrapper.findAll('.compatibility-segment.filled')).toHaveLength(filled)
  expect(wrapper.find('.compatibility-shared').exists()).toBe(false)
})

it('prefers shared artists to tags and formats up to three names', async () => {
  backend(profile({ compatibility: 87, sharedArtists: [
    { artistId: 'a1', artistName: 'Frank Ocean' }, { artistId: 'a2', artistName: 'SZA' },
    { artistId: 'a3', artistName: 'Mitski' },
  ], sharedTags: ['r&b'] }))
  await render()
  expect(wrapper.get('.compatibility-shared').text()).toBe('You both listen to Frank Ocean, SZA and Mitski.')
})

it.each([['chill'], ['chill', 'r&b'], ['chill', 'r&b', 'soul']])('uses shared tags when no artists are shared: %j', async (...tags) => {
  backend(profile({ compatibility: 35, sharedArtists: [], sharedTags: tags }))
  await render()
  expect(wrapper.findAll('.compatibility-shared b').map((name) => name.text())).toEqual(tags)
})

it.each(['signed out', 'own profile', 'no listening data', 'older response'])('hides compatibility for %s', async (reason) => {
  if (reason === 'signed out') useUserStore().logout()
  const data = profile({ isMe: reason === 'own profile', compatibility: null, sharedArtists: null, sharedTags: null })
  if (reason === 'older response') delete data.compatibility
  backend(data)
  await render()
  expect(wrapper.find('.compatibility').exists()).toBe(false)
})

it('shows zero without shared names even if stale names are present', async () => {
  backend(profile({ compatibility: 0, sharedArtists: [{ artistName: 'Stale artist' }], sharedTags: ['chill'] }))
  await render()
  expect(wrapper.get('.compatibility-level').text()).toBe('Low')
  expect(wrapper.find('.compatibility-shared').exists()).toBe(false)
})

it('keeps the profile score until the profile loads again, ignoring live matches', async () => {
  backend(profile({ compatibility: 87 }))
  await render()
  useEventsStore().matches = { matches: [{ user: { username: 'pal' }, compatibility: 20 }] }
  await flushPromises()
  expect(wrapper.get('.compatibility-percent').text()).toBe('87%')
  backend(profile({ compatibility: 20 }))
  await wrapper.vm.loadProfile()
  await flushPromises()
  expect(wrapper.get('.compatibility-percent').text()).toBe('20%')
})
