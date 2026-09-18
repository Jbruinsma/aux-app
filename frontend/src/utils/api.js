export async function fetchAPI(url) {
  return fetch(url)
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
  }

  if (isJson) {
    options.headers = { 'Content-Type': 'application/json' }
    options.body = JSON.stringify(data)
  } else { options.body = data }

  return fetch(url, options)
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
      console.error('Error posting:', err)
      throw err
    })
}
