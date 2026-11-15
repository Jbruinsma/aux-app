// Shared-element tweens: a cover glides from where it was (`from`, a DOMRect measured before the switch) to where it
// is now, so moving between the full cover and lyrics doesn't snap. Nothing moves for reduced motion or where
// animations aren't supported (tests)
export const TWEEN = { duration: 450, easing: 'cubic-bezier(0.2, 0, 0, 1)' }

export function calm() {
  return typeof matchMedia !== 'function' || matchMedia('(prefers-reduced-motion: reduce)').matches
}

export function glide(el, from) {
  if (calm() || !from?.width || typeof el?.animate !== 'function') return
  const to = el.getBoundingClientRect()
  if (!to.width) return
  const start = `translate(${from.left - to.left}px, ${from.top - to.top}px) scale(${from.width / to.width})`
  el.animate([{ transform: start, transformOrigin: 'top left' }, { transform: 'none', transformOrigin: 'top left' }], TWEEN)
}

// Fades elements up into place just after a glide starts
export function rise(elements) {
  if (calm()) return
  for (const el of elements) {
    if (typeof el.animate !== 'function') continue
    el.animate([{ opacity: 0, transform: 'translateY(12px)' }, { opacity: 1, transform: 'none' }], { ...TWEEN, delay: 120, fill: 'backwards' })
  }
}
