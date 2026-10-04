<template>
  <svg class="default-banner" viewBox="0 0 300 100" preserveAspectRatio="xMidYMid slice" aria-hidden="true">
    <rect class="so" x="12" y="-6" width="84" height="50" rx="4" />
    <rect class="ik" x="102" y="-6" width="40" height="64" rx="4" />
    <rect class="gr" x="148" y="58" width="44" height="48" rx="4" />
    <rect class="pu" x="198" y="-6" width="90" height="98" rx="4" />
    <rect v-for="(h, i) in BARS" :key="i" class="bar" :x="208 + i * 9.5" :y="84 - h" width="5" :height="h" rx="2" />
  </svg>
</template>

<script setup>
// The brand blocks laid out for a 3:1 profile banner. The avatar overlaps the bottom-left corner, so the tall blocks
// and the bars stay right of it
const BARS = [22, 40, 30, 52, 34, 46, 26, 38]
</script>

<style scoped>
.default-banner { display: block; height: 100%; width: 100%; }
.pu { fill: var(--purple); }
.so { fill: var(--purple-soft); }
.gr { fill: var(--line-strong); }
.ik { fill: var(--ink); }
.bar { animation: bar-bounce calc(var(--dur-med) * 2) var(--ease-pop) both; fill: var(--white); transform-box: fill-box; transform-origin: bottom; }
.bar:nth-of-type(odd) { animation-delay: 60ms; }
.bar:nth-of-type(3n) { animation-delay: 120ms; }
@keyframes bar-bounce {
  0% { transform: scaleY(0.15); }
  45% { transform: scaleY(1.15); }
  70% { transform: scaleY(0.85); }
}
</style>
