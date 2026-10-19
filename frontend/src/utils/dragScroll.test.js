import { expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { defineComponent, h, ref } from 'vue'
import { useDragScroll } from './dragScroll.js'

async function setup(onDrag) {
  const clicked = vi.fn()
  let state
  const wrapper = mount(defineComponent({
    setup() {
      const list = ref(null)
      state = useDragScroll(list, { onDrag })
      return () => h('ul', { ref: list }, [h('li', { onClick: clicked }, 'card')])
    },
  }), { attachTo: document.body })
  await flushPromises()
  const list = wrapper.element
  list.scrollLeft = 200
  return { wrapper, list, item: list.firstElementChild, clicked, state }
}

const pointer = (type, clientX, pointerType = 'mouse') =>
  new PointerEvent(type, { bubbles: true, button: 0, clientX, pointerId: 1, pointerType })

it('scrolls the list by mouse drag and ignores the click that ends the drag', async () => {
  const onDrag = vi.fn()
  const { wrapper, list, item, clicked, state } = await setup(onDrag)
  item.dispatchEvent(pointer('pointerdown', 300))
  item.dispatchEvent(pointer('pointermove', 240))
  expect(list.scrollLeft).toBe(260)
  expect(state.dragging.value).toBe(true)
  item.dispatchEvent(pointer('pointerup', 240))
  item.dispatchEvent(new MouseEvent('click', { bubbles: true }))
  expect(clicked).not.toHaveBeenCalled()
  expect(onDrag).toHaveBeenCalledTimes(1)
  expect(state.dragging.value).toBe(false)
  item.dispatchEvent(new MouseEvent('click', { bubbles: true }))
  expect(clicked).toHaveBeenCalledTimes(1)
  wrapper.unmount()
})

it('leaves small wiggles as clicks and touch to the browser', async () => {
  const onDrag = vi.fn()
  const { wrapper, list, item, clicked } = await setup(onDrag)
  item.dispatchEvent(pointer('pointerdown', 300))
  item.dispatchEvent(pointer('pointermove', 297))
  item.dispatchEvent(pointer('pointerup', 297))
  item.dispatchEvent(new MouseEvent('click', { bubbles: true }))
  expect(clicked).toHaveBeenCalledTimes(1)
  item.dispatchEvent(pointer('pointerdown', 300, 'touch'))
  item.dispatchEvent(pointer('pointermove', 200, 'touch'))
  expect(list.scrollLeft).toBe(200)
  expect(onDrag).not.toHaveBeenCalled()
  wrapper.unmount()
})

const timed = (event, timeStamp) => Object.defineProperty(event, 'timeStamp', { value: timeStamp })

it('glides on after a flick and eases to a stop, but not after holding still', async () => {
  let frames = []
  let now = 0
  vi.stubGlobal('requestAnimationFrame', (callback) => frames.push(callback))
  vi.stubGlobal('cancelAnimationFrame', () => { frames = [] })
  vi.spyOn(performance, 'now').mockImplementation(() => now)
  const { wrapper, list, item } = await setup()
  list.scrollLeft = 500

  item.dispatchEvent(timed(pointer('pointerdown', 300), 0))
  item.dispatchEvent(timed(pointer('pointermove', 280), 10))
  item.dispatchEvent(timed(pointer('pointermove', 260), 20))
  item.dispatchEvent(timed(pointer('pointerup', 260), 25))
  const released = list.scrollLeft
  const steps = []
  while (frames.length && steps.length < 500) {
    now += 16
    const step = frames.shift()
    const before = list.scrollLeft
    step(now)
    steps.push(list.scrollLeft - before)
  }
  expect(list.scrollLeft).toBeGreaterThan(released)
  expect(steps[0]).toBeGreaterThan(steps[5])
  expect(frames).toHaveLength(0)

  frames = []
  item.dispatchEvent(timed(pointer('pointerdown', 300), 1000))
  item.dispatchEvent(timed(pointer('pointermove', 260), 1010))
  item.dispatchEvent(timed(pointer('pointerup', 260), 1300))
  expect(frames).toHaveLength(0)
  wrapper.unmount()
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})
