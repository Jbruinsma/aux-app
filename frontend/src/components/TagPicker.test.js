import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { fetchAPI } from '@/utils/api.js'
import TagPicker from './TagPicker.vue'

vi.mock('@/utils/api.js', () => ({ fetchAPI: vi.fn() }))
let wrapper
beforeEach(() => { vi.useFakeTimers(); fetchAPI.mockReset(); fetchAPI.mockResolvedValue(['chill', 'r&b']) })
afterEach(() => { wrapper?.unmount(); vi.useRealTimers() })
async function render(tags = []) {
  wrapper = mount(TagPicker, { props: { modelValue: tags, 'onUpdate:modelValue': (value) => wrapper.setProps({ modelValue: value }) } })
  await flushPromises()
}

it('loads top suggestions without q, then searches with q and adds a suggestion', async () => {
  await render()
  expect(fetchAPI).toHaveBeenCalledWith(expect.stringMatching(/\/api\/tags$/), { notifyOnError: false })
  expect(wrapper.text()).toContain('Add tags to help people find this')
  await wrapper.get('input').setValue('r&')
  await vi.advanceTimersByTimeAsync(250)
  expect(fetchAPI).toHaveBeenLastCalledWith(expect.stringContaining('?q=r%26'), expect.any(Object))
  await wrapper.findAll('.suggestions button')[1].trigger('click')
  expect(wrapper.props('modelValue')).toEqual(['r&b'])
})

it('normalizes custom tags, prevents duplicates, rejects invalid ones, and stops at five', async () => {
  await render()
  const add = async (value) => { await wrapper.get('input').setValue(value); await wrapper.get('input').trigger('keydown', { key: 'Enter' }) }
  await add('  Late   NIGHT  ')
  await add('late night')
  expect(wrapper.props('modelValue')).toEqual(['late night'])
  for (const invalid of ['x', 'r,b', '&&', 'a'.repeat(25)]) {
    await add(invalid)
    expect(wrapper.get('[role="alert"]').text()).toContain('2–24')
  }
  for (const value of ['chill', 'r&b', 'lo-fi', 'soul']) await add(value)
  expect(wrapper.props('modelValue')).toHaveLength(5)
  expect(wrapper.get('input').attributes('disabled')).toBeDefined()
  await wrapper.get('button[aria-label="Remove chill"]').trigger('click')
  expect(wrapper.props('modelValue')).toHaveLength(4)
  expect(wrapper.get('input').attributes('disabled')).toBeUndefined()
})

it('ignores stale suggestions and displays server rejection under the input', async () => {
  await render()
  let finish
  fetchAPI.mockImplementationOnce(() => new Promise((resolve) => { finish = resolve }))
  await wrapper.get('input').setValue('old')
  await vi.advanceTimersByTimeAsync(250)
  fetchAPI.mockResolvedValueOnce(['new tag'])
  await wrapper.get('input').setValue('new')
  await vi.advanceTimersByTimeAsync(250)
  finish(['old tag'])
  await flushPromises()
  expect(wrapper.text()).toContain('new tag')
  expect(wrapper.text()).not.toContain('old tag')
  await wrapper.setProps({ error: 'Tag contains prohibited wording' })
  expect(wrapper.get('[role="alert"]').text()).toBe('Tag contains prohibited wording')
})
