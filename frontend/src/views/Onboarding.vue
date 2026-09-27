<template>
  <div class="aux-page">
    <AppHeader />

    <main class="stage">
      <div v-if="known" class="ob-card" :class="{ split: step === 'PFP' }">
        <!-- The home page blocks, cropped to a banner. The sign-up page's blocks morph into it (view-transition-name: brand-blocks) -->
        <div ref="banner" class="banner" :class="{ finish: finishing }" aria-hidden="true">
          <span class="blk purple">
            <!-- Re-keyed per step so the equalizer plays again on every step change -->
            <span :key="`${step}-${finishing}`" class="bars"><i v-for="n in 7" :key="n" /></span>
          </span>
          <span class="blk ink" />
          <span class="blk soft" />
          <span class="blk gray" />
        </div>

        <Transition name="pfp-in">
          <div v-if="step === 'PFP'" class="pfp">
            <span class="ring">
            <label
              class="drop"
              :class="{ over: dragging, filled: previewUrl }"
              for="onboarding-photo"
              @dragover.prevent="dragging = true"
              @dragleave="dragging = false"
              @drop.prevent="onDrop"
            >
              <img v-if="previewUrl" :src="previewUrl" alt="Your new profile photo" />
              <span v-else class="drop-hint">
                <svg class="icon" viewBox="0 0 24 24" aria-hidden="true">
                  <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" /><polyline points="17 8 12 3 7 8" /><line x1="12" x2="12" y1="3" y2="15" />
                </svg>
                Drop a photo or click to choose
              </span>
            </label>
            </span>
            <input
              id="onboarding-photo"
              ref="fileInput"
              class="visually-hidden"
              type="file"
              accept="image/jpeg,image/png"
              aria-describedby="onboarding-photo-hint"
              @change="onPick"
            />
            <button v-if="previewUrl" type="button" class="btn text" @click="fileInput.click()">Choose a different photo</button>
          </div>
        </Transition>

        <div class="side">
          <form v-if="step === 'USERNAME'" class="name-form" @submit.prevent="submitUsername">
            <p class="eyebrow">Step 1 of 2</p>
            <h1 ref="heading" tabindex="-1">Pick a username</h1>
            <p class="lead">This is how people find you on Aux. You can't change it yet, so choose carefully.</p>

            <label class="label" for="onboarding-username">Username</label>
            <input
              id="onboarding-username"
              ref="nameEl"
              v-model.trim="username"
              class="input"
              type="text"
              autocomplete="username"
              autocapitalize="off"
              spellcheck="false"
              maxlength="16"
              required
              autofocus
              aria-describedby="onboarding-username-status"
              @input="checkAvailability"
            />
            <p id="onboarding-username-status" class="status" :class="nameStatus" aria-live="polite">
              <svg v-if="nameStatus === 'available'" class="icon" viewBox="0 0 24 24" aria-hidden="true"><path d="M20 6 9 17l-5-5" /></svg>
              <svg v-else-if="nameStatus === 'taken' || nameStatus === 'invalid'" class="icon" viewBox="0 0 24 24" aria-hidden="true">
                <path d="M18 6 6 18" /><path d="m6 6 12 12" />
              </svg>
              {{ statusText }}
            </p>

            <p v-if="errorMessage" class="error" role="alert">{{ errorMessage }}</p>

            <button type="submit" class="btn primary submit" :disabled="submitting || nameStatus === 'taken' || nameStatus === 'invalid'">
              Save username
            </button>
          </form>

          <div v-else class="name-done">
            <p ref="nameEl" class="handle">
              @{{ userStore.userData.username }}
              <svg class="icon" viewBox="0 0 24 24" aria-label="Saved"><path d="M20 6 9 17l-5-5" /></svg>
            </p>
            <p class="eyebrow">Step 2 of 2</p>
            <h1 ref="heading" tabindex="-1">Add a profile photo</h1>
            <p id="onboarding-photo-hint" class="lead">A JPEG or PNG, at least 256 × 256 pixels and up to 5MB. We'll crop it to a square.</p>

            <p v-if="errorMessage" class="error" role="alert">{{ errorMessage }}</p>

            <button type="button" class="btn primary submit" :disabled="!photo || submitting" @click="submitPhoto">Upload photo</button>
          </div>
        </div>
      </div>
    </main>

    <AppFooter />
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import AppHeader from '@/components/AppHeader.vue'
import AppFooter from '@/components/AppFooter.vue'
import { fetchAPI, postToAPI } from '@/utils/api.js'
import { useUserStore } from '@/stores/user.js'
import { API_BASE_URL } from '@/utils/variables.js'
import { PHOTO_ERRORS, photoProblem } from '@/utils/photo.js'

