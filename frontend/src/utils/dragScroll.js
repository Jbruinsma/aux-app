import { onBeforeUnmount, ref, watch } from 'vue'

const DRAG_THRESHOLD = 5
// Release glide: speed (px per ms) keeps this share of itself every 16ms, and stops below MIN_SPEED
const FRICTION = 0.94
const MIN_SPEED = 0.02

// Dragging with the mouse scrolls a sideways list. Touch and trackpads keep the browser's own scrolling. A drag that ends
// over a card doesn't count as a click on it, and letting go mid-drag glides on and eases to a stop (unless the user
// prefers reduced motion). `onDrag` runs once each time a drag starts
export function useDragScroll(elementRef, { onDrag } = {}) {
  const dragging = ref(false)
  let start = null
  let moved = false
  let velocity = 0
  let last = null
  let glideFrame = null

  function stopGlide() {
    cancelAnimationFrame(glideFrame)
    glideFrame = null
  }

  function glide(speed) {
    const element = elementRef.value
    let previous = performance.now()
    function step(now) {
      const elapsed = now - previous
      previous = now
      const before = element.scrollLeft
      element.scrollLeft = before - speed * elapsed
      speed *= FRICTION ** (elapsed / 16)
      // Stop when it's slow enough or has hit an end of the list
      if (Math.abs(speed) < MIN_SPEED || element.scrollLeft === before) {
        glideFrame = null
        return
      }
      glideFrame = requestAnimationFrame(step)
    }
    glideFrame = requestAnimationFrame(step)
  }

  function onPointerDown(event) {
    stopGlide()
    if (event.pointerType !== 'mouse' || event.button !== 0) return
    start = { x: event.clientX, scrollLeft: elementRef.value.scrollLeft, pointerId: event.pointerId }
    last = { x: event.clientX, time: event.timeStamp }
    velocity = 0
    moved = false
  }

  function onPointerMove(event) {
    if (!start || event.pointerId !== start.pointerId) return
    const distance = event.clientX - start.x
    if (!moved) {
      if (Math.abs(distance) < DRAG_THRESHOLD) return
      moved = true
      dragging.value = true
      onDrag?.()
      try {
        elementRef.value.setPointerCapture(event.pointerId)
      } catch {
        // Capture keeps the drag going if the pointer leaves the list; without it the drag still works inside it
      }
    }
    elementRef.value.scrollLeft = start.scrollLeft - distance
    // Smoothed pointer speed, so the glide follows the flick rather than the last jittery sample
    const elapsed = event.timeStamp - last.time
    if (elapsed > 0) velocity = 0.8 * ((event.clientX - last.x) / elapsed) + 0.2 * velocity
    last = { x: event.clientX, time: event.timeStamp }
  }

  function onPointerUp(event) {
    if (!start) return
    start = null
    dragging.value = false
    // Holding still before letting go means no glide
    const held = event.timeStamp - last.time > 80
    const reduced = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
    if (moved && !held && !reduced && Math.abs(velocity) > MIN_SPEED) glide(velocity)
  }

  function onClick(event) {
    if (!moved) return
    moved = false
    event.preventDefault()
    event.stopPropagation()
  }

  // Stops the browser dragging images and links out of the list
  function onDragStart(event) {
    event.preventDefault()
  }

  const listeners = {
    pointerdown: onPointerDown,
    pointermove: onPointerMove,
    pointerup: onPointerUp,
    pointercancel: onPointerUp,
    wheel: stopGlide,
    touchstart: stopGlide,
    dragstart: onDragStart,
  }

  function attach(element) {
    for (const [name, handler] of Object.entries(listeners)) element.addEventListener(name, handler)
    element.addEventListener('click', onClick, true)
  }

  function detach(element) {
    for (const [name, handler] of Object.entries(listeners)) element.removeEventListener(name, handler)
    element.removeEventListener('click', onClick, true)
  }

  // The list can appear later (it renders once its data loads), so follow the ref
  watch(elementRef, (element, previous) => {
    if (previous) detach(previous)
    if (element) attach(element)
  }, { immediate: true })

  onBeforeUnmount(() => {
    stopGlide()
    if (elementRef.value) detach(elementRef.value)
  })

  return { dragging }
}
