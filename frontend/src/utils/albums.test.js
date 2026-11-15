import { beforeEach, expect, it } from 'vitest'
import { addToAlbum, arrangeByAlbum, updateAlbum } from './albums.js'

const piece = (id) => ({ musicPieceId: id, name: `Song ${id}`, coverUrl: `/uploads/${id}.webp` })
const album = (albumId, ids) => ({ albumId, name: albumId, coverUrl: 'data:cover', createdBy: 'u1', tracks: ids.map((id) => ({ musicPiece: piece(id), useAlbumCover: true })) })

beforeEach(() => {
  localStorage.clear()
  localStorage.setItem('aux-albums-a1', JSON.stringify([album('one', ['m1']), album('two', ['m2'])]))
})

it("refuses a song that's already on another album instead of moving it", async () => {
  await expect(updateAlbum('a1', 'two', { name: 'two', tracks: [{ musicPiece: piece('m2') }, { musicPiece: piece('m1') }] }))
    .rejects.toMatchObject({ status: 409, code: 'ALREADY_ON_ALBUM' })
  await expect(addToAlbum('a1', 'two', piece('m1'))).rejects.toMatchObject({ code: 'ALREADY_ON_ALBUM' })
  expect(JSON.parse(localStorage.getItem('aux-albums-a1'))[0].tracks).toHaveLength(1)
  await addToAlbum('a1', 'two', piece('m3'))
  expect(JSON.parse(localStorage.getItem('aux-albums-a1'))[1].tracks.map((track) => track.musicPiece.musicPieceId)).toEqual(['m2', 'm3'])
})

it('shows album tracks with the album cover and leaves the rest as singles', () => {
  const { albums, singles } = arrangeByAlbum([album('one', ['m1'])], [piece('m1'), piece('m4')])
  expect(albums[0].pieces[0].coverUrl).toBe('data:cover')
  expect(singles.map((single) => single.musicPieceId)).toEqual(['m4'])
})
