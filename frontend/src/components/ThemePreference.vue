<template>
  <section class="appearance" aria-labelledby="appearance-heading">
    <h2 id="appearance-heading">Appearance</h2>
    <fieldset aria-describedby="theme-hint">
      <legend>Theme</legend>
      <div class="theme-options">
        <label v-for="choice in choices" :key="choice.value" :class="{ selected: themePreference === choice.value }">
          <input type="radio" name="theme" :value="choice.value" :checked="themePreference === choice.value" @change="setThemePreference(choice.value)" />
          {{ choice.label }}
        </label>
      </div>
    </fieldset>
    <p id="theme-hint">System follows your device’s appearance. Your choice is saved on this device.</p>
  </section>
</template>

<script setup>
import { setThemePreference, themePreference } from '@/utils/theme.js'

const choices = [
  { value: 'system', label: 'System' },
  { value: 'light', label: 'Light' },
  { value: 'dark', label: 'Dark' },
]
</script>

<style scoped>
.appearance { border-bottom: 1px solid var(--line); padding: var(--space-6) 0; }
h2 { color: var(--ink); font: 700 20px/28px var(--font-sans); margin: 0 0 var(--space-4); }
fieldset { border: 0; margin: 0; min-width: 0; padding: 0; }
legend { color: var(--ink); font: 600 14px/20px var(--font-sans); margin-bottom: var(--space-2); padding: 0; }
.theme-options { display: flex; flex-wrap: wrap; gap: var(--space-2); }
label { align-items: center; background: var(--surface); border: 1px solid var(--line-strong); border-radius: var(--radius-sm); color: var(--ink); cursor: pointer; display: flex; font: 500 14px/20px var(--font-sans); gap: var(--space-2); min-height: 44px; padding: var(--space-2) var(--space-4); }
label:hover { background: var(--surface-alt); }
label.selected { background: var(--purple-soft); border-color: var(--link); }
input { accent-color: var(--primary); margin: 0; }
input:focus-visible { outline: 2px solid var(--focus-ring); outline-offset: 2px; }
p { color: var(--ink-muted); font: 500 14px/20px var(--font-sans); margin: var(--space-3) 0 0; }
</style>
