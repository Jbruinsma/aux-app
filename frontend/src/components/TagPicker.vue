<template>
  <div class="tag-picker">
    <label class="label" :for="inputId">Tags <span class="optional">(optional)</span></label>
    <ul v-if="modelValue.length" class="chips" aria-label="Selected tags">
      <li v-for="tag in modelValue" :key="tag" class="chip">
        <span aria-hidden="true">✓</span> {{ tag }}
        <button type="button" :disabled="disabled" :aria-label="`Remove ${tag}`" @click="remove(tag)">×</button>
      </li>
    </ul>
    <input :id="inputId" ref="input" v-model="query" class="input" type="text" autocomplete="off"
      placeholder="Genre, mood or your own tag" :disabled="disabled || modelValue.length >= 5"
      :aria-describedby="`${inputId}-hint ${inputId}-error`" :aria-invalid="!!(problem || error)"
      @keydown.enter.prevent="!$event.isComposing && add(query)" />
    <p :id="`${inputId}-hint`" class="hint">{{ modelValue.length ? `${modelValue.length} / 5 tags. Press Enter to add your own.` : 'Add tags to help people find this. Press Enter to add your own.' }}</p>
    <p :id="`${inputId}-error`" class="error" :role="problem || error ? 'alert' : undefined">{{ problem || error }}</p>
    <p v-if="loading" class="hint" role="status">Loading suggestions…</p>
    <p v-else-if="suggestionError" class="hint">Suggestions couldn't load. You can still type your own tags.</p>
    <ul v-else-if="available.length && modelValue.length < 5" class="suggestions" aria-label="Suggested tags">
      <li v-for="tag in available" :key="tag"><button type="button" :disabled="disabled" @click="add(tag)">{{ tag }}</button></li>
    </ul>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, ref, useId, watch } from 'vue'
import { fetchTags, normalizeTag, tagProblem } from '@/utils/tags.js'

const props = defineProps({ modelValue: { type: Array, default: () => [] }, error: { type: String, default: '' }, disabled: Boolean })
const emit = defineEmits(['update:modelValue'])
const inputId = `tags-${useId()}`
const input = ref(null)
const query = ref('')
const problem = ref('')
const suggestions = ref([])
const loading = ref(false)
const suggestionError = ref(false)
const available = computed(() => suggestions.value.filter((tag) => !props.modelValue.includes(tag)))
let timer
let requestId = 0

watch(query, (value) => {
  clearTimeout(timer)
  const id = ++requestId
  problem.value = ''
  suggestions.value = []
  loading.value = true
  const load = async () => {
    try {
      const result = await fetchTags(value)
      if (id !== requestId) return
      suggestions.value = Array.isArray(result) ? result : []
      suggestionError.value = false
    } catch {
      if (id === requestId) suggestionError.value = true
    } finally {
      if (id === requestId) loading.value = false
    }
  }
  if (value) timer = setTimeout(load, 250)
  else load()
}, { immediate: true })

function add(value) {
  if (props.disabled || props.modelValue.length >= 5) return
  const tag = normalizeTag(value)
  problem.value = tagProblem(tag)
  if (problem.value) return
  if (!props.modelValue.includes(tag)) emit('update:modelValue', [...props.modelValue, tag])
  query.value = ''
}

function remove(tag) {
  emit('update:modelValue', props.modelValue.filter((item) => item !== tag))
  problem.value = ''
}

onBeforeUnmount(() => { clearTimeout(timer); requestId++ })
defineExpose({ focus: () => input.value?.focus() })
</script>

<style scoped>
.tag-picker { display: flex; flex-direction: column; gap: var(--space-2); min-width: 0; }
.label { font: 600 14px/20px var(--font-sans); }
.optional { color: var(--ink-muted); font-weight: 400; }
.input { background: var(--surface); border: 1px solid var(--line-strong); border-radius: var(--radius-sm); color: var(--ink); font: 400 16px/24px var(--font-sans); padding: 8px 12px; width: 100%; }
.input:disabled { opacity: .6; }
.chips, .suggestions { display: flex; flex-wrap: wrap; gap: var(--space-2); list-style: none; margin: 0; padding: 0; }
.chip { align-items: center; background: var(--purple-soft); border-radius: var(--radius-pill); display: flex; font: 600 12px/16px var(--font-sans); gap: var(--space-1); overflow-wrap: anywhere; padding: var(--space-1) var(--space-2); }
.chip button { background: none; border: 0; color: var(--ink); cursor: pointer; font: inherit; padding: var(--space-1); }
.suggestions button { background: var(--surface); border: 1px solid var(--line-strong); border-radius: var(--radius-pill); color: var(--ink); cursor: pointer; font: 600 12px/16px var(--font-sans); padding: var(--space-1) var(--space-3); }
.suggestions button:hover { background: var(--purple-soft); }
.hint { color: var(--ink-muted); font: 500 12px/16px var(--font-sans); margin: 0; }
.error { color: var(--danger); font: 500 14px/20px var(--font-sans); margin: 0; }
.error:empty { display: none; }
</style>
