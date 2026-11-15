import { expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import MarqueeText from './MarqueeText.vue'

function render(play, textWidth, boxWidth) {
  const wrapper = mount(MarqueeText, { props: { text: 'Summer Friends (feat. Jeremih & Francis and the Lights)', play } })
  Object.defineProperty(wrapper.get('.marquee-text').element, 'scrollWidth', { value: textWidth, configurable: true })
  Object.defineProperty(wrapper.element, 'clientWidth', { value: boxWidth, configurable: true })
  wrapper.vm.measure()
  return wrapper
}

it('slides a title that is too long only while the song plays, and ends it in an ellipsis otherwise', async () => {
  const playing = render(true, 400, 250)
  await playing.vm.$nextTick()
  expect(playing.classes()).toContain('moving')
  expect(playing.attributes('style')).toContain('--shift: 166px')

  const paused = render(false, 400, 250)
  await paused.vm.$nextTick()
  expect(paused.classes()).toEqual(['marquee', 'clipped'])
})

it('stays still when the title fits', async () => {
  const fits = render(true, 200, 250)
  await fits.vm.$nextTick()
  expect(fits.classes()).toEqual(['marquee'])
})
