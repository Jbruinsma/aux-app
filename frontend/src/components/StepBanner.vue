<template>
  <div class="banner" :class="{ alt, finish: finishing }" aria-hidden="true">
    <span class="blk purple">
      <span :key="`${replayKey}-${finishing}`" class="bars"><i v-for="n in 7" :key="n" /></span>
    </span>
    <span class="blk ink" />
    <span class="blk soft" />
    <span class="blk gray" />
  </div>
</template>

<script setup>
// Brand-block header for step-by-step cards (onboarding, create playlist). `alt` reshapes the blocks for the next
// step, a new `replayKey` bounces the bars again, and `finishing` makes the blocks hop before leaving the page.
// The parent sets margins so the banner bleeds to the card's edges.
defineProps({
  alt: { type: Boolean, default: false },
  finishing: { type: Boolean, default: false },
  replayKey: { type: String, default: '' },
})
</script>

<style scoped>
.banner { border-radius: var(--radius-md) var(--radius-md) 0 0; display: flex; gap: var(--space-2); height: 72px; overflow: hidden; padding: 0 var(--space-4); }
.blk { border-radius: 0 0 var(--radius-md) var(--radius-md); display: block; position: relative; transition: flex-grow var(--dur-med) var(--ease-pop), height var(--dur-med) var(--ease-pop); }
.purple { background: var(--purple); flex-grow: 3; height: 100%; }
.ink { background: var(--ink); flex-grow: 2; height: 45%; }
.soft { background: var(--purple-soft); flex-grow: 2; height: 100%; }
.gray { align-self: flex-end; background: var(--line-strong); border-radius: var(--radius-md) var(--radius-md) 0 0; flex-grow: 2; height: 60%; }
.alt { height: 96px; }
.alt .purple { flex-grow: 2; }
.alt .ink { flex-grow: 3; height: 70%; }
.alt .soft { flex-grow: 1; }
.alt .gray { flex-grow: 3; height: 40%; }

.bars { align-items: flex-end; display: flex; height: 44px; inset: auto var(--space-3) var(--space-2); justify-content: space-between; position: absolute; }
.bars i { animation: bar-bounce calc(var(--dur-med) * 2) var(--ease-pop) both; background: var(--white); border-radius: 2px; transform-origin: bottom; width: 4px; }
.bars i:nth-child(1) { height: 35%; }
.bars i:nth-child(2) { animation-delay: 60ms; height: 70%; }
.bars i:nth-child(3) { animation-delay: 120ms; height: 50%; }
.bars i:nth-child(4) { animation-delay: 30ms; height: 100%; }
.bars i:nth-child(5) { animation-delay: 90ms; height: 60%; }
.bars i:nth-child(6) { animation-delay: 150ms; height: 80%; }
.bars i:nth-child(7) { animation-delay: 70ms; height: 45%; }
@keyframes bar-bounce {
  0% { transform: scaleY(0.15); }
  45% { transform: scaleY(1.15); }
  70% { transform: scaleY(0.85); }
}

.finish .blk { animation: hop calc(var(--dur-med) * 1.5) var(--ease-pop) both; }
.finish .ink { animation-delay: 70ms; }
.finish .soft { animation-delay: 140ms; }
.finish .gray { animation-delay: 210ms; }
@keyframes hop {
  40% { transform: translateY(8px) scaleY(1.08); }
}
</style>
