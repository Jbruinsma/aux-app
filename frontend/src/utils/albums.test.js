import { beforeEach, expect, it, vi } from 'vitest'
import { request } from '@/utils/api.js'
import { arrangeByAlbum, findOrCreateAlbum, setPieceAlbum, updateAlbum } from './albums.js'

vi.mock('@/utils/api.js', () => ({ fetchAPI: vi.fn(), request: vi.fn() }))

const piece = (id, album = null) => ({ musicPieceId: id, name: `Song ${id}`, album })
const fields = (form) => Object.fromEntries([...form.keys()].map((key) => [key, typeof form.get(key) === 'string' ? form.get(key) : 'file']))

beforeEach(() => request.mockResolvedValue({}))

it("puts each album's loaded songs in its section, and never lists a song on an album as a single", () => {
  const one = { albumId: 'one', albumName: 'One' }
  const two = { albumId: 'two', albumName: 'Two' }
  const onOne = piece('m1', { albumId: 'one', albumName: 'One' })
  // Two's list hasn't loaded yet, but its song still isn't a single
  const onTwo = piece('m2', { albumId: 'two', albumName: 'Two' })
  const { albums, singles } = arrangeByAlbum([one, two], { one: { items: [onOne] } }, [onOne, onTwo, piece('m3')])
  expect(albums.map((section) => [section.album.albumId, section.pieces.map((p) => p.musicPieceId)])).toEqual([['one', ['m1']], ['two', []]])
  expect(singles.map((single) => single.musicPieceId)).toEqual(['m3'])
})

it('sends albumName and the cover only when there is one', async () => {
  const cover = new File(['c'], 'c.png', { type: 'image/png' })
  await findOrCreateAlbum('a1', { albumName: 'Blonde', coverImage: cover })
  await updateAlbum('al1', { albumName: 'Blond' })
  expect(request.mock.calls.map(([method, url, form]) => [method, url.split('/api/')[1], fields(form)])).toEqual([
    ['POST', 'artists/a1/albums', { albumName: 'Blonde', coverImage: 'file' }],
    ['PUT', 'albums/al1', { albumName: 'Blond' }],
  ])
})

it('moves a song onto an album with its cover choice, or makes it a single', async () => {
  await setPieceAlbum('m1', 'al1', false)
  await setPieceAlbum('m1', null)
  expect(request.mock.calls.map(([method, url, form]) => [method, url.split('/api/')[1], fields(form)])).toEqual([
    ['PUT', 'music-pieces/m1', { albumId: 'al1', useAlbumCover: 'false' }],
    ['PUT', 'music-pieces/m1', { removeFromAlbum: 'true' }],
  ])
})
