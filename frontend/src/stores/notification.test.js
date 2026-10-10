import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import { useNotificationStore } from './notification.js'
import GlobalNotifications from '@/components/GlobalNotifications.vue'

beforeEach(() => {
  setActivePinia(createPinia())
  vi.useFakeTimers()
})
afterEach(() => vi.useRealTimers())

it('replaces a notification without an old timer hiding the replacement', () => {
  const notification = useNotificationStore()
  notification.success('Saved')
  vi.advanceTimersByTime(2000)
  notification.error('Upload failed')
  vi.advanceTimersByTime(1000)
  expect(notification.visible).toBe(true)
  expect(notification.message).toBe('Upload failed')
  expect(notification.type).toBe('error')
  vi.advanceTimersByTime(2000)
  expect(notification.visible).toBe(false)
})

it('clears the timer on dismissal and keeps a new notification visible for its full duration', () => {
  const notification = useNotificationStore()
  notification.success('Saved')
  vi.advanceTimersByTime(1000)
  notification.hide()
  expect(vi.getTimerCount()).toBe(0)
  notification.neutral('Preview only')
  vi.advanceTimersByTime(2000)
  expect(notification.visible).toBe(true)
  vi.advanceTimersByTime(1000)
  expect(notification.visible).toBe(false)
})

it('announces messages, renders server text safely and supports the dismiss button', async () => {
  const notification = useNotificationStore()
  const wrapper = mount(GlobalNotifications)
  expect(wrapper.attributes('role')).toBe('status')
  notification.success('<img src=x onerror=alert(1)>')
  await nextTick()
  expect(wrapper.find('img').exists()).toBe(false)
  expect(wrapper.text()).toContain('<img src=x onerror=alert(1)>')
  await wrapper.get('button[aria-label="Dismiss notification"]').trigger('click')
  expect(notification.visible).toBe(false)
  notification.error('Upload failed')
  await nextTick()
  expect(wrapper.attributes('role')).toBe('alert')
  wrapper.unmount()
})
