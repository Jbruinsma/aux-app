import { fetchAPI, request } from '@/utils/api.js'
import { API_BASE_URL } from '@/utils/variables.js'

const quiet = { notifyOnError: false }

// NotificationPage: { notifications: [NotificationResponse], unreadCount, nextCursor }, newest first, 20 per page. The
// first page also comes live as the `notifications` event; this is for older pages
export function fetchNotifications(cursor) {
  return fetchAPI(`${API_BASE_URL}/api/notifications?${new URLSearchParams({ cursor })}`, quiet)
}

export function markAllNotificationsRead() {
  return request('PUT', `${API_BASE_URL}/api/notifications/read-all`, undefined, quiet)
}

// The link to a notification's playlist uses its owner's username, which isn't always the actor's
export function notificationPlaylistRoute(notification) {
  return { name: 'Playlist', params: { username: notification.playlistOwnerUsername, id: notification.playlist.playlistId } }
}

const MINUTE = 60_000
const HOUR = 60 * MINUTE
const DAY = 24 * HOUR

// "just now", "5 min ago", "3 h ago", "2 d ago", then the date
export function timeAgo(isoDate, now = Date.now()) {
  const elapsed = now - new Date(isoDate).getTime()
  if (!(elapsed >= 0)) return ''
  if (elapsed < MINUTE) return 'just now'
  if (elapsed < HOUR) return `${Math.floor(elapsed / MINUTE)} min ago`
  if (elapsed < DAY) return `${Math.floor(elapsed / HOUR)} h ago`
  if (elapsed < 7 * DAY) return `${Math.floor(elapsed / DAY)} d ago`
  return new Date(isoDate).toLocaleDateString()
}
