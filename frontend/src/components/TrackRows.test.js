import { beforeEach, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { mount } from '@vue/test-utils'
import { useMusicStore } from '@/stores/music.js'
import TrackRows from './TrackRows.vue'

vi.mock('@/utils/api.js', () => ({ fetchAPI: vi.fn(), postToAPI: vi.fn(), request: vi.fn() }))

const piece = (n) => ({ musicPieceId: `m${n}`, name: `Song ${n}`, coverUrl: null, artistSummary: { artistId: 'a1', artistName: 'Frank Ocean' } })

beforeEach(() => setActivePinia(createPinia()))

function render() {
  return mount(TrackRows, {
    props: { pieces: [piece(1), piece(2)], label: 'Songs' },
    global: { stubs: { RouterLink: { template: '<a><slot /></a>' } } },
  })
}

it('shows pause on the playing song, pauses and resumes it, and plays the others', async () => {
  const music = useMusicStore()
  music.currentMusicPiece.uuid = 'm2'
  music.currentMusicPiece.mp3File = 'https://audio/m2.mp3'
  music.isPlaying = true
  const wrapper = render()
  await wrapper.vm.$nextTick()
  const buttons = () => wrapper.findAll('button.play')
  expect(buttons().map((button) => button.attributes('aria-label'))).toEqual(['Play Song 1 by Frank Ocean', 'Pause Song 2 by Frank Ocean'])

  await buttons()[1].trigger('click')
  expect(music.isPlaying).toBe(false)
  expect(buttons()[1].attributes('aria-label')).toBe('Play Song 2 by Frank Ocean')
  await buttons()[1].trigger('click')
  expect(music.isPlaying).toBe(true)
  expect(wrapper.emitted('play')).toBeUndefined()

  await buttons()[0].trigger('click')
  expect(wrapper.emitted('play')).toEqual([[0]])
})

it('links each title to its song page only when linked', () => {
  const linkStub = { props: ['to'], template: '<a class="stub-link" :data-to="JSON.stringify(to)"><slot /></a>' }
  const mountWith = (linked) => mount(TrackRows, { props: { pieces: [piece(1)], label: 'Songs', linked }, global: { stubs: { RouterLink: linkStub } } })
  const titleLink = (wrapper) => wrapper.findAll('a.stub-link').find((a) => a.text() === 'Song 1')
  expect(JSON.parse(titleLink(mountWith(true)).attributes('data-to'))).toEqual({ name: 'Piece', params: { musicPieceId: 'm1' } })
  expect(titleLink(mountWith(false))).toBeUndefined()
})
