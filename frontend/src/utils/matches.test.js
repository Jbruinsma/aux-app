import { expect, it } from 'vitest'
import { compatibilityLevel, nameSeparator } from './matches.js'

it.each([
  [0, 'Low', 1], [19, 'Low', 1], [20, 'Medium', 2], [40, 'High', 3], [64, 'Very high', 4], [80, 'Super', 5], [100, 'Super', 5],
])('%i%% is %s with %i of 5 segments filled', (score, label, filled) => {
  expect(compatibilityLevel(score)).toEqual({ label, filled })
})

it('separates shared artist names as "A, B and C"', () => {
  const names = ['A', 'B', 'C']
  expect(names.map((name, i) => name + nameSeparator(i, names.length)).join('')).toBe('A, B and C')
  expect(['A', 'B'].map((name, i) => name + nameSeparator(i, 2)).join('')).toBe('A and B')
})
