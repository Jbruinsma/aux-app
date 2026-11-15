import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { useEventsStore } from '@/stores/events.js'
import { useMusicStore } from '@/stores/music.js'
import { useNotificationStore } from '@/stores/notification.js'
import { useUserStore } from '@/stores/user.js'
import { fetchDiscover } from '@/utils/discover.js'
import { addPiecesToPlaylist } from '@/utils/music.js'
import PlaylistRecommendations from './PlaylistRecommendations.vue'

vi.mock('@/utils/discover.js', () => ({ fetchDiscover: vi.fn() }))
vi.mock('@/utils/music.js', () => ({ addPiecesToPlaylist: vi.fn() }))

const RouterLink = { props: ['to'], template: '<a :href="JSON.stringify(to)"><slot /></a>' }
const piece = (n) => ({ musicPieceId: `r${n}`, name: `Rec ${n}`, coverUrl: null, isPublic: true, artistSummary: { artistName: `Artist ${n}` }, uploader: { username: 'up', pfpUrl: null } })
const track = (n) => ({ musicPieceId: `m${n}`, name: `Song ${n}`, isPublic: true, isAvailable: true })

let wrapper
beforeEach(() => {
  setActivePinia(createPinia())
  vi.useFakeTimers({ toFake: ['setTimeout'] })
  fetchDiscover.mockReset()
  addPiecesToPlaylist.mockReset()
  // Seed m2 ranks r1, r2, r3...; seed m1 also puts r3 on top (and suggests m1, already in the playlist), so r3 comes first
  fetchDiscover.mockImplementation(async (id) => ({
    musicPieces: id === 'm2' ? [1, 2, 3, 4, 5, 6, 7, 8].map(piece) : [piece(3), track(1), piece(9)],
  }))
})
afterEach(() => {
  wrapper?.unmount()
  wrapper = null
  useNotificationStore().hide()
  vi.useRealTimers()
})

async function render(props = {}) {
  wrapper = mount(PlaylistRecommendations, {
    props: { playlistId: 'p1', tracks: [track(1), track(2)], canEdit: true, ...props },
    global: { stubs: { RouterLink } },
    attachTo: document.body,
  })
  await flushPromises()
}

const names = () => wrapper.findAll('.track-name').map(n => n.text())

it('shows the top 5 merged recommendations, leaving out songs already in the playlist', async () => {
  await render()
  expect(fetchDiscover.mock.calls.map(c => c[0])).toEqual(['m2', 'm1'])
  expect(wrapper.get('h2').text()).toBe('Recommended')
  expect(names()).toEqual(['Rec 3', 'Rec 1', 'Rec 2', 'Rec 4', 'Rec 5'])
})

it('hides itself when there are no public seeds, no results, or every request fails', async () => {
  await render({ tracks: [{ ...track(1), isPublic: false }] })
  expect(wrapper.find('.recs').exists()).toBe(false)
  expect(fetchDiscover).not.toHaveBeenCalled()
  wrapper.unmount()

  fetchDiscover.mockResolvedValue({ musicPieces: [] })
  await render()
  expect(wrapper.find('.recs').exists()).toBe(false)
  wrapper.unmount()

  fetchDiscover.mockRejectedValue(new Error('down'))
  await render()
  expect(wrapper.find('.recs').exists()).toBe(false)
})

it('plays a recommendation on its own', async () => {
  await render()
  const playPieces = vi.spyOn(useMusicStore(), 'playPieces').mockImplementation(() => {})
  await wrapper.get('button[aria-label="Play Rec 1 by Artist 1"]').trigger('click')
  expect(playPieces).toHaveBeenCalledWith([expect.objectContaining({ musicPieceId: 'r1' })])
})

it('lets an owner add a song here in one click, then fills its place with the next one', async () => {
  addPiecesToPlaylist.mockResolvedValue({ addedCount: 1, failedCount: 0, totalPieces: 3 })
  await render()
  await wrapper.get('button[aria-label="Add Rec 1 to this playlist"]').trigger('click')
  expect(wrapper.emitted('added')[0][0]).toMatchObject({ musicPieceId: 'r1' })
  expect(addPiecesToPlaylist).toHaveBeenCalledWith('p1', ['r1'])
  // The parent puts it in the playlist; the row keeps showing "Added" until it leaves
  await wrapper.setProps({ tracks: [track(1), track(2), piece(1)] })
  expect(wrapper.get('button[aria-label="Rec 1 added"]').text()).toBe('Added')
  await flushPromises()
  expect(useNotificationStore().message).toBe('Added "Rec 1" to this playlist.')
  vi.advanceTimersByTime(700)
  await flushPromises()
  expect(names()).toEqual(['Rec 3', 'Rec 2', 'Rec 4', 'Rec 5', 'Rec 6'])
})

