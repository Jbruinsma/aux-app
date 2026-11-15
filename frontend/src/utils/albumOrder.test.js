import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { arrangeAlbum, baseTitleKey, fetchAlbumTracklist, matchTracks, pickAlbum, titleKey } from './albumOrder.js'

// Apple's "good kid, m.A.A.d city (Deluxe Version)", trimmed
const gkmc = [
  [1, 2, 'Bitch, Don’t Kill My Vibe'],
  [1, 5, 'Money Trees (feat. Jay Rock)'],
  [1, 9, 'Swimming Pools (Drank) [Extended Version]'],
  [1, 10, "Sing About Me, I'm Dying of Thirst"],
  [1, 13, 'Bitch, Don’t Kill My Vibe (feat. JAŸ-Z) [Remix]'],
  [1, 14, 'Black Boy Fly (Bonus Track)'],
  [1, 17, 'Swimming Pools (Drank)'],
].map(([disc, track, title]) => ({ disc, track, title }))
const piece = (name, extra = {}) => ({ musicPieceId: name, name, ...extra })

beforeEach(() => localStorage.clear())
afterEach(() => vi.unstubAllGlobals())

it('leaves credits out of titles, and brackets out of bare titles', () => {
  expect(titleKey('Money Trees (feat. Jay Rock)')).toBe('moneytrees')
  expect(titleKey('Bitch, Don’t Kill My Vibe (feat. JAŸ-Z) [Remix]')).toBe('bitchdontkillmyviberemix')
  expect(titleKey('Poetic Justice ft. Drake')).toBe('poeticjustice')
  expect(titleKey('Swimming Pools (Drank) [Extended Version]')).toBe('swimmingpoolsdrank')
  expect(titleKey('Black Boy Fly (Bonus Track)')).toBe('blackboyfly')
  expect(titleKey('Let It Happen (2015 Remaster)')).toBe('letithappen')
  expect(titleKey('Ivy (Live)')).toBe('ivylive')
  expect(baseTitleKey('Swimming Pools (Drank) [Extended Version]')).toBe('swimmingpools')
  expect(baseTitleKey('Let It Happen - Remastered 2015')).toBe('letithappen')
  expect(titleKey('夜に駆ける')).toBe('夜に駆ける')
})

it('matches exact titles before bare ones, so the original and the remix each find their own track', () => {
  const pieces = [piece('Bitch Don’t Kill My Vibe (Remix)'), piece("Bitch Don't Kill My Vibe"), piece('Swimming Pools (Drank)'), piece('Not On It'), piece('Black Boy Fly'), piece('Swimming Pools (Radio Edit)')]
  const matches = matchTracks(pieces, gkmc)
  // The album's Swimming Pools takes track 9; the radio edit falls back to the bare title and gets the bonus copy
  expect([...matches].map(([index, track]) => [index, track.track]).sort((a, b) => a[0] - b[0])).toEqual([[0, 13], [1, 2], [2, 9], [4, 14], [5, 17]])
})

it('puts matched songs under Disc 1 in tracklist order with their track numbers, and the rest after them as the next disc', () => {
  const pieces = [piece('Swimming Pools (Drank)'), piece('Extra'), piece('Money Trees'), piece('Bitch, Don’t Kill My Vibe')]
  const { ordered, groups, inOrder } = arrangeAlbum(pieces, gkmc)
  expect(inOrder).toBe(true)
  expect(ordered.map((p) => p.name)).toEqual(['Bitch, Don’t Kill My Vibe', 'Money Trees', 'Swimming Pools (Drank)', 'Extra'])
  expect(groups.map(({ label, numbers, start }) => [label, numbers, start])).toEqual([['Disc 1', [2, 5, 9], 0], ['Disc 2', [1], 3]])
})

it('splits a multi-disc album into a group per disc', () => {
  const tracks = [{ disc: 1, track: 1, title: 'A' }, { disc: 1, track: 2, title: 'B' }, { disc: 2, track: 1, title: 'C' }]
  const { groups } = arrangeAlbum([piece('C'), piece('A')], tracks)
  expect(groups.map(({ label, pieces, numbers }) => [label, pieces.map((p) => p.name), numbers])).toEqual([['Disc 1', ['A'], [1]], ['Disc 2', ['C'], [1]]])
})