// Same rule the backend enforces; checking here saves a round trip
const USERNAME_PATTERN = /^[A-Za-z0-9_]{3,16}$/

const router = useRouter()
const userStore = useUserStore()
const step = computed(() => userStore.userData.onboardingStep)

// Nothing renders until the step is known (older saved sessions don't have one)
const known = computed(() => step.value === 'USERNAME' || step.value === 'PFP')
const finishing = ref(false)
const banner = ref(null)

const heading = ref(null)
const nameEl = ref(null)
const fileInput = ref(null)
const errorMessage = ref('')
const submitting = ref(false)

async function refreshStatus() {
  try {
    const onboardingStep = await fetchAPI(`${API_BASE_URL}/api/users/onboarding/status`)
    userStore.updateUser({ onboardingStep })
    if (onboardingStep === 'DONE') await router.replace({ name: 'Dashboard' })
  } catch (err) {
    if (err.status === 401) {
      userStore.logout()
      await router.replace({ name: 'Login' })
    }
  }
}

// The server has the real step (another tab or device may have moved it on)
onMounted(refreshStatus)

function sendStep(stepName, field, value) {
  const form = new FormData()
  form.append('step', stepName)
  form.append(field, value)
  return postToAPI(`${API_BASE_URL}/api/users/onboarding/step`, form)
}

/* Username step */

const username = ref('')
const nameStatus = ref('idle') // idle | checking | available | taken | invalid
let checkTimer = null

const statusText = computed(() => ({
  idle: '3 to 16 characters: letters, numbers and underscores.',
  checking: 'Checking…',
  available: `${username.value} is available.`,
  taken: `${username.value} is taken. Try another one.`,
  invalid: 'Use 3 to 16 letters, numbers or underscores.',
})[nameStatus.value])

function checkAvailability() {
  clearTimeout(checkTimer)
  errorMessage.value = ''
  const candidate = username.value
  if (!candidate) return (nameStatus.value = 'idle')
  if (!USERNAME_PATTERN.test(candidate)) return (nameStatus.value = 'invalid')

  nameStatus.value = 'checking'
  checkTimer = setTimeout(async () => {
    try {
      const { exists } = await fetchAPI(`${API_BASE_URL}/api/users/check-username/${encodeURIComponent(candidate)}`)
      // Ignore answers for a name the user has already typed past
      if (candidate === username.value) nameStatus.value = exists ? 'taken' : 'available'
    } catch {
      if (candidate === username.value) nameStatus.value = 'idle' // the save still checks
    }
  }, 300)
}

async function submitUsername() {
  if (!USERNAME_PATTERN.test(username.value)) return (nameStatus.value = 'invalid')
  errorMessage.value = ''
  submitting.value = true
  try {
    const user = await sendStep('USERNAME', 'username', username.value)
    await moveToPhotoStep(user)
  } catch (err) {
    if (err.code === 'USERNAME_TAKEN') nameStatus.value = 'taken'
    else if (err.code === 'INVALID_USERNAME') nameStatus.value = 'invalid'
    else if (err.code === 'WRONG_ONBOARDING_STEP') await refreshStatus()
    else errorMessage.value = "We couldn't save your username. Check that the server is running, then try again."
  } finally {
    submitting.value = false
  }
}

// FLIP: measure the typed name, swap the step, then slide the saved @name from where the text was
async function moveToPhotoStep(user) {
  const from = nameEl.value.getBoundingClientRect()
  userStore.updateUser(user)
  await nextTick()
  heading.value?.focus()

  if (matchMedia('(prefers-reduced-motion: reduce)').matches) return
  const handle = nameEl.value
  const to = handle.getBoundingClientRect()
  const styles = getComputedStyle(document.documentElement)
  const scale = 16 / parseFloat(getComputedStyle(handle).fontSize) // input text is 16px
  const dx = from.left + 12 - to.left // 12px is the input's left padding
  const dy = from.top + from.height / 2 - (to.top + to.height / 2)
  handle.animate(
    [{ transform: `translate(${dx}px, ${dy}px) scale(${scale})` }, { transform: 'none' }],
    { duration: parseFloat(styles.getPropertyValue('--dur-med')), easing: styles.getPropertyValue('--ease').trim() },
  )
}

