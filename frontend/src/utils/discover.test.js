import { expect, it } from 'vitest'
import { buildLayout, slotAt } from './discover.js'

it('puts the seed in the middle and the best matches in the first ring', () => {
  const layout = buildLayout(20)
  expect(layout.side).toBe(5)
  expect(slotAt(layout, 20, 0, 0)).toBe(0)
  const firstRing = [[-1, -1], [0, -1], [1, -1], [-1, 0], [1, 0], [-1, 1], [0, 1], [1, 1]]
  expect(firstRing.map(([x, y]) => slotAt(layout, 20, x, y)).sort((a, b) => a - b)).toEqual([1, 2, 3, 4, 5, 6, 7, 8])
})

it('repeats the block in every direction and fills spare cells with repeats', () => {
  const layout = buildLayout(20) // 25 cells: seed, 20 results, 4 repeats
  expect(slotAt(layout, 20, 5, 0)).toBe(0)
  expect(slotAt(layout, 20, -5, 10)).toBe(0)
  expect(slotAt(layout, 20, 6, -4)).toBe(slotAt(layout, 20, 1, 1))
  const ranks = []
  for (let y = -2; y <= 2; y++) for (let x = -2; x <= 2; x++) ranks.push(slotAt(layout, 20, x, y))
  expect(new Set(ranks).size).toBe(21)
})

it('shows only the seed when there are no results', () => {
  expect(slotAt(buildLayout(0), 0, 3, -2)).toBe(0)
})
