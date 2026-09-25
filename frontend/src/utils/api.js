import { useUserStore } from '@/stores/user.js'

// Sends the logged-in user's session token so the Java backend knows who is asking
function authHeaders() {
  const token = useUserStore().token
  return token ? { Authorization: `Bearer ${token}` } : {}
}

export async function fetchAPI(url) {
  return fetch(url, { headers: authHeaders() })
    .then(response => {
      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`)
      }
      return response.json()
    })
    .then(data => {
      return data
    })
    .catch(err => {
      console.error('Error fetching:', err)
      throw err
    })
}

export async function postToAPI(url, data, isJson = true) {
  const options = {
    method: 'POST',
    headers: authHeaders(),
  }

  if (isJson) {
    options.headers['Content-Type'] = 'application/json'
    options.body = JSON.stringify(data)
  } else { options.body = data }

  return fetch(url, options)
    .then(async response => {
      if (!response.ok) {
        // Java backend errors look like {errorDetails: {message, code, parameter}}
        const body = await response.json().catch(() => null)
        throw new Error(body?.errorDetails?.message ?? `HTTP error! status: ${response.status}`)
      }
      return response.json()
    })
    .then(data => {
      return data
    })
    .catch(err => {
      console.error('Error posting:', err)
      throw err
    })
}
