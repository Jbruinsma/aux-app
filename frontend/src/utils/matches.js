// Matches arrive on the live event stream (the `matches` event, MatchesResponse):
// { matches: [{ user: { userId, username, pfpUrl }, compatibility: 0-100, sharedArtists: [ArtistSummary], sharedTags }] }

// The style guide's levels; each one fills one more of the meter's five segments
const LEVELS = [
  { min: 80, label: 'Super' },
  { min: 60, label: 'Very high' },
  { min: 40, label: 'High' },
  { min: 20, label: 'Medium' },
  { min: 0, label: 'Low' },
]

export function compatibilityLevel(score) {
  const index = LEVELS.findIndex((level) => score >= level.min)
  return { label: LEVELS[index].label, filled: LEVELS.length - index }
}

// What follows name `index` of `count` in "A, B and C"
export function nameSeparator(index, count) {
  if (index === count - 1) return ''
  return index === count - 2 ? ' and ' : ', '
}
