import { fetchAPI, request } from '@/utils/api.js'
import { API_BASE_URL } from '@/utils/variables.js'

const quiet = { notifyOnError: false }
const userUrl = (username) => `${API_BASE_URL}/api/users/${encodeURIComponent(username)}`

// Keep the friends-only fallback until the full lists support server-enforced follow visibility.
export const FOLLOWS_MOCKED = true

// Who can see the people a user follows and is followed by. Friends are people who follow each other
export const FOLLOWS_VISIBILITY = [
  { id: 'EVERYONE', label: 'Everyone' },
  { id: 'FRIENDS', label: 'Friends', hint: 'Only people you follow who follow you back.' },
  { id: 'NO_ONE', label: 'No one', hint: 'Only you.' },
]

// 204 either way: following twice or unfollowing someone you don't follow does nothing
export function setFollowing(username, follow) {
  return request(follow ? 'PUT' : 'DELETE', `${userUrl(username)}/follow`, undefined, quiet)
}

export function isFriend(user) {
  return Boolean(user?.isFollowing && user?.followsMe)
}

const FRIENDS_PAGE = 50

// GET /api/users/me/friends (offset paging, `totalResults`) as a follow-list page. Friends follow the caller and are
// followed back, so they belong on both of the caller's lists
async function fetchFriendsPage(cursor) {
  const offset = Number(cursor) || 0
  const page = await fetchAPI(`${API_BASE_URL}/api/users/me/friends?${new URLSearchParams({ limit: FRIENDS_PAGE, offset })}`, quiet)
  const friends = page?.friends ?? []
  const users = friends.map(({ userId, username, pfpUrl }) => ({ userId, username, pfpUrl: pfpUrl ?? null, isFollowing: true, followsMe: true }))
  const next = offset + friends.length
  return { users, nextCursor: friends.length && next < (page?.totalResults ?? 0) ? String(next) : null }
}

// { users: [FollowUser], nextCursor, partial?, unavailable? }, newest follow first, 50 per page. FollowUser:
// { userId, username, pfpUrl, isFollowing (the caller follows them), followsMe (they follow the caller) }. 403
// FOLLOWS_HIDDEN when the user's followsVisibility doesn't include the caller; 404 USER_NOT_FOUND.
// While FOLLOWS_MOCKED only real people are shown: the caller's own lists hold their friends (`partial`), and anyone
// else's lists can't be loaded yet (`unavailable`)
export function fetchFollowList(username, which, cursor = null, { own = false } = {}) {
  if (FOLLOWS_MOCKED) {
    if (!own) return Promise.resolve({ users: [], nextCursor: null, unavailable: true })
    return fetchFriendsPage(cursor).then((page) => ({ ...page, partial: true }))
  }
  const query = cursor ? `?${new URLSearchParams({ cursor })}` : ''
  return fetchAPI(`${userUrl(username)}/${which === 'following' ? 'following' : 'followers'}${query}`, quiet)
}

// A settings response without a supported value can't offer follow-list privacy controls.
export function readFollowsVisibility(settings) {
  return FOLLOWS_VISIBILITY.find((option) => option.id === settings?.followsVisibility)?.id ?? null
}

export async function saveFollowsVisibility(followsVisibility) {
  const saved = await request('PUT', `${API_BASE_URL}/api/users/me/follows-visibility`, { followsVisibility }, quiet)
  const confirmed = readFollowsVisibility(saved)
  if (!confirmed) throw new Error('The server did not confirm follower visibility')
  return confirmed
}
