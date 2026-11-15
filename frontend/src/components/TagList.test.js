import { expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import TagList from './TagList.vue'

const RouterLink = { props: ['to'], template: '<a :data-to="JSON.stringify(to)"><slot /></a>' }

it('links each tag to its Explore page, labelled for screen readers', () => {
  const wrapper = mount(TagList, { props: { tags: ['chill', 'r&b'], label: 'Song tags' }, global: { stubs: { RouterLink } } })
  expect(wrapper.get('ul').attributes('aria-label')).toBe('Song tags')
  const links = wrapper.findAll('a.tag')
  expect(links.map(l => l.text())).toEqual(['chill', 'r&b'])
  expect(JSON.parse(links[1].attributes('data-to'))).toEqual({ name: 'ExploreTag', params: { tag: 'r&b' } })
})

it('renders nothing without tags', () => {
  const wrapper = mount(TagList, { props: { tags: [] }, global: { stubs: { RouterLink } } })
  expect(wrapper.find('ul').exists()).toBe(false)
})
