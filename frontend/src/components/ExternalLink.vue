<template>
  <a v-if="url" :href="url" target="_blank" rel="noopener noreferrer nofollow" @click="onClick"><slot /></a>
  <span v-else><slot /></span>
</template>

<script setup>
import { computed } from 'vue'
import { pendingExternalLink, safeExternalUrl, skipExternalWarning } from '@/utils/externalLinks.js'

// A link to another site. It asks first (see ExternalLinkDialog) unless the viewer saved their choice,
// in which case it opens in a new tab straight away. Anything that isn't an http(s) link renders as plain text.
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