/* Photo step */

const photo = ref(null)
const previewUrl = ref('')
const dragging = ref(false)

onBeforeUnmount(() => {
  clearTimeout(checkTimer)
  URL.revokeObjectURL(previewUrl.value)
})

function onPick(event) {
  const [file] = event.target.files
  if (file) choosePhoto(file)
}

function onDrop(event) {
  dragging.value = false
  const [file] = event.dataTransfer.files
  if (file) choosePhoto(file)
}

// Send the original file; the server crops and resizes, so there's nothing to edit here
async function choosePhoto(file) {
  errorMessage.value = ''
  const problem = await photoProblem(file)
  if (problem) {
    errorMessage.value = problem
    return
  }
  URL.revokeObjectURL(previewUrl.value)
  photo.value = file
  previewUrl.value = URL.createObjectURL(file)
}

async function submitPhoto() {
  if (!photo.value || submitting.value) return
  errorMessage.value = ''
  submitting.value = true
  try {
    const user = await sendStep('PFP', 'file', photo.value)
    // Send-off: the banner blocks hop in turn, then on to the dashboard
    finishing.value = true
    await nextTick()
    await Promise.all(banner.value.getAnimations({ subtree: true }).map((a) => a.finished))
    userStore.updateUser(user)
    await router.push({ name: 'Dashboard' })
  } catch (err) {
    if (err.code === 'WRONG_ONBOARDING_STEP') await refreshStatus()
    else errorMessage.value = PHOTO_ERRORS[err.code] ?? "We couldn't upload your photo. Check that the server is running, then try again."
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.stage { display: flex; flex-direction: column; align-items: center; gap: var(--space-6); padding: 56px var(--space-6) var(--space-8); }

/* Banner: the home page's brand blocks cropped to a strip, bleeding off the top of the card.
   Sizes shift between steps; flex-grow and height are fine to animate on something this small */
.banner {
  grid-column: 1 / -1;
  display: flex;
  gap: var(--space-2);
  height: 72px;
  margin: calc(-1 * var(--space-8)) calc(-1 * var(--space-6)) 0;
  padding: 0 var(--space-4);
  overflow: hidden;
  border-radius: var(--radius-md) var(--radius-md) 0 0;
  view-transition-name: brand-blocks;
}
.blk { position: relative; display: block; border-radius: 0 0 var(--radius-md) var(--radius-md); transition: flex-grow var(--dur-med) var(--ease-pop), height var(--dur-med) var(--ease-pop); }
.purple { flex-grow: 3; height: 100%; background: var(--purple); }
.ink { flex-grow: 2; height: 45%; background: var(--ink); }
.soft { flex-grow: 2; height: 100%; background: var(--purple-soft); }
.gray { flex-grow: 2; height: 60%; align-self: flex-end; border-radius: var(--radius-md) var(--radius-md) 0 0; background: var(--line-strong); }
.split .banner { height: 96px; }
.split .purple { flex-grow: 2; }
.split .ink { flex-grow: 3; height: 70%; }
.split .soft { flex-grow: 1; }
.split .gray { flex-grow: 3; height: 40%; }

.bars { position: absolute; inset: auto var(--space-3) var(--space-2); display: flex; align-items: flex-end; justify-content: space-between; height: 44px; }
.bars i { width: 4px; border-radius: 2px; background: var(--white); transform-origin: bottom; animation: bar-bounce calc(var(--dur-med) * 2) var(--ease-pop) both; }
.bars i:nth-child(1) { height: 35%; }
.bars i:nth-child(2) { height: 70%; animation-delay: 60ms; }
.bars i:nth-child(3) { height: 50%; animation-delay: 120ms; }
.bars i:nth-child(4) { height: 100%; animation-delay: 30ms; }
.bars i:nth-child(5) { height: 60%; animation-delay: 90ms; }
.bars i:nth-child(6) { height: 80%; animation-delay: 150ms; }
.bars i:nth-child(7) { height: 45%; animation-delay: 70ms; }
@keyframes bar-bounce {
  0% { transform: scaleY(0.15); }
  45% { transform: scaleY(1.15); }
  70% { transform: scaleY(0.85); }
}

/* Send-off after the photo uploads: each block hops in turn */
.finish .blk { animation: hop calc(var(--dur-med) * 1.5) var(--ease-pop) both; }
.finish .ink { animation-delay: 70ms; }
.finish .soft { animation-delay: 140ms; }
.finish .gray { animation-delay: 210ms; }
@keyframes hop {
  40% { transform: translateY(8px) scaleY(1.08); }
}

.eyebrow { margin: 0; font: 600 12px/16px var(--font-sans); color: var(--ink-muted); }


/* One fixed width for both steps, so only the content moves, never the card */
.ob-card {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: var(--space-8);
  width: 100%;
  max-width: 720px;
  padding: var(--space-8) var(--space-6);
  border: 1px solid var(--line);
  border-radius: var(--radius-md);
  background: var(--surface);
  view-transition-name: auth-card;
}
.ob-card.split { grid-template-columns: 252px minmax(0, 1fr); align-items: center; }
/* The username step keeps a narrow column centered under the full-width banner */
.side { width: 100%; max-width: 360px; justify-self: center; }
.split .side { max-width: none; }

h1 { font: 700 28px/34px var(--font-sans); margin: 0 0 var(--space-2); }
h1:focus { outline: none; }
.lead { color: var(--ink-muted); margin: 0 0 var(--space-4); }
.name-form, .name-done { display: flex; flex-direction: column; gap: var(--space-2); }
.label { font: 600 14px/20px var(--font-sans); }
.input {
  font: 400 16px/24px var(--font-sans);
  padding: 8px 12px;
  color: var(--ink);
  background: var(--surface);
  border: 1px solid var(--line-strong);
  border-radius: var(--radius-sm);
}
.submit { margin-top: var(--space-4); align-self: flex-start; }
.error { color: var(--danger); font: 500 14px/20px var(--font-sans); margin: 0; }

.icon { width: 16px; height: 16px; flex: none; fill: none; stroke: currentColor; stroke-width: 2; stroke-linecap: round; stroke-linejoin: round; }
.status { display: flex; align-items: center; gap: var(--space-1); margin: 0; min-height: 20px; font: 500 14px/20px var(--font-sans); color: var(--ink-muted); }
.status.available { color: var(--success); }
.status.taken, .status.invalid { color: var(--danger); }

.handle { display: flex; align-items: center; gap: var(--space-2); margin: 0 0 var(--space-2); font: 700 20px/28px var(--font-sans); color: var(--link); transform-origin: left center; }
.handle .icon { color: var(--success); width: 20px; height: 20px; }

.pfp { position: relative; z-index: 1; display: flex; flex-direction: column; align-items: center; gap: var(--space-2); margin-top: -88px; }
/* A surface-colored ring so the photo circle reads cleanly over the banner */
.ring { display: block; padding: 6px; border-radius: var(--radius-pill); background: var(--surface); }
.drop {
  display: grid;
  place-items: center;
  width: 240px;
  height: 240px;
  border: 2px dashed var(--line-strong);
  border-radius: var(--radius-pill);
  background: var(--surface-alt);
  cursor: pointer;
  overflow: hidden;
  transition: border-color var(--dur-fast) var(--ease), background var(--dur-fast) var(--ease);
}
.drop:hover, .drop.over { border-color: var(--primary); background: var(--purple-soft); }
.drop.filled { border-style: solid; border-color: var(--line); }
.pfp:has(input:focus-visible) .drop { outline: 2px solid var(--focus-ring); outline-offset: 2px; }
.drop img { width: 100%; height: 100%; object-fit: cover; }
.drop-hint { display: flex; flex-direction: column; align-items: center; gap: var(--space-2); padding: var(--space-6); text-align: center; font: 500 14px/20px var(--font-sans); color: var(--ink-muted); }
.drop-hint .icon { width: 24px; height: 24px; }
.visually-hidden { position: absolute; width: 1px; height: 1px; overflow: hidden; clip-path: inset(50%); white-space: nowrap; }

/* The photo column arrives while the @name slides over (see moveToPhotoStep) */
.pfp-in-enter-active { transition: opacity var(--dur-med) var(--ease), transform var(--dur-med) var(--ease); }
.pfp-in-enter-from { opacity: 0; transform: scale(0.96); }

@media (max-width: 720px) {
  .stage { padding: var(--space-6) var(--space-4); }
  .ob-card { padding: var(--space-6) var(--space-4); }
  .banner { margin: calc(-1 * var(--space-6)) calc(-1 * var(--space-4)) 0; }
  .ob-card.split { grid-template-columns: minmax(0, 1fr); }
  .drop { width: 200px; height: 200px; }
}
</style>
