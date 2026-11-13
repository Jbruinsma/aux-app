import { fetchAPI, request } from '@/utils/api.js'
import { API_BASE_URL } from '@/utils/variables.js'

const quiet = { notifyOnError: false }
const userUrl = (username) => `${API_BASE_URL}/api/users/${encodeURIComponent(username)}`

// The follower and following lists, and who may see them, aren't in the API yet (BACKEND_REQUESTS.md §3 and §4). While
// this is true the lists show sample people and the visibility setting is kept in this browser; set it to false once
// GET /api/users/{username}/followers, .../following and the followsVisibility setting exist
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

const SAMPLE_PEOPLE = [
  { userId: 'sample-1', username: 'justin', pfpUrl: null, isFollowing: true, followsMe: true },
  { userId: 'sample-2', username: 'arjay', pfpUrl: null, isFollowing: false, followsMe: true },
  { userId: 'sample-3', username: 'sam', pfpUrl: null, isFollowing: true, followsMe: false },
  { userId: 'sample-4', username: 'nia', pfpUrl: null, isFollowing: true, followsMe: true },
]

// { users: [FollowUser], nextCursor }, newest follow first, 50 per page. FollowUser: { userId, username, pfpUrl,
// isFollowing (the caller follows them), followsMe (they follow the caller) }. 403 FOLLOWS_HIDDEN when the user's
// followsVisibility doesn't include the caller; 404 USER_NOT_FOUND
export function fetchFollowList(username, which, cursor = null) {
  if (FOLLOWS_MOCKED) return Promise.resolve({ users: SAMPLE_PEOPLE, nextCursor: null })
  const query = cursor ? `?${new URLSearchParams({ cursor })}` : ''
  return fetchAPI(`${userUrl(username)}/${which === 'following' ? 'following' : 'followers'}${query}`, quiet)
}

const visibilityKey = (userId) => `aux-follows-visibility-${userId}`

// The caller's followsVisibility: from GET /api/users/settings once the backend sends it, from this browser until then
export function readFollowsVisibility(settings, userId) {
  if (!FOLLOWS_MOCKED) return settings?.followsVisibility ?? 'EVERYONE'
  try {
    return localStorage.getItem(visibilityKey(userId)) ?? 'EVERYONE'
  } catch {
    return 'EVERYONE'
  }
}

// PUT /api/users/me/follows-visibility { followsVisibility }; returns the saved value
export async function saveFollowsVisibility(followsVisibility, userId) {
  if (!FOLLOWS_MOCKED) {
    const saved = await request('PUT', `${API_BASE_URL}/api/users/me/follows-visibility`, { followsVisibility }, quiet)
    return saved?.followsVisibility ?? followsVisibility
  }
  try {
    localStorage.setItem(visibilityKey(userId), followsVisibility)
  } catch {
    // Blocked storage: the choice holds until the page reloads
  }
  return followsVisibility
}
