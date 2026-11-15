import { expect, it } from 'vitest'
import { pickProminentColor } from '@/utils/coverColor.js'

// RGBA pixels: `count` copies of [r, g, b], fully opaque
function pixels(...runs) {
  const out = []
  for (const [count, [r, g, b, a = 255]] of runs) for (let i = 0; i < count; i++) out.push(r, g, b, a)
  return new Uint8ClampedArray(out)
}

function lightness(hex) {
  const [r, g, b] = [1, 3, 5].map(i => parseInt(hex.slice(i, i + 2), 16) / 255)
  return (Math.max(r, g, b) + Math.min(r, g, b)) / 2
}

it('picks the main color of a single-color cover', () => {
  expect(pickProminentColor(pixels([100, [60, 90, 160]]))).toBe('#3c5aa0')
})

it('lets a saturated accent beat a larger gray area', () => {
  const hex = pickProminentColor(pixels([700, [128, 128, 128]], [300, [200, 40, 30]]))
  const [r, g, b] = [1, 3, 5].map(i => parseInt(hex.slice(i, i + 2), 16))
  expect(r).toBeGreaterThan(g + 80)
  expect(r).toBeGreaterThan(b + 80)
})

it('prefers the color with more pixels when both are saturated', () => {
  const hex = pickProminentColor(pixels([600, [40, 60, 160]], [200, [230, 190, 60]]))
  const [r, , b] = [1, 3, 5].map(i => parseInt(hex.slice(i, i + 2), 16))
  expect(b).toBeGreaterThan(r)
})

it('falls back to the average for a black and white cover, kept to a middle lightness', () => {
  const hex = pickProminentColor(pixels([500, [250, 250, 250]], [500, [245, 245, 245]]))
  expect(hex).toMatch(/^#([0-9a-f]{2})\1\1$/)
  expect(lightness(hex)).toBeCloseTo(0.65, 1)
})

it('brightens a very dark color and darkens a very light one', () => {
  expect(lightness(pickProminentColor(pixels([100, [30, 10, 60]])))).toBeGreaterThanOrEqual(0.29)
  expect(lightness(pickProminentColor(pixels([100, [255, 200, 220]])))).toBeLessThanOrEqual(0.66)
})

it('ignores transparent pixels and returns null when nothing is opaque', () => {
  expect(pickProminentColor(pixels([100, [200, 40, 30, 0]]))).toBeNull()
  expect(pickProminentColor(pixels([100, [200, 40, 30, 0]], [10, [60, 90, 160]]))).toBe('#3c5aa0')
})
