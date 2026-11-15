import { expect, it } from 'vitest'
import { tagsErrorMessage } from './tags.js'

it("rewords the server's tag errors for people, and passes others through", () => {
  expect(tagsErrorMessage({ message: 'Tag not allowed: badword' })).toBe(`"badword" isn't allowed. Remove it or try a different tag.`)
  expect(tagsErrorMessage({ message: 'At most 5 tags' })).toBe('Use up to 5 tags.')
  expect(tagsErrorMessage({ message: "Tags are 2-24 letters, digits, spaces, '&' or '-'" })).toBe('Use 2–24 letters, digits, spaces, & or -, with at least one letter or digit.')
  expect(tagsErrorMessage({ message: 'Choose another tag' })).toBe('Choose another tag')
  expect(tagsErrorMessage({})).toBe("These tags couldn't be saved. Check their wording and try again.")
})
