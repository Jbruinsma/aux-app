import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { useNotificationStore } from '@/stores/notification.js'
import { useUserStore } from '@/stores/user.js'
import { useMusicStore } from '@/stores/music.js'
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
const piece = (id, artistId, playCount) => ({ tags: [],
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
