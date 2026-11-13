import { computed } from 'vue'
import { useEventsStore } from '@/stores/events.js'
import { DASHBOARD_PREVIEW } from '@/utils/dashboardPreview.js'

export const STREAM_RETRY = "Check that the server is running; we'll keep trying."

// A section fed by the live event stream (stores/events.js): `field` is the store field, `key` the list inside its
// body (null when the body is the list). It's loading until its event arrives, and shows an error only while the stream
// can't connect and nothing has arrived yet; data already shown stays if it drops later. `placeholder` fills it only
// while the account's own list is empty and DASHBOARD_PREVIEW is on
export function useStreamRow(field, key, what, placeholder = []) {
  const eventsStore = useEventsStore()
  return computed(() => {
    const body = eventsStore[field]
    if (body === null) {
      const failed = eventsStore.status === 'error'
      return { items: [], loading: !failed, error: failed ? `We couldn't load ${what}.` : '', preview: false }
    }
    const items = (key ? body[key] : body) ?? []
    if (!items.length && DASHBOARD_PREVIEW && placeholder.length) {
      return { items: placeholder, loading: false, error: '', preview: true }
    }
    return { items, loading: false, error: '', preview: false }
  })
}
