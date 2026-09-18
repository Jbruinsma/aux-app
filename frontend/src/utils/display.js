import { API_BASE_URL } from '@/utils/variables.js'

export function resolveCoverURL(cover) {
  return cover.startsWith('/uploads')
    ? `${API_BASE_URL}${cover}`
    : cover
}

export function displayArtist(artist) {
  return artist && artist.trim() !== "" && artist !== "null" ? artist : "";
}
