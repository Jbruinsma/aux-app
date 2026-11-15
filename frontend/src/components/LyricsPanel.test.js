import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import LyricsPanel from './LyricsPanel.vue'

const synced = '[00:01.00] First line\n[00:05.00] Second line\n[00:09.00] Third line'

let wrapper
beforeEach(() => {
  localStorage.clear()
  Element.prototype.scrollTo = vi.fn()
  vi.stubGlobal('fetch', vi.fn(async () => ({ ok: true, status: 200, json: async () => ({ duration: 200, syncedLyrics: synced, plainLyrics: 'x' }) })))
})
afterEach(() => {
  wrapper?.unmount()
  vi.unstubAllGlobals()
})

async function render(props = {}) {
  wrapper = mount(LyricsPanel, { props: { open: true, piece: { uuid: `m${Math.random()}`, title: 'Song', artist: 'Artist' }, position: 6, duration: 200, ...props } })
  await flushPromises()
}

it('highlights the line being sung, and seeks to a line when it is clicked', async () => {
  await render()
  const lines = wrapper.findAll('.line')
  expect(lines.map((line) => line.text())).toEqual(['First line', 'Second line', 'Third line'])
  expect(wrapper.get('.line.now').text()).toBe('Second line')
  expect(lines[0].classes()).toContain('past')
  await lines[2].trigger('click')
  expect(wrapper.emitted('seek')).toEqual([[9]])
  await wrapper.setProps({ position: 9.5 })
  expect(wrapper.get('.line.now').text()).toBe('Third line')
})

it('stops following when scrolled by hand, and Sync brings it back', async () => {
  await render()
  expect(wrapper.find('.resync').exists()).toBe(false)
  await wrapper.get('.body').trigger('wheel')
  expect(wrapper.get('.resync').text()).toBe('Sync')
  Element.prototype.scrollTo.mockClear()
  await wrapper.get('.resync').trigger('click')
  expect(wrapper.find('.resync').exists()).toBe(false)
  expect(Element.prototype.scrollTo).toHaveBeenCalled()
})

it('has no timing controls or credit line', async () => {
  await render()
  expect(wrapper.find('.timing').exists()).toBe(false)
  expect(wrapper.text()).not.toContain('LRCLIB')
})

it('says so when LRCLIB has nothing for the song', async () => {
  fetch.mockImplementation(async () => ({ ok: false, status: 404, json: async () => ({}) }))
  await render()
  expect(wrapper.text()).toContain('No lyrics found for this song')
  expect(wrapper.get('.body').classes()).toContain('centered')
})

it('shows the cover, title and artist at the top on the phone page', async () => {
  await render({ piece: { uuid: 'm-head', title: 'Summer Friends', artist: 'Chance the Rapper', cover: 'https://img/c.webp' }, isPlaying: true })
  expect(wrapper.get('.head .art').attributes('src')).toBe('https://img/c.webp')
  expect(wrapper.get('.head .title').text()).toBe('Summer Friends')
  expect(wrapper.get('.head .artist').text()).toBe('Chance the Rapper')
  await wrapper.get('.art-btn').trigger('click')
  expect(wrapper.emitted('close')).toHaveLength(1)
})
