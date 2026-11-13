import { expect, it } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import router from './index.js'

setActivePinia(createPinia())

it('serves the dashboard as the Music tab, and sends old /dashboard links there', () => {
  expect(router.resolve('/music')).toMatchObject({ name: 'Dashboard', meta: { nav: 'Music' } })
  const old = router.resolve('/dashboard/library')
  expect(router.resolve(old.matched[0].redirect).fullPath).toBe('/music')
  expect(router.resolve('/explore/tag/chill')).toMatchObject({ name: 'ExploreTag', meta: { nav: 'Explore' } })
})
