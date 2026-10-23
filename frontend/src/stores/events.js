import { defineStore } from 'pinia'
import { ref } from 'vue'
import { ApiError, authHeaders, endSession } from '@/utils/api.js'
import { API_BASE_URL } from '@/utils/variables.js'

// GET /api/users/me/events: one server-sent-event stream that keeps the header and the Dashboard current. On connect the
// server sends every event once; after that it resends an event, whole, whenever its data changes. Read with fetch so
// the token goes in the Authorization header: EventSource can't send headers, and the token must never be in the URL
const FIELDS = {
  invites: 'invites',
  recents: 'recents',
  'top-artists': 'topArtists',
  library: 'library',
  uploads: 'uploads',
  notifications: 'notifications',
}
const FIRST_RETRY_MS = 1000
const MAX_RETRY_MS = 30_000

export const useEventsStore = defineStore('events', () => {
  // Each is null until its event first arrives, then the latest body as the endpoint in the docs returns it
  const invites = ref(null)
  const recents = ref(null)
  const topArtists = ref(null)
  const library = ref(null)
  const uploads = ref(null)
  const notifications = ref(null)
  const fields = { invites, recents, topArtists, library, uploads, notifications }

  // idle (logged out) | connecting | open | error (couldn't connect; still retrying)
  const status = ref('idle')

  let controller = null
  let retryTimer = null
  let failures = 0

  function apply(name, data) {
    const field = FIELDS[name]
    if (!field) return false
    try {
      fields[field].value = JSON.parse(data)
      return true
    } catch {
      console.warn('Ignored a malformed event:', name)
      return false
    }
  }

  // event:/data: lines, a blank line ends an event. One space after the colon is allowed; ':' lines are keep-alives.
  // Returns how many events arrived
  async function read(body, signal) {
    const reader = body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    let name = null
    let data = []
    let received = 0
    while (!signal.aborted) {
      const { value, done } = await reader.read()
      if (done) return received
      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop()
      for (const raw of lines) {
        const line = raw.endsWith('\r') ? raw.slice(0, -1) : raw
        if (line === '') {
          if (name && data.length && apply(name, data.join('\n'))) received++
          name = null
          data = []
        } else if (line.startsWith(':')) {
          continue
        } else {
          const colon = line.indexOf(':')
          const key = colon === -1 ? line : line.slice(0, colon)
          let field = colon === -1 ? '' : line.slice(colon + 1)
          if (field.startsWith(' ')) field = field.slice(1)
          if (key === 'event') name = field
          else if (key === 'data') data.push(field)
        }
      }
    }
    return received
  }

  async function run(signal) {
    status.value = status.value === 'open' ? 'open' : 'connecting'
    try {
      const response = await fetch(`${API_BASE_URL}/api/users/me/events`, {
        headers: { ...authHeaders(), Accept: 'text/event-stream' },
        signal,
      })
      if (!response.ok) {
        const body = await response.json().catch(() => null)
        throw new ApiError(response.status, body?.errorDetails)
      }
      status.value = 'open'
      const received = await read(response.body, signal)
      if (signal.aborted) return
      // The server ends the stream when the token expires: reconnect straight away with the current one. A stream that
      // closed without sending anything counts as a failure, so it backs off instead of looping
      if (received) {
        failures = 0
        scheduleReconnect(0)
        return
      }
      throw new Error('The event stream closed without sending anything')
    } catch (err) {
      if (signal.aborted) return
      if (err.status === 401) {
        disconnect()
        await endSession()
        return
      }
      failures++
      status.value = 'error'
      scheduleReconnect(Math.min(MAX_RETRY_MS, FIRST_RETRY_MS * 2 ** (failures - 1)))
    }
  }

  function scheduleReconnect(delay) {
    clearTimeout(retryTimer)
    retryTimer = setTimeout(() => {
      if (!controller || controller.signal.aborted) return
      run(controller.signal)
    }, delay)
  }

  // Opens the stream once; calling it again while connected does nothing
  function connect() {
    if (controller) return
    controller = new AbortController()
    failures = 0
    run(controller.signal)
  }

  // Closes the stream and forgets everything it sent (on logout)
  function disconnect() {
    controller?.abort()
    controller = null
    clearTimeout(retryTimer)
    failures = 0
    status.value = 'idle'
    for (const field of Object.values(fields)) field.value = null
  }

  // A new token (logging in as someone else, for example): start over with it
  function restart() {
    disconnect()
    connect()
  }

  return { invites, recents, topArtists, library, uploads, notifications, status, connect, disconnect, restart }
})