it("uses the backend's track numbers once pieces carry them, and keeps the order given with neither", () => {
  const pieces = [piece('B', { trackNumber: 2 }), piece('C', { discNumber: 2, trackNumber: 1 }), piece('A', { trackNumber: 1 })]
  expect(arrangeAlbum(pieces, gkmc).ordered.map((p) => p.name)).toEqual(['A', 'B', 'C'])
  expect(arrangeAlbum(pieces, gkmc).groups.map((group) => group.label)).toEqual(['Disc 1', 'Disc 2'])
  const plain = [piece('Z'), piece('Y')]
  expect(arrangeAlbum(plain, null)).toEqual({ ordered: plain, groups: [{ key: 'all', label: null, pieces: plain, numbers: null, start: 0 }], inOrder: false })
})

it("picks the artist's album with the same edition, preferring originals over clean versions", () => {
  const album = (collectionId, collectionName, extra = {}) => ({ collectionId, collectionName, artistName: 'Kendrick Lamar', trackCount: 13, collectionExplicitness: 'explicit', ...extra })
  const results = [
    album(1, 'good kid, m.A.A.d city'),
    album(2, 'good kid, m.A.A.d city (Deluxe Version)', { collectionExplicitness: 'cleaned' }),
    album(3, 'good kid, m.A.A.d city (Deluxe Version)'),
    album(4, 'good kid, m.A.A.d city (Deluxe Version)', { artistName: 'Someone Else' }),
  ]
  expect(pickAlbum(results, 'Kendrick Lamar', 'good kid, m.A.A.d city (Deluxe)').collectionId).toBe(3)
  expect(pickAlbum(results, 'Kendrick Lamar', 'Good Kid, M.A.A.D City').collectionId).toBe(1)
  expect(pickAlbum(results, 'Kendrick Lamar', 'DAMN.')).toBeNull()
})

it('finds the album through the artist, looks it up once, remembers it in this browser, and gives up quietly when Apple fails', async () => {
  const answers = {
    musicArtist: { results: [{ artistId: 9, artistName: 'Frank Ocean Tribute' }, { artistId: 3, artistName: 'Frank Ocean' }] },
    album: { results: [{ wrapperType: 'artist' }, { wrapperType: 'collection', collectionId: 6, collectionName: 'Nikes - Single', artistName: 'Frank Ocean' }, { wrapperType: 'collection', collectionId: 7, collectionName: 'Blonde', artistName: 'Frank Ocean', trackCount: 2 }] },
    song: { results: [{ wrapperType: 'collection' }, { wrapperType: 'track', kind: 'song', discNumber: 1, trackNumber: 2, trackName: 'Ivy' }, { wrapperType: 'track', kind: 'song', discNumber: 1, trackNumber: 1, trackName: 'Nikes' }] },
  }
  const fetch = vi.fn(async (url) => ({ ok: true, json: async () => answers[new URL(url).searchParams.get('entity')] }))
  vi.stubGlobal('fetch', fetch)
  const expected = [{ disc: 1, track: 1, title: 'Nikes' }, { disc: 1, track: 2, title: 'Ivy' }]
  expect(await fetchAlbumTracklist('Frank Ocean', 'Blonde')).toEqual(expected)
  expect(await fetchAlbumTracklist('Frank Ocean', 'Blonde')).toEqual(expected)
  const asked = fetch.mock.calls.map(([url]) => Object.fromEntries(new URL(url).searchParams))
  expect(asked.map(({ entity, id }) => [entity, id])).toEqual([['musicArtist', undefined], ['album', '3'], ['song', '7']])

  vi.stubGlobal('fetch', vi.fn(async () => ({ ok: false, status: 403 })))
  expect(await fetchAlbumTracklist('Tame Impala', 'Currents')).toBeNull()
  expect(localStorage.getItem('aux-album-order:tameimpala|currents')).toBeNull()
})
