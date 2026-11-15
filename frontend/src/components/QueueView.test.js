import { afterEach, expect, it } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import QueueView from '@/components/QueueView.vue'

const Stub = { template: '<div />' }

function makeRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: Stub },
      { path: '/playlist/:username/:id', name: 'Playlist', component: Stub },
    ],
  })
}

const overview = (n) => ({ musicPieceId: `m${n}`, name: `Song ${n}`, coverUrl: null, artistSummary: { artistName: `Artist ${n}` }, uploader: { username: 'up' } })
const queue = [1, 2, 3, 4, 5].map(overview)
const piece = { uuid: 'm3', title: 'Song 3', artist: 'Artist 3', cover: '' }
const playlistLink = { to: { name: 'Playlist', params: { username: 'owner', id: 'p1' } }, name: 'Road trip' }

let wrapper
afterEach(() => wrapper?.unmount())

async function render(props = {}) {
  const router = makeRouter()
  router.push('/')
  await router.isReady()
  wrapper = mount(QueueView, {
    props: { open: true, queue, index: 2, piece, playlistLink, canPlay: true, canSkip: true, canShuffle: true, ...props },
    global: { plugins: [router] },
    attachTo: document.body,
  })
  await flushPromises()
  return router
}

const slots = () => wrapper.findAll('.slot')

it('shows played songs before the current one and up next after it', async () => {
  await render()
  expect(slots().map(s => s.attributes('aria-label'))).toEqual([
    'Play Song 1 by Artist 1',
    'Play Song 2 by Artist 2',
    'Now playing: Song 3 by Artist 3',
    'Play Song 4 by Artist 4',
    'Play Song 5 by Artist 5',
  ])
  expect(slots().map(s => s.classes().includes('past'))).toEqual([true, true, false, false, false])
  expect(slots()[2].attributes('aria-current')).toBe('true')
  expect(wrapper.get('.label').text()).toBe('Playing from Road trip')
})

it('plays a cover when it is clicked, past or upcoming, but not the one playing', async () => {
  await render()
  await slots()[0].trigger('click')
  await slots()[2].trigger('click')
  await slots()[4].trigger('click')
  expect(wrapper.emitted('play-at')).toEqual([[0], [4]])
})

it('browses with the arrows and keys without changing the song, and comes back', async () => {
  await render()
  const view = () => wrapper.get('.queue-view').attributes('style')
  expect(view()).toContain('--view: 2')
  expect(wrapper.get('.queue-view').classes()).not.toContain('browsing')
  await wrapper.get('button[aria-label="Show later songs"]').trigger('click')
  await wrapper.get('.queue-view').trigger('keydown', { key: 'ArrowRight' })
  expect(view()).toContain('--view: 4')
  expect(wrapper.get('button[aria-label="Show later songs"]').attributes('disabled')).toBeDefined()
  expect(wrapper.get('.queue-view').classes()).toContain('browsing')
  expect(wrapper.emitted('play-at')).toBeUndefined()
  await wrapper.get('.back').trigger('click')
  expect(view()).toContain('--view: 2')
})

it('recenters on the new song when the queue moves on', async () => {
  await render()
  await wrapper.get('.queue-view').trigger('keydown', { key: 'ArrowLeft' })
  await wrapper.setProps({ index: 3 })
  expect(wrapper.get('.queue-view').attributes('style')).toContain('--view: 3')
})

it('shows the current song on its own when there is no queue', async () => {
  await render({ queue: [], index: null })
  expect(slots().map(s => s.attributes('aria-label'))).toEqual(['Now playing: Song 3 by Artist 3'])
})

it('closes with Escape, the chevron, or by following the playlist link', async () => {
  const router = await render()
  await wrapper.get('.queue-view').trigger('keydown', { key: 'Escape' })
  await wrapper.get('button[aria-label="Close queue"]').trigger('click')
  expect(wrapper.emitted('close')).toHaveLength(2)
  await wrapper.get('.label a').trigger('click')
  await flushPromises()
  expect(router.currentRoute.value.fullPath).toBe('/playlist/owner/p1')
  expect(wrapper.emitted('close')).toHaveLength(3)
})

it('sends the controls up to BottomPlayer', async () => {
  await render()
  await wrapper.get('button[aria-label="Play"]').trigger('click')
  await wrapper.get('button[aria-label="Previous"]').trigger('click')
  await wrapper.get('button[aria-label="Next"]').trigger('click')
  await wrapper.get('button[aria-label="Shuffle"]').trigger('click')
  await wrapper.get('input[aria-label="Seek"]').setValue(12)
  await wrapper.get('input[aria-label="Volume"]').setValue(0.25)
  for (const event of ['toggle-play', 'prev', 'next', 'shuffle']) expect(wrapper.emitted(event)).toHaveLength(1)
  expect(wrapper.emitted('seek')[0]).toEqual([12])
  expect(wrapper.emitted('volume')[0]).toEqual([0.25])
})

it('is inert while closed', async () => {
  await render({ open: false })
  expect(wrapper.get('.queue-view').attributes('inert')).toBeDefined()
  await wrapper.setProps({ open: true })
  expect(wrapper.get('.queue-view').attributes('inert')).toBeUndefined()
})
