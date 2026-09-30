<template>
  <a v-if="url" :href="url" target="_blank" rel="noopener noreferrer nofollow" @click="onClick"><slot /></a>
  <span v-else><slot /></span>
</template>

<script setup>
import { computed } from 'vue'
import { pendingExternalLink, safeExternalUrl, skipExternalWarning } from '@/utils/externalLinks.js'

const props = defineProps({
  href: { type: String, required: true },
})

const url = computed(() => safeExternalUrl(props.href))

function onClick(event) {
  if (skipExternalWarning.value) return
  event.preventDefault()
  pendingExternalLink.value = url.value
}
</script>
