import { expect, it } from 'vitest'
import { addablePlaylists, mergeRecommendations, pickSeeds } from './recommendations.js'

const piece = (id, extra = {}) => ({ musicPieceId: id, name: id, ...extra })

it('seeds with the newest public, playable songs, newest first', () => {
  const tracks = [piece('a'), piece('b'), piece('c', { isPublic: false }), piece('d', { isAvailable: false }), piece('e'), piece('f')]
  expect(pickSeeds(tracks).map(p => p.musicPieceId)).toEqual(['f', 'e', 'b'])
  expect(pickSeeds(tracks, 1).map(p => p.musicPieceId)).toEqual(['f'])
  expect(pickSeeds([piece('x', { isPublic: false })])).toEqual([])
})

it('ranks songs that come up for several seeds first, then by position', () => {
  const lists = [
    [piece('a'), piece('b'), piece('c')],
    [piece('d'), piece('b'), piece('e')],
  ]
  expect(mergeRecommendations(lists).map(p => p.musicPieceId)).toEqual(['b', 'a', 'd', 'c', 'e'])
})

it('drops songs already in the playlist and keeps the first copy of each song', () => {
  const lists = [[piece('a'), piece('b')], [piece('b'), piece('c')]]
  const merged = mergeRecommendations(lists, new Set(['a']))
  expect(merged.map(p => p.musicPieceId)).toEqual(['b', 'c'])
  expect(merged[0]).toBe(lists[0][1])
  expect(mergeRecommendations([])).toEqual([])
})

it('offers owned playlists, not saved ones or the current one, until the library says who can edit', () => {
  const library = [
    { playlistId: 'p1', ownerUsername: 'Me', isSaved: false },
    { playlistId: 'p2', ownerUsername: 'other', isSaved: false },
    { playlistId: 'p3', ownerUsername: 'other', isSaved: true },
    { playlistId: 'p4', ownerUsername: 'me', isSaved: false },
  ]
  expect(addablePlaylists(library, 'me', 'p4').map(p => p.playlistId)).toEqual(['p1'])
  expect(addablePlaylists(null, 'me')).toEqual([])
  expect(addablePlaylists(library, null)).toEqual([])
})

it('uses the access field once the library has it, so edited playlists count too', () => {
  const library = [
    { playlistId: 'p1', ownerUsername: 'me', access: 'OWNER' },
    { playlistId: 'p2', ownerUsername: 'other', access: 'EDITOR' },
    { playlistId: 'p3', ownerUsername: 'other', access: 'LISTENER' },
    { playlistId: 'p4', ownerUsername: 'other', access: null, isSaved: true },
    { playlistId: 'p5', ownerUsername: 'other', access: 'EDITOR', isSaved: true },
  ]
  expect(addablePlaylists(library, 'me').map(p => p.playlistId)).toEqual(['p1', 'p2', 'p5'])
})
