// Placeholder rows for Recent listening and Top artists, so the dashboard layout can be judged with real-looking artwork.
// While this is true they replace the account's own rows; set it to false to show real data. Shapes match
// GET /api/users/recent-listening and GET /api/users/top-artists. Artwork is hotlinked from Deezer's CDN for local
// previews only, so turn this off before deploying
export const DASHBOARD_PREVIEW = true

const art = (path) => `https://cdn-images.dzcdn.net/images/${path}/500x500-000000-80-0-0.jpg`

const ARTISTS = {
  kendrick: { artistId: 'preview-kendrick', artistName: 'Kendrick Lamar', artistPfpUrl: art('artist/be0a7c550567f4af0ed202d7235b74d6') },
  frank: { artistId: 'preview-frank', artistName: 'Frank Ocean', artistPfpUrl: art('artist/882155c08dc31d6464d6d580083c968c') },
  sza: { artistId: 'preview-sza', artistName: 'SZA', artistPfpUrl: art('artist/8ced041da2bed70d5715f0860956169b') },
  tame: { artistId: 'preview-tame', artistName: 'Tame Impala', artistPfpUrl: art('artist/879015e713cc6ad6ffaeec154c027505') },
  yeat: { artistId: 'preview-yeat', artistName: 'Yeat', artistPfpUrl: art('artist/87f49e3c06e20dcb431717d5ed502bf6') },
  kanye: { artistId: 'preview-kanye', artistName: 'Kanye West', artistPfpUrl: art('artist/bb76c2ee3b068726ab4c37b0aabdb57a') },
  travis: { artistId: 'preview-travis', artistName: 'Travis Scott', artistPfpUrl: art('artist/8d8316146026d7e6ce377e314536df62') },
  drake: { artistId: 'preview-drake', artistName: 'Drake', artistPfpUrl: art('artist/eb0ed5b21d1ea5af021fc074ded0e91f') },
  juice: { artistId: 'preview-juice', artistName: 'Juice WRLD', artistPfpUrl: art('artist/2ecf2968fbbc8eb042b241b8e60e5d7e') },
  daniel: { artistId: 'preview-daniel', artistName: 'Daniel Caesar', artistPfpUrl: art('artist/973809864ad1c52d8c61238662400089') },
}

const piece = (id, name, cover, artist) => ({
  musicPiece: { musicPieceId: `preview-${id}`, name, coverUrl: art(`cover/${cover}`), artistSummary: ARTISTS[artist], isFavorite: false },
  playlistId: null,
})

export const PREVIEW_RECENTS = [
  piece('nights', 'Nights', 'f798a866107715dd6dc1049e498ce21f', 'frank'),
  piece('humble', 'HUMBLE.', '7ce6b8452fae425557067db6e6a1cad5', 'kendrick'),
  piece('snooze', 'Snooze', '328d68300e654b21831b261e413780e0', 'sza'),
  piece('let-it-happen', 'Let It Happen', 'de5b9b704cd4ec36f8bf49beb3e17ba2', 'tame'),
  piece('runaway', 'Runaway', '742aba8510ba803bea51d304cf2ca786', 'kanye'),
  piece('get-busy', 'Gët Busy', '14e37e05b1acd67c06b0408ef1146a26', 'yeat'),
  piece('sicko-mode', 'SICKO MODE', '0b08015216f975dc7fb5be3d5dcc4d88', 'travis'),
  piece('passionfruit', 'Passionfruit', '8f0187ad83cdd1f47f1c55420f9df227', 'drake'),
  piece('robbery', 'Robbery', 'f987eca394bfb6b4c2839f3e57476bb2', 'juice'),
  piece('best-part', 'Best Part', '4dff56488d13d0b5e96d93d895c9624b', 'daniel'),
]

export const PREVIEW_TOP_ARTISTS = [
  ARTISTS.frank, ARTISTS.kendrick, ARTISTS.yeat, ARTISTS.kanye, ARTISTS.sza,
  ARTISTS.tame, ARTISTS.travis, ARTISTS.drake, ARTISTS.juice, ARTISTS.daniel,
]
