// Slides text that just moved (e.g. a typed name becoming a step heading) from where it was into its new spot.
// `from` is the old element's DOMRect, taken before the DOM changed; `fromFontSize` is its font size in px.
// The old element is assumed to be an input with 12px of left padding, like .input
export function slideFrom(el, from, fromFontSize) {
  if (matchMedia('(prefers-reduced-motion: reduce)').matches) return
  const to = el.getBoundingClientRect()
  const styles = getComputedStyle(document.documentElement)
  const scale = fromFontSize / parseFloat(getComputedStyle(el).fontSize)
  const dx = from.left + 12 - to.left
  const dy = from.top + from.height / 2 - (to.top + to.height / 2)
  el.animate(
    [{ transform: `translate(${dx}px, ${dy}px) scale(${scale})` }, { transform: 'none' }],
    { duration: parseFloat(styles.getPropertyValue('--dur-med')), easing: styles.getPropertyValue('--ease').trim() },
  )
}

// Resolves once every animation inside el (and el itself) has finished
export function animationsDone(el) {
  return Promise.all(el.getAnimations({ subtree: true }).map((a) => a.finished))
}
