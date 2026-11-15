<template>
  <span ref="box" class="marquee" :class="{ moving, clipped: overflow && !moving }" :style="{ '--shift': `${overflow + 16}px`, '--marquee-time': `${seconds}s` }">
    <span ref="inner" class="marquee-text">{{ text }}</span>
  </span>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'

// One line of text that, when it's too long to fit and `play` is on, drifts slowly to its end and back, like a long
// "(feat. …)" title on Apple Music. Otherwise it ends in an ellipsis. Still for reduced motion
const props = defineProps({
  text: { type: String, default: '' },
  play: Boolean,
})

const box = ref(null)
const inner = ref(null)
const overflow = ref(0)
// A slow drift: the slide itself takes about overflow / 20 seconds (at least 4.8), with a short rest at each end so
// it starts moving soon after the song does
const seconds = computed(() => Math.round((Math.max(4.8, overflow.value / 20) / 0.88) * 10) / 10)
const moving = computed(() => props.play && overflow.value > 0)
let observer = null

function measure() {
  if (!box.value || !inner.value) return
  overflow.value = Math.max(0, Math.ceil(inner.value.scrollWidth - box.value.clientWidth))
}

onMounted(() => {
  measure()
  if (typeof ResizeObserver === 'function') {
    observer = new ResizeObserver(measure)
    observer.observe(box.value)
  }
})
onBeforeUnmount(() => observer?.disconnect())
watch(() => props.text, () => nextTick(measure))

defineExpose({ measure })
</script>

<style scoped>
.marquee { display: block; min-width: 0; overflow: hidden; white-space: nowrap; }
.marquee-text { display: inline-block; }
.clipped .marquee-text { display: block; overflow: hidden; text-overflow: ellipsis; }
.moving { mask-image: linear-gradient(to right, #000 calc(100% - 16px), transparent 100%); }
.moving .marquee-text { animation: marquee var(--marquee-time) ease-in-out infinite alternate; }
@keyframes marquee {
  0%, 6% { transform: translateX(0); }
  94%, 100% { transform: translateX(calc(-1 * var(--shift))); }
}

@media (prefers-reduced-motion: reduce) {
  .moving .marquee-text { animation: none; }
}
</style>
