import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { initializeTheme, setThemePreference, themePreference } from './theme.js'
import ThemePreference from '@/components/ThemePreference.vue'

let media, stop, wrapper
beforeEach(() => {
  localStorage.clear()
  media = new EventTarget()
  media.matches = false
  vi.stubGlobal('matchMedia', vi.fn(() => media))
})
afterEach(() => {
  stop?.()
  wrapper?.unmount()
  wrapper = null
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})
function deviceTheme(dark) {
  media.matches = dark
  media.dispatchEvent(new Event('change'))
}

it('defaults to the device and follows changes live', () => {
  media.matches = true
  stop = initializeTheme()
  expect(themePreference.value).toBe('system')
  expect(document.documentElement.dataset.theme).toBe('dark')
  deviceTheme(false)
  expect(document.documentElement.dataset.theme).toBe('light')
  expect(document.documentElement.style.colorScheme).toBe('light')
})

it.each(['light', 'dark'])('loads saved %s and ignores device changes until System is selected', choice => {
  localStorage.setItem('aux-theme', choice)
  stop = initializeTheme()
  deviceTheme(true)
  deviceTheme(false)
  expect(document.documentElement.dataset.theme).toBe(choice)
  setThemePreference('system')
  expect(localStorage.getItem('aux-theme')).toBe(null)
  deviceTheme(true)
  expect(document.documentElement.dataset.theme).toBe('dark')
})

it('saves radio selections and restores them on startup', async () => {
  stop = initializeTheme()
  wrapper = mount(ThemePreference)
  await wrapper.get('input[value="dark"]').setValue()
  expect(localStorage.getItem('aux-theme')).toBe('dark')
  expect(document.documentElement.dataset.theme).toBe('dark')
  stop = initializeTheme()
  expect(themePreference.value).toBe('dark')
  await wrapper.get('input[value="system"]').setValue()
  expect(localStorage.getItem('aux-theme')).toBe(null)
  expect(document.documentElement.dataset.theme).toBe('light')
})

it('falls back to System for an invalid stored choice', () => {
  localStorage.setItem('aux-theme', 'invalid')
  media.matches = true
  stop = initializeTheme()
  expect(themePreference.value).toBe('system')
  expect(document.documentElement.dataset.theme).toBe('dark')
})

it('still works when browser storage is blocked', () => {
  vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => { throw new Error('blocked') })
  vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => { throw new Error('blocked') })
  vi.spyOn(Storage.prototype, 'removeItem').mockImplementation(() => { throw new Error('blocked') })
  stop = initializeTheme()
  expect(() => setThemePreference('dark')).not.toThrow()
  expect(document.documentElement.dataset.theme).toBe('dark')
  expect(() => setThemePreference('system')).not.toThrow()
  expect(document.documentElement.dataset.theme).toBe('light')
})

it('syncs another tab’s selection and removes listeners on disposal', () => {
  stop = initializeTheme()
  localStorage.setItem('aux-theme', 'dark')
  window.dispatchEvent(new StorageEvent('storage', { key: 'aux-theme' }))
  expect(themePreference.value).toBe('dark')
  localStorage.clear()
  window.dispatchEvent(new StorageEvent('storage', { key: null }))
  expect(themePreference.value).toBe('system')
  expect(document.documentElement.dataset.theme).toBe('light')
  stop()
  deviceTheme(true)
  expect(document.documentElement.dataset.theme).toBe('light')
})
