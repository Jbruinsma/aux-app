<template>
  <dialog ref="dialogEl" class="leave" aria-labelledby="leave-title" aria-describedby="leave-text" @close="onClose">
    <form method="dialog" class="body" @submit.prevent="continueToSite">
      <h2 id="leave-title">You're leaving Aux</h2>
      <p id="leave-text" class="text">
        You're about to open <strong class="host">{{ host }}</strong>, a site Aux has no control over.
      </p>
      <p class="url">{{ pendingExternalLink }}</p>
      <label class="save">
        <input v-model="saveOption" type="checkbox" />
        Save my option
      </label>
      <p class="hint">You can turn the warning back on in Settings.</p>
      <div class="actions">
        <button type="button" class="btn secondary" autofocus @click="goBack">Go back</button>
        <button type="submit" class="btn primary">Continue</button>
      </div>
    </form>
  </dialog>
</template>

<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { openExternal, pendingExternalLink, setSkipExternalWarning } from '@/utils/externalLinks.js'

// Mounted once in App.vue; any ExternalLink can open it by setting pendingExternalLink
const dialogEl = ref(null)
const saveOption = ref(false)

const host = computed(() => {
  try {
    return new URL(pendingExternalLink.value).host
  } catch {
    return ''
  }
})

watch(pendingExternalLink, (url) => {
  if (url && !dialogEl.value.open) {
    saveOption.value = false
    dialogEl.value.showModal()
    // The page behind can't scroll while the dialog is up (see .scroll-locked in aux.css)
    document.documentElement.classList.add('scroll-locked')
  }
}, { flush: 'post' })

function goBack() {
  dialogEl.value.close()
}

function continueToSite() {
  if (saveOption.value) setSkipExternalWarning(true)
  openExternal(pendingExternalLink.value)
  dialogEl.value.close()
}

// Esc also closes the dialog, which counts as Go back
function onClose() {
  pendingExternalLink.value = null
  document.documentElement.classList.remove('scroll-locked')
}

onBeforeUnmount(() => document.documentElement.classList.remove('scroll-locked'))
</script>

<style scoped>
.leave { overscroll-behavior: contain; width: min(440px, calc(100% - 32px)); padding: 0; border: 1px solid var(--line); border-radius: var(--radius-md); background: var(--surface); color: var(--ink); font: 400 16px/24px var(--font-sans); }
.leave::backdrop { background: rgb(0 0 0 / 0.45); }
.body { display: flex; flex-direction: column; gap: var(--space-3); padding: var(--space-6); }
h2 { margin: 0; font: 700 20px/28px var(--font-sans); }
.text { margin: 0; }
.host { font-weight: 600; overflow-wrap: anywhere; }
.url { margin: 0; padding: var(--space-2) var(--space-3); background: var(--surface-alt); border-radius: var(--radius-sm); font: 500 14px/20px var(--font-sans); color: var(--ink-muted); overflow-wrap: anywhere; }
.save { display: flex; align-items: center; gap: var(--space-2); font: 600 14px/20px var(--font-sans); cursor: pointer; }
.save input { width: 16px; height: 16px; accent-color: var(--primary); }
.hint { margin: calc(-1 * var(--space-2)) 0 0 24px; font: 500 12px/16px var(--font-sans); color: var(--ink-muted); }
.actions { display: flex; justify-content: flex-start; gap: var(--space-3); margin-top: var(--space-2); }
.btn { font: 600 15px/22px var(--font-sans); padding: 10px 18px; border-radius: var(--radius-sm); border: 1px solid transparent; cursor: pointer; }
.btn.primary { background: var(--primary); color: var(--on-primary); }
.btn.primary:hover { background: var(--purple-hover); }
.btn.secondary { background: var(--surface); color: var(--ink); border-color: var(--line-strong); }
.btn.secondary:hover { background: var(--surface-alt); }
.leave :focus-visible { outline: 2px solid var(--focus-ring); outline-offset: 2px; }
</style>
