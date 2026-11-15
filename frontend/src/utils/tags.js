import { fetchAPI } from '@/utils/api.js'
import { API_BASE_URL } from '@/utils/variables.js'

export const normalizeTag = (value) => value.toLowerCase().trim().replace(/\s+/g, ' ')
export const tagProblem = (tag) => /^(?=.*[a-z0-9])[a-z0-9 &-]{2,24}$/.test(tag)
  ? '' : 'Use 2–24 letters, digits, spaces, & or -, with at least one letter or digit.'
export const tagsChanged = (tags, original = []) => tags.length !== original.length || tags.some((tag) => !original.includes(tag))
export const isTagsError = (err) => err.status === 400 && err.code === 'INVALID_FIELD' && err.parameter === 'tags'
// The server's tag messages (TagService) are worded for developers; known ones are rephrased for people
export function tagsErrorMessage(err) {
  const message = err.message ?? ''
  const notAllowed = message.match(/^Tag not allowed: (.+)$/)
  if (notAllowed) return `"${notAllowed[1]}" isn't allowed. Remove it or try a different tag.`
  if (/^At most \d+ tags$/.test(message)) return 'Use up to 5 tags.'
  if (/^Tags are 2-24/.test(message)) return 'Use 2–24 letters, digits, spaces, & or -, with at least one letter or digit.'
  return message || "These tags couldn't be saved. Check their wording and try again."
}

export function fetchTags(query = '') {
  const suffix = query ? `?${new URLSearchParams({ q: query })}` : ''
  return fetchAPI(`${API_BASE_URL}/api/tags${suffix}`, { notifyOnError: false })
}

// Omit on create when empty; updates send a blank value to clear all tags.
export function appendTags(form, tags, clear = false) {
  for (const tag of tags) form.append('tags', tag)
  if (clear && !tags.length) form.append('tags', '')
}
