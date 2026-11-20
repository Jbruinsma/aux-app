import { fetchAPI, request } from '@/utils/api.js'
import { API_BASE_URL } from '@/utils/variables.js'

const quiet = { notifyOnError: false }
const userUrl = (username) => `${API_BASE_URL}/api/users/${encodeURIComponent(username)}`

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

// { users: [FollowUser], nextCursor }, newest follow first, 50 per page. FollowUser: { userId, username, pfpUrl,
// isFollowing (the caller follows them), followsMe (they follow the caller) }. 403 FOLLOWS_HIDDEN when the user's
// followsVisibility doesn't include the caller; 404 USER_NOT_FOUND
export function fetchFollowList(username, which, cursor = null) {
  const query = cursor ? `?${new URLSearchParams({ cursor })}` : ''
  return fetchAPI(`${userUrl(username)}/${which === 'following' ? 'following' : 'followers'}${query}`, quiet)
}

// The caller's followsVisibility, from GET /api/users/settings
export function readFollowsVisibility(settings) {
  return settings?.followsVisibility ?? 'EVERYONE'
}

// PUT /api/users/me/follows-visibility { followsVisibility }; returns the saved value
export async function saveFollowsVisibility(followsVisibility) {
  const saved = await request('PUT', `${API_BASE_URL}/api/users/me/follows-visibility`, { followsVisibility }, quiet)
  return saved?.followsVisibility ?? followsVisibility
}
