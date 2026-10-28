<template>
  <div class="cropper">
    <div
      ref="frame"
      class="frame"
      :class="{ dragging: drag }"
      tabindex="0"
      role="group"
      aria-label="Banner position"
      :aria-describedby="hintId"
      @pointerdown="startDrag"
      @pointermove="moveDrag"
      @pointerup="endDrag"
      @pointercancel="endDrag"
      @keydown="nudge"
    >
      <img :src="src" :style="imageStyle" alt="" draggable="false" />
    </div>
    <p :id="hintId" class="hint">Drag to reposition, or use the arrow keys. Zoom with the slider or + and −.</p>
    <div class="zoom-row">
      <label :for="zoomId" class="zoom-label">Zoom</label>
      <input
        :id="zoomId"
        class="zoom"
        type="range"
        :min="MIN_ZOOM"
        :max="MAX_ZOOM"
        step="0.01"
        :value="modelValue.zoom"
        @input="setZoom(Number($event.target.value))"
      />
      <button type="button" class="btn text" @click="emit('update:modelValue', { ...DEFAULT_CROP })">Reset</button>
    </div>
  </div>
</template>

<script setup>
import { computed, ref, useId } from 'vue'
import { DEFAULT_CROP, MAX_ZOOM, MIN_ZOOM, bannerImageStyle, clampCrop, panCrop } from '@/utils/banner.js'

// Positions a picked banner image in a 3:1 frame by dragging, arrow keys and zoom. `width` and `height` are the image's
// natural size; v-model is the crop ({ cx, cy, zoom }) that bannerCropRect turns into the pixels the server crops to
const props = defineProps({
  src: { type: String, required: true },
  width: { type: Number, required: true },
  height: { type: Number, required: true },
  modelValue: { type: Object, required: true },
})
const emit = defineEmits(['update:modelValue'])

const NUDGE = 0.02
const KEY_MOVES = { ArrowLeft: [NUDGE, 0], ArrowRight: [-NUDGE, 0], ArrowUp: [0, NUDGE], ArrowDown: [0, -NUDGE] }

const hintId = `banner-hint-${useId()}`
const zoomId = `banner-zoom-${useId()}`
const frame = ref(null)
const drag = ref(null)
const imageStyle = computed(() => bannerImageStyle(props.modelValue, props.width, props.height))

function setZoom(zoom) {
  emit('update:modelValue', clampCrop({ ...props.modelValue, zoom }, props.width, props.height))
}

function startDrag(event) {
  if (event.button !== 0) return
  frame.value.setPointerCapture?.(event.pointerId)
  drag.value = { pointerId: event.pointerId, x: event.clientX, y: event.clientY, crop: props.modelValue }
}

function moveDrag(event) {
  const current = drag.value
  if (!current || current.pointerId !== event.pointerId) return
  const frameWidth = frame.value.clientWidth
  emit('update:modelValue', panCrop(current.crop, (event.clientX - current.x) / frameWidth, (event.clientY - current.y) / frameWidth, props.width, props.height))
}

function endDrag() {
  drag.value = null
}

function nudge(event) {
  const move = KEY_MOVES[event.key]
  if (move) emit('update:modelValue', panCrop(props.modelValue, move[0], move[1], props.width, props.height))
  else if (event.key === '+' || event.key === '=') setZoom(props.modelValue.zoom + 0.1)
  else if (event.key === '-' || event.key === '_') setZoom(props.modelValue.zoom - 0.1)
  else return
  event.preventDefault()
}
</script>

<style scoped>
.cropper { display: flex; flex-direction: column; gap: var(--space-2); }
.frame { aspect-ratio: 3 / 1; background: var(--purple-soft); border-radius: var(--radius-md); cursor: grab; display: block; overflow: hidden; position: relative; touch-action: none; user-select: none; }
.frame.dragging { cursor: grabbing; }
.frame:focus-visible { outline: 2px solid var(--focus-ring); outline-offset: 2px; }
.frame img { display: block; pointer-events: none; }
.hint { color: var(--ink-muted); font: 500 12px/16px var(--font-sans); margin: 0; }
.zoom-row { align-items: center; display: flex; gap: var(--space-3); }
.zoom-label { font: 600 14px/20px var(--font-sans); }
.zoom { accent-color: var(--primary); flex: 1; }
</style>
