import { afterEach, expect, it } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import MobilePlayer from '@/components/MobilePlayer.vue'

const Stub = { template: '<div />' }

function makeRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: Stub },
      { path: '/:username', name: 'Profile', component: Stub },
      { path: '/artist/:artistId', name: 'Artist', component: Stub },
      { path: '/playlist/:username/:id', name: 'Playlist', component: Stub },
    ],
  })
}

const piece = { uuid: 'm1', title: 'Violent Crimes', artist: 'Kanye West', artistId: 'a1', cover: '' }
const uploader = { userId: 'u1', username: 'test1', pfpUrl: null }
const playlistLink = { to: { name: 'Playlist', params: { username: 'owner', id: 'p1' } }, name: 'Road trip' }

let wrapper
afterEach(() => wrapper?.unmount())

async function render(props = {}) {
  const router = makeRouter()
  router.push('/')
  await router.isReady()
  wrapper = mount(MobilePlayer, {
    props: { piece, uploader, playlistLink, canPlay: true, canSkip: true, canShuffle: true, ...props },
    global: { plugins: [router] },
    attachTo: document.body,
  })
  return router
}

it('opens the full player from the mini player and closes it with the chevron', async () => {
  await render()
  expect(wrapper.get('.mini-open').attributes('aria-label')).toBe('Open player: Violent Crimes by Kanye West')
  expect(wrapper.get('.sheet').classes()).not.toContain('open')
  await wrapper.get('.mini-open').trigger('click')
  expect(wrapper.get('.sheet').classes()).toContain('open')
  expect(document.body.classList.contains('player-sheet-open')).toBe(true)
  await wrapper.get('button[aria-label="Close player"]').trigger('click')
  expect(wrapper.get('.sheet').classes()).not.toContain('open')
  expect(document.body.classList.contains('player-sheet-open')).toBe(false)
})

it('lists artist, playlist and uploader in the song menu, and navigates and closes on a pick', async () => {
  const router = await render()
  await wrapper.get('.mini-open').trigger('click')
  await wrapper.get('.meta-btn').trigger('click')
  const items = wrapper.findAll('[role="menuitem"]')
  expect(items.map(i => i.text())).toEqual(['Go to artistKanye West', 'Go to playlistRoad trip', 'Go to uploadertest1'])
  await items[1].trigger('click')
  await flushPromises()
  expect(router.currentRoute.value.fullPath).toBe('/playlist/owner/p1')
  expect(wrapper.get('.sheet').classes()).not.toContain('open')
})

it('leaves out menu rows it has no link for, and shows no menu when there are none', async () => {
  await render({ playlistLink: null })
  expect(wrapper.findAll('[role="menuitem"]').map(i => i.text())).toEqual(['Go to artistKanye West', 'Go to uploadertest1'])
  wrapper.unmount()
  await render({ playlistLink: null, uploader: null, piece: { ...piece, artistId: '' } })
  expect(wrapper.find('.meta-btn').exists()).toBe(false)
  expect(wrapper.get('.sheet-title').text()).toBe('Violent Crimes')
})

it('sends the player controls up to BottomPlayer', async () => {
  await render()
  await wrapper.get('.mini-play').trigger('click')
  await wrapper.get('.mini button[aria-label="Next"]').trigger('click')
  await wrapper.get('.sheet button[aria-label="Previous"]').trigger('click')
  await wrapper.get('button[aria-label="Shuffle"]').trigger('click')
  await wrapper.get('input[aria-label="Seek"]').setValue('42')
  expect(Object.keys(wrapper.emitted())).toEqual(expect.arrayContaining(['toggle-play', 'next', 'prev', 'shuffle', 'seek']))
  expect(wrapper.emitted('seek').at(-1)).toEqual([42])
})
