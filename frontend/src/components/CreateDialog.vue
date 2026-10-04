<template>
  <dialog ref="dialogEl" class="create" aria-labelledby="create-title" @close="onClose" @click.self="close">
    <div class="card">
      <StepBanner class="banner" :replay-key="replayKey" />
      <div class="body">
        <h2 id="create-title">What do you want to make?</h2>
        <p class="lead">Upload a music piece you own, or gather music into a playlist.</p>

        <div class="choices">
          <router-link to="/upload" class="choice" @click="close">
            <span class="choice-icon">
              <svg class="icon" viewBox="0 0 24 24" aria-hidden="true">
                <path d="M9 18V5l12-2v13" /><circle cx="6" cy="18" r="3" /><circle cx="18" cy="16" r="3" />
              </svg>
            </span>
            <span class="choice-name">Music piece</span>
            <span class="choice-text">Upload an MP3 with its cover and artist.</span>
          </router-link>

          <router-link to="/create" class="choice" @click="close">
            <span class="choice-icon">
              <svg class="icon" viewBox="0 0 24 24" aria-hidden="true">
                <path d="M21 15V6" /><path d="M18.5 18a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5Z" />
                <path d="M12 12H3" /><path d="M16 6H3" /><path d="M12 18H3" />
              </svg>
            </span>
            <span class="choice-name">Playlist</span>
            <span class="choice-text">Name it, add a cover, then fill it with music.</span>
          </router-link>
        </div>

        <div class="actions">
          <button type="button" class="btn secondary" @click="close">Cancel</button>
        </div>
      </div>
    </div>
  </dialog>
</template>

<script setup>
import { onBeforeUnmount, ref } from 'vue'
import StepBanner from '@/components/StepBanner.vue'

// Asks whether to make a music piece or a playlist. The parent calls `open()`; Esc, Cancel or a click on the
// backdrop closes it
const dialogEl = ref(null)
const replayKey = ref('')

function open() {
  replayKey.value = String(Date.now())
  dialogEl.value.showModal()
  document.documentElement.classList.add('scroll-locked')
}

function close() {
  dialogEl.value.close()
}

function onClose() {
  document.documentElement.classList.remove('scroll-locked')
}

onBeforeUnmount(onClose)

defineExpose({ open })
</script>

<style scoped>
.create { background: none; border: 0; color: var(--ink); font: 400 16px/24px var(--font-sans); max-width: none; overscroll-behavior: contain; padding: 0; width: min(560px, calc(100% - 32px)); }
.create::backdrop { background: rgb(0 0 0 / 0.45); }
.create[open] .card { animation: card-in var(--dur-med) var(--ease-pop) both; }
.card { background: var(--surface); border: 1px solid var(--line); border-radius: var(--radius-md); overflow: hidden; }
.body { display: flex; flex-direction: column; gap: var(--space-2); padding: var(--space-6); }
h2 { font: 700 28px/34px var(--font-sans); margin: 0; }
.lead { color: var(--ink-muted); margin: 0 0 var(--space-4); }

.choices { display: grid; gap: var(--space-4); grid-template-columns: 1fr 1fr; }
.choice { align-items: flex-start; background: var(--surface-alt); border: 2px solid transparent; border-radius: var(--radius-md); color: var(--ink); display: flex; flex-direction: column; gap: var(--space-1); padding: var(--space-4); text-decoration: none; transition: background var(--dur-fast) var(--ease), border-color var(--dur-fast) var(--ease); }
.choice:hover { background: var(--purple-soft); border-color: var(--primary); }
.choice:hover .choice-name { color: var(--link); }
.choice-icon { align-items: center; background: var(--primary); border-radius: var(--radius-sm); color: var(--on-primary); display: flex; height: 48px; justify-content: center; margin-bottom: var(--space-2); width: 48px; }
.icon { fill: none; height: 24px; stroke: currentColor; stroke-linecap: round; stroke-linejoin: round; stroke-width: 2; width: 24px; }
.choice-name { font: 600 16px/24px var(--font-sans); }
.choice-text { color: var(--ink-muted); font: 500 14px/20px var(--font-sans); }

.actions { display: flex; margin-top: var(--space-4); }
.create :focus-visible { outline: 2px solid var(--focus-ring); outline-offset: 2px; }

@media (max-width: 520px) {
  .choices { grid-template-columns: 1fr; }
}
</style>
