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

it('stops following when scrolled by hand, and Sync to song brings it back', async () => {
  await render()
  expect(wrapper.find('.resync').exists()).toBe(false)
  await wrapper.get('.body').trigger('wheel')
  expect(wrapper.get('.resync').text()).toBe('Sync to song')
  Element.prototype.scrollTo.mockClear()
  await wrapper.get('.resync').trigger('click')
  expect(wrapper.find('.resync').exists()).toBe(false)
  expect(Element.prototype.scrollTo).toHaveBeenCalled()
})

it('nudges the timing for the song and keeps it', async () => {
  const piece = { uuid: 'm-nudge', title: 'Song', artist: 'Artist' }
  await render({ piece, position: 5.2 })
  expect(wrapper.get('.line.now').text()).toBe('Second line')
  await wrapper.findAll('.timing button').find((button) => button.text() === 'Later').trigger('click')
  expect(wrapper.get('.timing-label').text()).toBe('Lyrics 0.5s later')
  expect(wrapper.get('.line.now').text()).toBe('First line')
  expect(localStorage.getItem('aux-lyrics-offset-m-nudge')).toBe('0.5')
  await wrapper.findAll('.line')[1].trigger('click')
  expect(wrapper.emitted('seek')).toEqual([[5.5]])
})

it('says so when LRCLIB has nothing for the song', async () => {
  fetch.mockImplementation(async () => ({ ok: false, status: 404, json: async () => ({}) }))
  await render()
  expect(wrapper.text()).toContain('No lyrics found for this song')
})