it("takes an owner's song back out if adding it fails", async () => {
  addPiecesToPlaylist.mockRejectedValue(new Error('down'))
  await render()
  await wrapper.get('button[aria-label="Add Rec 1 to this playlist"]').trigger('click')
  await flushPromises()
  expect(wrapper.emitted('add-failed')[0][0]).toMatchObject({ musicPieceId: 'r1' })
  expect(useNotificationStore().message).toBe(`We couldn't add "Rec 1". Check that the server is running, then try again.`)
  expect(names()).toContain('Rec 1')
})

it("lets anyone else add a song to one of their own playlists from a menu", async () => {
  useUserStore().login({ username: 'me', userId: 'u1' })
  useEventsStore().library = [
    { playlistId: 'mine', playlistName: 'Late night', ownerUsername: 'me', totalPieces: 11, isSaved: false },
    { playlistId: 'theirs', playlistName: 'Not mine', ownerUsername: 'other', totalPieces: 3, isSaved: true },
  ]
  addPiecesToPlaylist.mockResolvedValue({ addedCount: 1, failedCount: 0, totalPieces: 12 })
  await render({ canEdit: false })
  const add = wrapper.get('button[aria-label="Add Rec 1 to one of your playlists"]')
  expect(add.attributes('aria-haspopup')).toBe('menu')
  await add.trigger('click')
  const items = wrapper.findAll('[role="menuitem"]')
  expect(items.map(i => i.text())).toEqual(['Late night11 songs'])
  expect(document.activeElement).toBe(items[0].element)
  await items[0].trigger('click')
  await flushPromises()
  expect(addPiecesToPlaylist).toHaveBeenCalledWith('mine', ['r1'])
  expect(wrapper.emitted('added')).toBeUndefined()
  expect(useNotificationStore().message).toBe('Added "Rec 1" to Late night.')
  expect(wrapper.find('[role="menu"]').exists()).toBe(false)
  // It stays recommended here, and the menu marks where it went
  expect(names()).toContain('Rec 1')
  expect(wrapper.get('button[aria-label="Rec 1 added"]').text()).toBe('Added')
  vi.advanceTimersByTime(1500)
  await flushPromises()
  await wrapper.get('button[aria-label="Add Rec 1 to one of your playlists"]').trigger('click')
  expect(wrapper.find('[role="menuitem"] .tick').exists()).toBe(true)
})

it('says so when the song is already in the picked playlist, and closes the menu with Escape', async () => {
  useUserStore().login({ username: 'me', userId: 'u1' })
  useEventsStore().library = [{ playlistId: 'mine', playlistName: 'Late night', ownerUsername: 'me', totalPieces: 11, isSaved: false }]
  addPiecesToPlaylist.mockResolvedValue({ addedCount: 0, failedCount: 1, totalPieces: 11 })
  await render({ canEdit: false })
  await wrapper.get('.add-wrap button').trigger('click')
  await wrapper.get('[role="menuitem"]').trigger('click')
  await flushPromises()
  expect(useNotificationStore().message).toBe(`"Rec 3" is already in Late night, or can't be added to it.`)
  await wrapper.get('.add-wrap button').trigger('click')
  await wrapper.get('[role="menu"]').trigger('keydown', { key: 'Escape' })
  expect(wrapper.find('[role="menu"]').exists()).toBe(false)
})

it('tells someone with no playlists of their own, and hides Add from logged-out visitors', async () => {
  useUserStore().login({ username: 'me', userId: 'u1' })
  useEventsStore().library = []
  await render({ canEdit: false })
  await wrapper.get('.add-wrap button').trigger('click')
  expect(wrapper.get('[role="menu"]').text()).toContain("You don't have any playlists yet.")
  wrapper.unmount()

  useUserStore().logout()
  await render({ canEdit: false })
  expect(wrapper.find('.add-btn').exists()).toBe(false)
  expect(names()).toHaveLength(5)
})

it('asks again on Refresh, showing placeholder rows meanwhile', async () => {
  await render()
  expect(fetchDiscover).toHaveBeenCalledTimes(2)
  let release
  const held = new Promise((resolve) => { release = resolve })
  const answer = fetchDiscover.getMockImplementation()
  fetchDiscover.mockImplementation(async (id) => { await held; return answer(id) })
  await wrapper.get('.refresh').trigger('click')
  expect(wrapper.findAll('.skeleton')).toHaveLength(5)
  release()
  await flushPromises()
  expect(fetchDiscover).toHaveBeenCalledTimes(4)
  expect(names()).toHaveLength(5)
})
