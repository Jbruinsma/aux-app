<template>
  <div class="aux-page with-player">
    <AppHeader />

    <main>
      <div class="page-head">
        <div class="wrap">
          <h1>Settings</h1>
          <!-- Logging out empties the username before the redirect, and a link with no username param throws -->
          <nav v-if="username" class="tabs" aria-label="Settings sections">
            <router-link
              v-for="item in TABS"
              :key="item.id"
              class="tab"
              :class="{ active: tab === item.id }"
              :aria-current="tab === item.id ? 'page' : undefined"
              :to="{ name: 'Settings', params: { username, tab: item.id === 'profile' ? undefined : item.id } }"
            >{{ item.label }}</router-link>
          </nav>
        </div>
      </div>

      <div v-if="tab === 'profile'" class="wrap layout">
        <div class="main-col">
          <section aria-labelledby="picture-heading">
            <h2 id="picture-heading">Your picture</h2>
            <div class="avatar-area">
              <img v-if="shownPicture" class="avatar" :src="shownPicture" alt="" />
              <div v-else class="avatar avatar-empty" aria-hidden="true">{{ initial }}</div>
            </div>

            <div class="picture-controls">
                <div class="file-row">
                  <input
                    id="photo-input"
                    ref="fileInput"
                    class="visually-hidden"
                    type="file"
                    accept="image/jpeg,image/png"
                    aria-describedby="photo-hint"
                    @change="onPick"
                  />
                  <label for="photo-input" class="btn secondary">Choose file</label>
                  <span class="file-name">{{ newPhoto ? newPhoto.name : 'No file chosen' }}</span>
                  <button v-if="newPhoto" type="button" class="btn text revert" @click="revertPhoto">Revert</button>
                </div>
                <p id="photo-hint" class="hint">
                  A JPEG or PNG, at least 256 × 256 pixels and under 5MB. It's cropped to a square and shown in a circle,
                  like the example.
                </p>
                <p v-if="photoError" class="error" role="alert">{{ photoError }}</p>
                <p v-if="photoSaved" class="success" role="status">Your new picture is saved.</p>
                <button type="button" class="btn primary" :disabled="!newPhoto || uploading" @click="uploadPhoto">
                  {{ uploading ? 'Uploading…' : 'Upload picture' }}
                </button>
            </div>
          </section>

          <section aria-labelledby="banner-heading">
            <h2 id="banner-heading">Your banner</h2>
            <div
              v-if="newBanner"
              ref="bannerFrame"
              class="banner-frame editing"
              :class="{ dragging: bannerDrag }"
              tabindex="0"
              role="group"
              aria-label="Banner position"
              aria-describedby="banner-adjust-hint"
              @pointerdown="startBannerDrag"
              @pointermove="moveBannerDrag"
              @pointerup="endBannerDrag"
              @pointercancel="endBannerDrag"
              @keydown="nudgeBanner"
            >
              <img :src="bannerPreviewUrl" :style="bannerStyle" alt="" draggable="false" />
            </div>
            <div v-else class="banner-frame">
              <img v-if="savedBanner" class="banner-saved" :src="savedBanner" alt="Your current banner" />
              <p v-else class="banner-empty">No banner yet</p>
            </div>

            <div v-if="newBanner" class="adjust">
              <p id="banner-adjust-hint" class="hint">Drag to reposition, or use the arrow keys. Zoom with the slider or + and −.</p>
              <div class="zoom-row">
                <label for="banner-zoom" class="zoom-label">Zoom</label>
                <input
                  id="banner-zoom"
                  class="zoom"
                  type="range"
                  :min="MIN_ZOOM"
                  :max="MAX_ZOOM"
                  step="0.01"
                  :value="bannerCrop.zoom"
                  @input="setBannerZoom(Number($event.target.value))"
                />
                <button type="button" class="btn text" @click="resetBannerCrop">Reset</button>
              </div>
            </div>
            <div class="picture-controls banner-controls">
              <div class="file-row">
                <input
                  id="banner-input"
                  ref="bannerInput"
                  class="visually-hidden"
                  type="file"
                  accept="image/jpeg,image/png"
                  aria-describedby="banner-hint"
                  @change="onPickBanner"
                />
                <label for="banner-input" class="btn secondary">Choose file</label>
                <span class="file-name">{{ newBanner ? newBanner.name : 'No file chosen' }}</span>
                <button v-if="newBanner" type="button" class="btn text revert" @click="revertBanner">Revert</button>
              </div>
              <p id="banner-hint" class="hint">
                A wide JPEG or PNG, at least 600 × 200 pixels and under 5MB. It's cropped to 3:1, so 1500 × 500 looks
                sharpest. See how it looks on your profile card.
              </p>
              <p v-if="bannerError" class="error" role="alert">{{ bannerError }}</p>
              <p v-if="bannerSaved" class="success" role="status">Your new banner is saved.</p>
              <button type="button" class="btn primary" :disabled="!newBanner || bannerUploading" @click="uploadBanner">
                {{ bannerUploading ? 'Uploading…' : 'Upload banner' }}
              </button>
            </div>
          </section>

          <section class="wide" aria-labelledby="creative-heading">
            <h2 id="creative-heading">Get creative!</h2>
            <form class="details" @submit.prevent="saveDetails">
              <div class="field">
                <label class="row-label" for="display-name">Display name</label>
                <input
                  id="display-name"
                  v-model="details.displayName"
                  class="input"
                  type="text"
                  :maxlength="NAME_MAX"
                  :placeholder="username"
                  :aria-invalid="nameInvalid ? 'true' : 'false'"
                  aria-describedby="name-error"
                />
                <p v-if="nameInvalid" id="name-error" class="error">
                  Use {{ NAME_MIN }} to {{ NAME_MAX }} characters, or leave it empty.
                </p>
              </div>

              <div class="field">
                <label class="row-label" for="country">Country</label>
                <div class="select-wrap">
                  <select id="country" v-model="details.country" class="input select">
                    <option value="">None</option>
                    <option v-for="country in COUNTRIES" :key="country.code" :value="country.code">{{ country.name }}</option>
                  </select>
                </div>
              </div>

              <div class="field">
                <label class="row-label" for="website">Website</label>
                <input
                  id="website"
                  v-model="details.website"
                  class="input"
                  type="url"
                  placeholder="https://"
                  :aria-invalid="websiteInvalid ? 'true' : 'false'"
                  aria-describedby="website-error"
                />
                <p v-if="websiteInvalid" id="website-error" class="error">
                  Enter a full link starting with http:// or https://
                </p>
              </div>

              <div class="field about-field">
                <label class="row-label" for="about">About you</label>
                <textarea
                  id="about"
                  v-model="details.about"
                  class="input textarea"
                  rows="4"
                  :maxlength="ABOUT_LIMIT"
                  aria-describedby="about-hint"
                ></textarea>
                <p id="about-hint" class="hint">
                  {{ details.about.length }} / {{ ABOUT_LIMIT }} characters, plain text only.
                </p>
              </div>

              <div class="wide">
                <button type="submit" class="btn primary" :disabled="detailsSaving || nameInvalid || websiteInvalid">
                  {{ detailsSaving ? 'Saving…' : 'Save changes' }}
                </button>
                <p v-if="detailsError" class="error status-line" role="alert">{{ detailsError }}</p>
                <p v-if="detailsSaved" class="success status-line" role="status">Your details are saved.</p>
              </div>
            </form>
          </section>

          <section aria-labelledby="links-heading">
            <h2 id="links-heading">Links to other sites</h2>
            <label class="check">
              <input v-model="warnBeforeLeaving" type="checkbox" aria-describedby="links-hint" />
              Warn me before opening a link to another site
            </label>
            <p id="links-hint" class="hint">
              Turn this back on if you chose "Save my option" by mistake. This setting is saved on this browser.
            </p>
          </section>
        </div>

        <aside class="side" aria-labelledby="preview-heading">
          <h2 id="preview-heading" class="side-title">How others see you</h2>
          <ProfileCard
            :username="username"
            :display-name="details.displayName"
            :picture-url="shownPicture"
            :banner-url="bannerPreviewUrl || savedBanner"
            :banner-style="bannerPreviewUrl ? bannerStyle : null"
            :country="countryName"
            :about="details.about"
            :website="websiteUrl"
          />
        </aside>
      </div>

      <div v-else-if="tab === 'account'" class="wrap layout">
        <div class="account-col">
          <section aria-labelledby="username-heading">
            <h2 id="username-heading">Change Username</h2>
            <form class="account-form" @submit.prevent="saveUsername">
              <label class="form-label" for="current-username">Current Username</label>
              <input id="current-username" class="input readonly" type="text" :value="currentUsername" readonly />

              <label class="form-label" for="new-username">New Username</label>
              <div class="form-field">
                <input
                  id="new-username"
                  v-model.trim="newUsername"
                  class="input"
                  type="text"
                  autocomplete="off"
                  autocapitalize="off"
                  spellcheck="false"
                  maxlength="16"
                  required
                  :aria-invalid="usernameStatus === 'taken' || usernameStatus === 'invalid' ? 'true' : 'false'"
                  aria-describedby="new-username-status"
                  @input="onUsernameInput"
                />
                <p id="new-username-status" class="status" :class="usernameStatus" aria-live="polite">
                  <svg v-if="usernameStatus === 'available'" class="icon" viewBox="0 0 24 24" aria-hidden="true"><path d="M20 6 9 17l-5-5" /></svg>
                  <svg v-else-if="usernameStatus === 'taken' || usernameStatus === 'invalid'" class="icon" viewBox="0 0 24 24" aria-hidden="true">
                    <path d="M18 6 6 18" /><path d="m6 6 12 12" />
                  </svg>
                  {{ usernameStatusText }}
                </p>
              </div>

              <div class="form-field">
                <button type="submit" class="btn primary" :disabled="!canSaveUsername">
                  {{ usernameSaving ? 'Saving…' : 'Change username' }}
                </button>
                <p v-if="usernameError" class="error" role="alert">{{ usernameError }}</p>
                <p v-if="usernameSaved" class="success" role="status">Your username is now {{ usernameSaved }}.</p>
              </div>
            </form>
          </section>

          <section aria-labelledby="email-heading">
            <h2 id="email-heading">Change Email Address</h2>
            <form class="account-form" @submit.prevent="emailCodeSent ? confirmEmail() : sendEmailCode()">
              <label class="form-label" for="current-email">Current Email Address</label>
              <div class="form-field">
                <input
                  id="current-email"
                  class="input readonly"
                  type="text"
                  :value="currentEmail || (accountError ? 'Not loaded' : 'Loading…')"
                  readonly
                />
                <p v-if="accountError" class="error" role="alert">{{ accountError }}</p>
              </div>

              <label class="form-label" for="new-email">New Email Address</label>
              <div class="form-field">
                <input
                  id="new-email"
                  v-model.trim="newEmail"
                  class="input"
                  :class="{ readonly: emailCodeSent }"
                  :readonly="emailCodeSent"
                  type="email"
                  autocomplete="email"
                  spellcheck="false"
                  :maxlength="EMAIL_MAX"
                  required
                  :aria-invalid="emailStatus === 'invalid' ? 'true' : 'false'"
                  aria-describedby="new-email-status"
                  @input="onEmailInput"
                />
                <p id="new-email-status" class="status" :class="emailStatus" aria-live="polite">
                  <svg v-if="emailStatus === 'invalid'" class="icon" viewBox="0 0 24 24" aria-hidden="true">
                    <path d="M18 6 6 18" /><path d="m6 6 12 12" />
                  </svg>
                  {{ emailStatusText }}
                </p>
              </div>

              <label class="form-label" for="email-password">Current Password</label>
              <div class="form-field">
                <input
                  id="email-password"
                  v-model="emailPassword"
                  class="input"
                  :class="{ readonly: emailCodeSent }"
                  type="password"
                  autocomplete="current-password"
                  required
                  :readonly="emailCodeSent"
                  :aria-invalid="emailPasswordError ? 'true' : 'false'"
                  aria-describedby="email-password-error"
                  @input="emailPasswordError = ''"
                />
                <p v-if="emailPasswordError" id="email-password-error" class="error">{{ emailPasswordError }}</p>
              </div>

              <template v-if="emailCodeSent">
                <label class="form-label" for="email-code">Code</label>
                <div class="form-field">
                  <input
                    id="email-code"
                    ref="emailCodeInput"
                    v-model.trim="emailCode"
                    class="input"
                    type="text"
                    inputmode="numeric"
                    autocomplete="one-time-code"
                    maxlength="6"
                    required
                    :aria-invalid="emailCodeError ? 'true' : 'false'"
                    aria-describedby="email-code-hint"
                    @input="emailCodeError = ''"
                  />
                  <p id="email-code-hint" :class="emailCodeError ? 'error' : 'hint'">
                    {{ emailCodeError || `We sent a 6-digit code to ${newEmail}. It expires in 10 minutes.` }}
                  </p>
                </div>
              </template>

              <div class="form-field">
                <button v-if="!emailCodeSent" type="submit" class="btn primary" :disabled="!canSaveEmail">
                  {{ emailSaving ? 'Sending…' : 'Send code' }}
                </button>
                <template v-else>
                  <button type="submit" class="btn primary" :disabled="!canConfirmEmail">
                    {{ emailSaving ? 'Saving…' : 'Change email address' }}
                  </button>
                  <div class="code-actions">
                    <button type="button" class="btn text" :disabled="emailSaving" @click="sendEmailCode">Send a new code</button>
                    <button type="button" class="btn text" :disabled="emailSaving" @click="cancelEmailChange">Cancel</button>
                  </div>
                </template>
                <p v-if="emailError" class="error" role="alert">{{ emailError }}</p>
                <p v-if="emailSaved" class="success" role="status">Your email address is now {{ emailSaved }}.</p>
              </div>
            </form>
          </section>

          <section aria-labelledby="password-heading">
            <h2 id="password-heading">Change Password</h2>
            <form class="account-form" @submit.prevent="passwordCodeSent ? confirmPassword() : sendPasswordCode()">
              <label class="form-label" for="current-password">Current password</label>
              <div class="form-field">
                <input
                  id="current-password"
                  v-model="password.current"
                  class="input"
                  :class="{ readonly: passwordCodeSent }"
                  :readonly="passwordCodeSent"
                  type="password"
                  autocomplete="current-password"
                  required
                  :aria-invalid="currentPasswordError ? 'true' : 'false'"
                  aria-describedby="current-password-error"
                  @input="currentPasswordError = ''"
                />
                <p v-if="currentPasswordError" id="current-password-error" class="error">{{ currentPasswordError }}</p>
              </div>

              <label class="form-label" for="new-password">New Password</label>
              <div class="form-field">
                <input
                  id="new-password"
                  v-model="password.new"
                  class="input"
                  :class="{ readonly: passwordCodeSent }"
                  :readonly="passwordCodeSent"
                  type="password"
                  autocomplete="new-password"
                  :maxlength="PASSWORD_MAX"
                  required
                  :aria-invalid="newPasswordProblem ? 'true' : 'false'"
                  aria-describedby="new-password-hint"
                />
                <p id="new-password-hint" :class="newPasswordProblem ? 'error' : 'hint'">
                  {{ newPasswordProblem || `${PASSWORD_MIN} to ${PASSWORD_MAX} characters.` }}
                </p>
              </div>

              <label class="form-label" for="confirm-password">Confirm New Password</label>
              <div class="form-field">
                <input
                  id="confirm-password"
                  v-model="password.confirm"
                  class="input"
                  :class="{ readonly: passwordCodeSent }"
                  :readonly="passwordCodeSent"
                  type="password"
                  autocomplete="new-password"
                  required
                  :aria-invalid="passwordsDiffer ? 'true' : 'false'"
                  aria-describedby="confirm-password-error"
                />
                <p v-if="passwordsDiffer" id="confirm-password-error" class="error">
                  Those passwords don't match. Type the same password in both fields.
                </p>
              </div>

              <template v-if="passwordCodeSent">
                <label class="form-label" for="password-code">Code</label>
                <div class="form-field">
                  <input
                    id="password-code"
                    ref="passwordCodeInput"
                    v-model.trim="passwordCode"
                    class="input"
                    type="text"
                    inputmode="numeric"
                    autocomplete="one-time-code"
                    maxlength="6"
                    required
                    :aria-invalid="passwordCodeError ? 'true' : 'false'"
                    aria-describedby="password-code-hint"
                    @input="passwordCodeError = ''"
                  />
                  <p id="password-code-hint" :class="passwordCodeError ? 'error' : 'hint'">
                    {{ passwordCodeError || `We sent a 6-digit code to ${currentEmail}. It expires in 10 minutes.` }}
                  </p>
                </div>
              </template>

              <div class="form-field">
                <button v-if="!passwordCodeSent" type="submit" class="btn primary" :disabled="!canSavePassword">
                  {{ passwordSaving ? 'Sending…' : 'Send code' }}
                </button>
                <template v-else>
                  <button type="submit" class="btn primary" :disabled="!canConfirmPassword">
                    {{ passwordSaving ? 'Saving…' : 'Change password' }}
                  </button>
                  <div class="code-actions">
                    <button type="button" class="btn text" :disabled="passwordSaving" @click="sendPasswordCode">Send a new code</button>
                    <button type="button" class="btn text" :disabled="passwordSaving" @click="cancelPasswordChange">Cancel</button>
                  </div>
                </template>
                <p v-if="passwordError" class="error" role="alert">{{ passwordError }}</p>
                <p v-if="passwordSaved" class="success" role="status">Your password is changed.</p>
              </div>
            </form>
          </section>

          <section aria-labelledby="delete-heading">
            <h2 id="delete-heading">Delete Account</h2>
            <div class="account-form">
              <p class="form-label">Delete Your Account</p>
              <div class="form-field delete-field">
                <p class="notice warning">
                  <svg class="icon" viewBox="0 0 24 24" aria-hidden="true">
                    <path d="m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3" /><path d="M12 9v4" /><path d="M12 17h.01" />
                  </svg>
                  <span><strong>Deleting your account completely wipes your data.</strong> Download or save anything you want to
                  keep first.
                  </span>
                </p>
                <button type="button" class="btn primary" @click="openDeleteDialog">Delete account</button>
                <p v-if="deleteNotice" class="success" role="status">{{ deleteNotice }}</p>
              </div>
            </div>
          </section>
        </div>

        <dialog
          ref="deleteDialog"
          class="confirm"
          aria-labelledby="delete-title"
          aria-describedby="delete-text"
          @cancel="deleting && $event.preventDefault()"
          @close="onDeleteDialogClose"
        >
          <div class="confirm-body">
            <h2 id="delete-title">Are you sure you want to delete your account?</h2>
            <p id="delete-text" class="confirm-text">
              Your profile, playlists, uploaded songs and listening history will be wiped. You can't undo this.
            </p>
            <div
              class="countdown"
              role="progressbar"
              aria-label="Time before you can delete"
              aria-valuemin="0"
              :aria-valuemax="DELETE_WAIT"
              :aria-valuenow="deleteElapsed"
              :style="{ '--progress': deleteElapsed / DELETE_WAIT }"
            >
              <div :key="deleteRun" class="countdown-fill"></div>
            </div>
            <p class="hint">
              {{ deleteReady ? 'You can delete your account now.' : `Take a moment to read this. Delete unlocks in ${DELETE_WAIT - deleteElapsed}s.` }}
            </p>
            <p v-if="deleteError" class="error" role="alert">{{ deleteError }}</p>
            <div class="confirm-actions">
              <button type="button" class="btn secondary" autofocus :disabled="deleting" @click="deleteDialog.close()">
                Keep my account
              </button>
              <button type="button" class="btn primary" :disabled="!deleteReady || deleting" @click="confirmDelete">
                {{ deleting ? 'Deleting…' : 'Delete account' }}
              </button>
            </div>
          </div>
        </dialog>
      </div>

      <div v-else class="wrap layout">
        <section class="main-col" aria-labelledby="apps-heading">
          <div>
            <h2 id="apps-heading">Connect your music apps</h2>
            <p class="intro">Connect Spotify so Aux can learn your taste from what you actually play.</p>
          </div>
          <ul class="apps">
            <li v-for="app in APPS" :key="app.id" class="app">
              <svg class="app-icon" viewBox="0 0 40 40" aria-hidden="true">
                <circle class="spotify" cx="20" cy="20" r="20" />
                <path d="M10 15.5c6.5-2 14-1.4 20 1.8M11 21c5.4-1.6 11.4-1 16.2 1.6M12 26.2c4.2-1.1 8.8-.7 12.5 1.2"
                      fill="none" stroke="#fff" stroke-width="2.6" stroke-linecap="round" />
              </svg>
              <div class="app-text">
                <p class="app-name">{{ app.name }}</p>
                <p class="app-desc">{{ app.description }}</p>
                <p class="app-status">Not connected</p>
              </div>
              <button type="button" class="btn secondary" disabled :aria-describedby="`${app.id}-soon`">Connect</button>
              <p :id="`${app.id}-soon`" class="visually-hidden">Connecting {{ app.name }} is coming soon.</p>
            </li>
          </ul>
          <p class="hint">Spotify connectivity coming soon.</p>
        </section>
      </div>
    </main>

    <AppFooter />
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppHeader from '@/components/AppHeader.vue'
import AppFooter from '@/components/AppFooter.vue'
import ProfileCard from '@/components/ProfileCard.vue'
import { request } from '@/utils/api.js'
import { useUserStore } from '@/stores/user.js'
import { API_BASE_URL } from '@/utils/variables.js'
import { resolveCoverURL } from '@/utils/display.js'
import { BANNER_ERRORS, PHOTO_ERRORS, bannerProblem, photoProblem } from '@/utils/photo.js'
import { DEFAULT_CROP, MAX_ZOOM, MIN_ZOOM, bannerCropRect, bannerImageStyle, clampCrop, panCrop } from '@/utils/banner.js'
import { COUNTRIES } from '@/utils/countries.js'
import { setSkipExternalWarning, skipExternalWarning } from '@/utils/externalLinks.js'
import {
  ACCOUNT_MOCKED,
  EMAIL_MAX,
  EMAIL_MIN,
  PASSWORD_MAX,
  PASSWORD_MIN,
  USERNAME_PATTERN,
  changeUsername,
  checkUsername,
  confirmEmailChange,
  confirmPasswordChange,
  deleteAccount,
  fetchSettings,
  requestEmailChange,
  requestPasswordChange,
} from '@/utils/account.js'

const ABOUT_LIMIT = 200
// Same limits as the backend's ProfileDetailsUpdate
const NAME_MIN = 3
const NAME_MAX = 15

const TABS = [
  { id: 'profile', label: 'Profile' },
  { id: 'account', label: 'Account' },
  { id: 'applications', label: 'Applications' },
]

// Spotify is the one listening service Aux supports; connecting needs a backend sign-in flow that doesn't exist yet
const APPS = [
  {
    id: 'spotify',
    name: 'Spotify',
    description: 'Bring in what you play on Spotify, from desktop, mobile or any other device.',
  },
]

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const username = computed(() => userStore.userData?.username ?? '')
const tab = computed(() => (TABS.some((t) => t.id === route.params.tab) ? route.params.tab : 'profile'))
const initial = computed(() => username.value.charAt(0).toUpperCase())

onMounted(() => {
  if (!userStore.loggedIn) {
    router.replace({ name: 'Login' })
    return
  }
  loadSettings()
})

// A 401 means the session ended: log out and go to the login page
async function handleUnauthorized(err) {
  if (err.status !== 401) return false
  userStore.logout()
  await router.replace({ name: 'Login' })
  return true
}

const fileInput = ref(null)
const newPhoto = ref(null)
const previewUrl = ref('')
const photoError = ref('')
const photoSaved = ref(false)
const uploading = ref(false)

const savedPicture = computed(() => {
  const url = userStore.userData?.profilePictureUrl
  return url ? resolveCoverURL(url) : ''
})
const shownPicture = computed(() => previewUrl.value || savedPicture.value)

onBeforeUnmount(() => {
  URL.revokeObjectURL(previewUrl.value)
  URL.revokeObjectURL(bannerPreviewUrl.value)
})

async function onPick(event) {
  const [file] = event.target.files
  if (!file) return
  photoError.value = ''
  photoSaved.value = false
  const problem = await photoProblem(file)
  if (problem) {
    photoError.value = problem
    clearChoice()
    return
  }
  URL.revokeObjectURL(previewUrl.value)
  newPhoto.value = file
  previewUrl.value = URL.createObjectURL(file)
}

function clearChoice() {
  URL.revokeObjectURL(previewUrl.value)
  previewUrl.value = ''
  newPhoto.value = null
  if (fileInput.value) fileInput.value.value = ''
}

function revertPhoto() {
  photoError.value = ''
  clearChoice()
}

async function uploadPhoto() {
  if (!newPhoto.value || uploading.value) return
  photoError.value = ''
  uploading.value = true
  try {
    const form = new FormData()
    form.append('file', newPhoto.value)
    const { profilePictureUrl } = await request('PUT', `${API_BASE_URL}/api/users/me/profile-picture`, form)
    userStore.updateUser({ profilePictureUrl })
    clearChoice()
    photoSaved.value = true
  } catch (err) {
    if (await handleUnauthorized(err)) return
    photoError.value = PHOTO_ERRORS[err.code] ?? "We couldn't upload your picture. Check that the server is running, then try again."
  } finally {
    uploading.value = false
  }
}

const bannerInput = ref(null)
const bannerFrame = ref(null)
const newBanner = ref(null)
const bannerPreviewUrl = ref('')
const bannerError = ref('')
const bannerSaved = ref(false)
const bannerUploading = ref(false)
const bannerSize = ref(null)
const bannerCrop = ref({ ...DEFAULT_CROP })
const bannerDrag = ref(null)

const bannerStyle = computed(() =>
  bannerSize.value ? bannerImageStyle(bannerCrop.value, bannerSize.value.width, bannerSize.value.height) : null,
)

function setBannerCrop(crop) {
  bannerCrop.value = clampCrop(crop, bannerSize.value.width, bannerSize.value.height)
}

function setBannerZoom(zoom) {
  setBannerCrop({ ...bannerCrop.value, zoom })
}

function resetBannerCrop() {
  bannerCrop.value = { ...DEFAULT_CROP }
}

function startBannerDrag(event) {
  if (event.button !== 0) return
  bannerFrame.value.setPointerCapture(event.pointerId)
  bannerDrag.value = { pointerId: event.pointerId, x: event.clientX, y: event.clientY, crop: bannerCrop.value }
}

function moveBannerDrag(event) {
  const drag = bannerDrag.value
  if (!drag || drag.pointerId !== event.pointerId) return
  const frameWidth = bannerFrame.value.clientWidth
  const { width, height } = bannerSize.value
  bannerCrop.value = panCrop(drag.crop, (event.clientX - drag.x) / frameWidth, (event.clientY - drag.y) / frameWidth, width, height)
}

function endBannerDrag() {
  bannerDrag.value = null
}

const NUDGE = 0.02
const KEY_MOVES = { ArrowLeft: [NUDGE, 0], ArrowRight: [-NUDGE, 0], ArrowUp: [0, NUDGE], ArrowDown: [0, -NUDGE] }

function nudgeBanner(event) {
  const move = KEY_MOVES[event.key]
  const { width, height } = bannerSize.value
  if (move) {
    bannerCrop.value = panCrop(bannerCrop.value, move[0], move[1], width, height)
  } else if (event.key === '+' || event.key === '=') {
    setBannerZoom(bannerCrop.value.zoom + 0.1)
  } else if (event.key === '-' || event.key === '_') {
    setBannerZoom(bannerCrop.value.zoom - 0.1)
  } else {
    return
  }
  event.preventDefault()
}

async function onPickBanner(event) {
  const [file] = event.target.files
  if (!file) return
  const problem = await bannerProblem(file)
  revertBanner()
  bannerSaved.value = false
  if (problem) {
    bannerError.value = problem
    return
  }
  const url = URL.createObjectURL(file)
  const image = new Image()
  image.src = url
  await image.decode()
  bannerSize.value = { width: image.naturalWidth, height: image.naturalHeight }
  bannerCrop.value = { ...DEFAULT_CROP }
  newBanner.value = file
  bannerPreviewUrl.value = url
}

function revertBanner() {
  URL.revokeObjectURL(bannerPreviewUrl.value)
  bannerPreviewUrl.value = ''
  newBanner.value = null
  bannerSize.value = null
  bannerDrag.value = null
  bannerError.value = ''
  if (bannerInput.value) bannerInput.value.value = ''
}

const savedBanner = computed(() => {
  const url = userStore.userData?.bannerUrl
  return url ? resolveCoverURL(url) : ''
})

// The server crops, so we send the original file plus the visible area in pixels of the original image
async function uploadBanner() {
  if (!newBanner.value || bannerUploading.value) return
  bannerError.value = ''
  bannerUploading.value = true
  try {
    const { width, height } = bannerSize.value
    const rect = bannerCropRect(bannerCrop.value, width, height)
    // Rounding can push the area one pixel past the image edge, which the server rejects
    rect.x = Math.min(rect.x, width - rect.width)
    rect.y = Math.min(rect.y, height - rect.height)

    const query = new URLSearchParams({
      cropX: rect.x,
      cropY: rect.y,
      cropWidth: rect.width,
      cropHeight: rect.height,
    })
    const form = new FormData()
    form.append('file', newBanner.value)
    const { bannerUrl } = await request('PUT', `${API_BASE_URL}/api/users/me/banner?${query}`, form)
    userStore.updateUser({ bannerUrl })
    revertBanner()
    bannerSaved.value = true
  } catch (err) {
    if (await handleUnauthorized(err)) return
    bannerError.value = BANNER_ERRORS[err.code] ?? "We couldn't upload your banner. Check that the server is running, then try again."
  } finally {
    bannerUploading.value = false
  }
}

const details = reactive({ displayName: '', country: '', website: '', about: '' })
const detailsSaving = ref(false)
const detailsSaved = ref(false)
const detailsError = ref('')

// The server sends null for anything the user never filled in; the form wants empty strings
function showDetails(saved) {
  details.displayName = saved?.displayName ?? ''
  details.country = saved?.country ?? ''
  details.website = saved?.website ?? ''
  details.about = saved?.about ?? ''
}

// The settings endpoint has the saved details, banner and email
async function loadSettings() {
  try {
    const settings = await fetchSettings()
    showDetails(settings.profileDetails)
    currentEmail.value = settings.email
    userStore.updateUser({ bannerUrl: settings.bannerUrl })
  } catch (err) {
    if (await handleUnauthorized(err)) return
    detailsError.value = "We couldn't load your settings. Check that the server is running, then reload."
    accountError.value = "We couldn't load your email address. Check that the server is running, then reload."
  }
}

const nameInvalid = computed(() => {
  const length = details.displayName.trim().length
  return length > 0 && (length < NAME_MIN || length > NAME_MAX)
})

async function saveDetails() {
  if (detailsSaving.value || nameInvalid.value || websiteInvalid.value) return
  detailsError.value = ''
  detailsSaved.value = false
  detailsSaving.value = true
  try {
    // Empty values are sent as null, which clears them on the server
    const body = {
      displayName: details.displayName.trim() || null,
      country: details.country || null,
      website: details.website.trim() || null,
      about: details.about.trim() || null,
    }
    const saved = await request('PUT', `${API_BASE_URL}/api/users/me/profile-details`, body)
    showDetails(saved)
    detailsSaved.value = true
  } catch (err) {
    if (await handleUnauthorized(err)) return
    detailsError.value = err.code === 'INVALID_FIELD' ? err.message : "We couldn't save your details. Check that the server is running, then try again."
  } finally {
    detailsSaving.value = false
  }
}

const websiteInvalid = computed(() => {
  const value = details.website.trim()
  if (!value) return false
  try {
    const url = new URL(value)
    return url.protocol !== 'http:' && url.protocol !== 'https:'
  } catch {
    return true
  }
})

const countryName = computed(() => COUNTRIES.find((c) => c.code === details.country)?.name ?? '')
const websiteUrl = computed(() => (websiteInvalid.value ? '' : details.website.trim()))

const warnBeforeLeaving = computed({
  get: () => !skipExternalWarning.value,
  set: (warn) => setSkipExternalWarning(!warn),
})

function availabilityField({ isValid, isCurrent, isOwn = () => false, check }) {
  const value = ref('')
  const status = ref('idle')
  let timer = null

  function onInput() {
    clearTimeout(timer)
    const candidate = value.value
    if (!candidate) return (status.value = 'idle')
    if (!isValid(candidate)) return (status.value = 'invalid')
    if (isCurrent(candidate)) return (status.value = 'current')
    if (isOwn(candidate)) return (status.value = 'available')

    status.value = 'checking'
    timer = setTimeout(async () => {
      try {
        const { exists } = await check(candidate)
        if (candidate === value.value) status.value = exists ? 'taken' : 'available'
      } catch {
        if (candidate === value.value) status.value = 'idle' // the save still checks
      }
    }, 300)
  }

  function reset() {
    clearTimeout(timer)
    value.value = ''
    status.value = 'idle'
  }

  return { value, status, onInput, reset }
}

const currentUsername = username

const {
  value: newUsername,
  status: usernameStatus,
  onInput: checkNewUsername,
  reset: resetUsername,
} = availabilityField({
  isValid: (name) => USERNAME_PATTERN.test(name),
  isCurrent: (name) => name === currentUsername.value,
  isOwn: (name) => name.toLowerCase() === currentUsername.value.toLowerCase(),
  check: checkUsername,
})
const usernameSaving = ref(false)
const usernameSaved = ref('')
const usernameError = ref('')

const usernameStatusText = computed(() => ({
  idle: '3 to 16 characters: letters, numbers and underscores.',
  checking: 'Checking…',
  available: `${newUsername.value} is available.`,
  taken: `${newUsername.value} is taken. Try another one.`,
  invalid: 'Use 3 to 16 letters, numbers or underscores.',
  current: "That's already your username.",
})[usernameStatus.value])

const canSaveUsername = computed(
  () => !usernameSaving.value && !!newUsername.value && !['taken', 'invalid', 'current'].includes(usernameStatus.value),
)

function onUsernameInput() {
  usernameError.value = ''
  usernameSaved.value = ''
  checkNewUsername()
}

async function saveUsername() {
  if (!canSaveUsername.value) return
  usernameError.value = ''
  usernameSaving.value = true
  try {
    const user = await changeUsername(newUsername.value)
    userStore.updateUser(user)
    router.replace({ name: 'Settings', params: { username: user.username, tab: 'account' } })
    resetUsername()
    usernameSaved.value = user.username
  } catch (err) {
    if (await handleUnauthorized(err)) return
    if (err.code === 'USERNAME_TAKEN') usernameStatus.value = 'taken'
    else if (err.code === 'INVALID_USERNAME') usernameStatus.value = 'invalid'
    else usernameError.value = "We couldn't change your username. Check that the server is running, then try again."
  } finally {
    usernameSaving.value = false
  }
}

/* Email and password changes are two steps: send a code by email, then type it in */

const CODE_PATTERN = /^\d{6}$/
const WRONG_CODE = "That code is wrong or has expired. Check your email, or send a new code."

function codeFlowError(err, what) {
  if (err.status === 429) return 'Wait a minute before asking for another code.'
  return `We couldn't change your ${what}. Check that the server is running, then try again.`
}

/* Email: the login response doesn't include it, so it loads with the settings */

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+$/
const currentEmail = ref('')
const accountError = ref('')

// No "available / taken" check: it would tell anyone logged in which emails have accounts
const newEmail = ref('')
const emailStatus = computed(() => {
  const email = newEmail.value
  if (!email) return 'idle'
  if (email.length < EMAIL_MIN || email.length > EMAIL_MAX || !EMAIL_PATTERN.test(email)) return 'invalid'
  if (email.toLowerCase() === currentEmail.value.toLowerCase()) return 'current'
  return 'idle'
})
const emailSaving = ref(false)
const emailSaved = ref('')
const emailError = ref('')
const emailPassword = ref('')
const emailPasswordError = ref('')
const emailCodeSent = ref(false)
const emailCode = ref('')
const emailCodeError = ref('')
const emailCodeInput = ref(null)

const emailStatusText = computed(() => ({
  idle: 'The address you log in with.',
  invalid: 'Enter a full email address, like name@example.com.',
  current: "That's already your email address.",
})[emailStatus.value])

const canSaveEmail = computed(
  () =>
    !emailSaving.value &&
    !!newEmail.value &&
    !!emailPassword.value &&
    emailStatus.value === 'idle',
)
const canConfirmEmail = computed(() => !emailSaving.value && CODE_PATTERN.test(emailCode.value))

function onEmailInput() {
  emailError.value = ''
  emailSaved.value = ''
}

// Step 1: check the password and email a code to the new address. Also used by "Send a new code"
async function sendEmailCode() {
  if (!canSaveEmail.value) return
  emailError.value = ''
  emailSaved.value = ''
  emailSaving.value = true
  try {
    await requestEmailChange(newEmail.value, emailPassword.value)
    emailCode.value = ''
    emailCodeError.value = ''
    emailCodeSent.value = true
    await nextTick()
    emailCodeInput.value?.focus()
  } catch (err) {
    if (await handleUnauthorized(err)) return
    if (err.code === 'INVALID_FIELD') emailError.value = 'Enter a full email address, like name@example.com.'
    else if (err.code === 'WRONG_PASSWORD') emailPasswordError.value = "That isn't your current password. Try again."
    else emailError.value = codeFlowError(err, 'email address')
  } finally {
    emailSaving.value = false
  }
}

// Step 2: spend the code
async function confirmEmail() {
  if (!canConfirmEmail.value) return
  emailError.value = ''
  emailSaving.value = true
  try {
    await confirmEmailChange(emailCode.value)
    const email = newEmail.value
    currentEmail.value = email
    cancelEmailChange()
    emailSaved.value = email
  } catch (err) {
    if (await handleUnauthorized(err)) return
    if (err.code === 'INVALID_OTP') emailCodeError.value = WRONG_CODE
    else if (err.code === 'EMAIL_TAKEN') {
      // Someone registered the address after the code went out; the code is spent, so start over
      emailCodeSent.value = false
      emailError.value = 'That email now has an account. Try another one.'
    } else emailError.value = codeFlowError(err, 'email address')
  } finally {
    emailSaving.value = false
  }
}

function cancelEmailChange() {
  newEmail.value = ''
  emailPassword.value = ''
  emailPasswordError.value = ''
  emailCode.value = ''
  emailCodeError.value = ''
  emailCodeSent.value = false
  emailError.value = ''
}

const password = reactive({ current: '', new: '', confirm: '' })
const passwordSaving = ref(false)
const passwordSaved = ref(false)
const passwordError = ref('')
const currentPasswordError = ref('')
const passwordCodeSent = ref(false)
const passwordCode = ref('')
const passwordCodeError = ref('')
const passwordCodeInput = ref(null)

// Nothing shows until something is typed. bcrypt only reads 72 bytes, so the backend also caps the UTF-8 length
const newPasswordProblem = computed(() => {
  const value = password.new
  if (!value) return ''
  if (value.length < PASSWORD_MIN || value.length > PASSWORD_MAX || new TextEncoder().encode(value).length > 72) {
    return `Use ${PASSWORD_MIN} to ${PASSWORD_MAX} characters.`
  }
  if (value === password.current) return 'Choose a password different from your current one.'
  return ''
})
const passwordsDiffer = computed(() => !!password.confirm && password.confirm !== password.new)

const canSavePassword = computed(
  () =>
    !passwordSaving.value &&
    !!password.current &&
    !!password.new &&
    !!password.confirm &&
    !newPasswordProblem.value &&
    !passwordsDiffer.value,
)

const canConfirmPassword = computed(() => !passwordSaving.value && CODE_PATTERN.test(passwordCode.value))

watch(password, () => {
  passwordSaved.value = false
  passwordError.value = ''
})

// Step 1: check the current password and email a code to the current address. Also used by "Send a new code"
async function sendPasswordCode() {
  if (!canSavePassword.value) return
  passwordError.value = ''
  currentPasswordError.value = ''
  passwordSaving.value = true
  try {
    await requestPasswordChange(password.current)
    passwordCode.value = ''
    passwordCodeError.value = ''
    passwordCodeSent.value = true
    await nextTick()
    passwordCodeInput.value?.focus()
  } catch (err) {
    if (await handleUnauthorized(err)) return
    if (err.code === 'WRONG_PASSWORD') currentPasswordError.value = "That isn't your current password. Try again."
    else passwordError.value = codeFlowError(err, 'password')
  } finally {
    passwordSaving.value = false
  }
}

// Step 2: spend the code
async function confirmPassword() {
  if (!canConfirmPassword.value) return
  passwordError.value = ''
  passwordSaving.value = true
  try {
    await confirmPasswordChange(passwordCode.value, password.new)
    cancelPasswordChange()
    await nextTick()
    passwordSaved.value = true
  } catch (err) {
    if (await handleUnauthorized(err)) return
    if (err.code === 'INVALID_OTP') passwordCodeError.value = WRONG_CODE
    else if (err.code === 'INVALID_FIELD') passwordError.value = `That password isn't allowed. Use ${PASSWORD_MIN} to ${PASSWORD_MAX} characters.`
    else passwordError.value = codeFlowError(err, 'password')
  } finally {
    passwordSaving.value = false
  }
}

function cancelPasswordChange() {
  Object.assign(password, { current: '', new: '', confirm: '' })
  currentPasswordError.value = ''
  passwordCode.value = ''
  passwordCodeError.value = ''
  passwordCodeSent.value = false
}

const DELETE_WAIT = 6
const deleteDialog = ref(null)
const deleteElapsed = ref(0)
const deleteRun = ref(0)
const deleting = ref(false)
const deleteError = ref('')
const deleteNotice = ref('')
let deleteTimer = null

const deleteReady = computed(() => deleteElapsed.value >= DELETE_WAIT)

function openDeleteDialog() {
  deleteError.value = ''
  deleteNotice.value = ''
  deleteElapsed.value = 0
  deleteRun.value++
  deleteDialog.value.showModal()
  document.documentElement.classList.add('scroll-locked')
  deleteTimer = setInterval(() => {
    deleteElapsed.value++
    if (deleteReady.value) clearInterval(deleteTimer)
  }, 1000)
}

function onDeleteDialogClose() {
  clearInterval(deleteTimer)
  document.documentElement.classList.remove('scroll-locked')
}

onBeforeUnmount(onDeleteDialogClose)

async function confirmDelete() {
  if (!deleteReady.value || deleting.value) return
  deleteError.value = ''
  deleting.value = true
  try {
    await deleteAccount()
    deleteDialog.value.close()
    if (ACCOUNT_MOCKED) {
      deleteNotice.value = "Preview only: your account wasn't deleted."
      return
    }
    userStore.logout()
    await router.replace({ name: 'Home' })
  } catch (err) {
    if (await handleUnauthorized(err)) return
    deleteError.value = "We couldn't delete your account. Check that the server is running, then try again."
  } finally {
    deleting.value = false
  }
}
</script>

<style scoped>
.wrap { padding: 0 var(--page-gutter); }
.page-head { border-bottom: 1px solid var(--line); padding-top: var(--space-8); }
h1 { font: 700 28px/34px var(--font-sans); margin: 0 0 var(--space-4); }
.tabs { display: flex; gap: var(--space-6); }
.tab { font: 600 14px/20px var(--font-sans); color: var(--ink-muted); text-decoration: none; padding-bottom: var(--space-2); border-bottom: 3px solid transparent; }
.tab:hover { color: var(--ink); border-bottom-color: var(--primary); }
.tab.active { color: var(--link); border-bottom-color: var(--primary); }

.layout { display: grid; grid-template-columns: minmax(0, 1fr) 380px; gap: var(--space-8); padding-top: var(--space-6); padding-bottom: var(--space-8); }
.main-col { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); gap: var(--space-8); align-content: start; }
.wide { grid-column: 1 / -1; }
h2 { font: 700 20px/28px var(--font-sans); margin: 0 0 var(--space-4); }
.hint { margin: 0; font: 500 12px/16px var(--font-sans); color: var(--ink-muted); }
.error { margin: 0; font: 500 14px/20px var(--font-sans); color: var(--danger); }
.success { margin: 0; font: 500 14px/20px var(--font-sans); color: var(--success); }

.avatar-area { aspect-ratio: 3 / 1; max-height: 200px; display: flex; align-items: center; margin-bottom: var(--space-4); }
.avatar { height: 100%; aspect-ratio: 1; border-radius: var(--radius-pill); object-fit: cover; background: var(--surface-alt); }
.avatar-empty { display: grid; place-items: center; background: var(--primary); color: var(--on-primary); font: 700 56px/1 var(--font-sans); }
.revert { font-size: 14px; }
.picture-controls { display: flex; flex-direction: column; align-items: flex-start; gap: var(--space-3); max-width: 480px; }
.file-row { display: flex; align-items: center; gap: var(--space-3); flex-wrap: wrap; }
.file-row .btn { font-size: 14px; }
.file-name { font: 500 14px/20px var(--font-sans); color: var(--ink-muted); overflow-wrap: anywhere; }
.visually-hidden { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); white-space: nowrap; }
.visually-hidden:focus-visible + label { outline: 2px solid var(--focus-ring); outline-offset: 2px; }

.details { display: grid; grid-template-columns: minmax(0, 560px); gap: var(--space-4); align-items: start; }
.field { display: flex; flex-direction: column; gap: var(--space-2); }
.row-label { font: 600 14px/20px var(--font-sans); }
.input { width: 100%; font: 400 16px/24px var(--font-sans); padding: 8px 12px; color: var(--ink); background: var(--surface); border: 1px solid var(--line-strong); border-radius: var(--radius-sm); }
.input[aria-invalid='true'] { border-color: var(--danger); }
.textarea { resize: vertical; }
.details .hint, .details .error { margin-top: var(--space-1); }
.status-line { margin-top: var(--space-2) !important; }

.account-col { align-content: start; display: grid; gap: var(--space-8); grid-template-columns: minmax(0, 1fr); }
.account-col section + section { border-top: 1px solid var(--line); padding-top: var(--space-8); }
.account-form { align-items: start; display: grid; gap: var(--space-4) var(--space-6); grid-template-columns: 200px minmax(0, 560px); }

.form-label { font: 600 14px/20px var(--font-sans); padding-top: var(--space-3); text-align: right; }
.form-field { display: flex; flex-direction: column; gap: var(--space-2); grid-column: 2; }
.form-field .btn { align-self: flex-start; }
.code-actions { display: flex; flex-wrap: wrap; gap: var(--space-2); }
.readonly { background: var(--surface-alt); border-color: var(--surface-alt); color: var(--ink-muted); }
.notice { background: var(--purple-soft); border-radius: var(--radius-sm); font: 500 14px/20px var(--font-sans); margin: 0; padding: var(--space-4); }
.delete-field { gap: var(--space-4); }
.warning { align-items: flex-start; border-left: 3px solid var(--primary); display: flex; gap: var(--space-2); }
.warning .icon { color: var(--link); margin-top: 2px; }

.confirm { background: var(--surface); border: 1px solid var(--line); border-radius: var(--radius-md); color: var(--ink); font: 400 16px/24px var(--font-sans); overscroll-behavior: contain; padding: 0; width: min(440px, calc(100% - 32px)); }
.confirm::backdrop { background: rgb(0 0 0 / 0.45); }
.confirm-body { display: flex; flex-direction: column; gap: var(--space-3); padding: var(--space-6); }
.confirm h2 { margin: 0; }
.confirm-text { margin: 0; }
.confirm-actions { display: flex; gap: var(--space-3); margin-top: var(--space-2); }
.countdown { background: var(--surface-alt); border-radius: var(--radius-pill); height: 8px; overflow: hidden; }
.countdown-fill { animation: countdown 6s linear forwards; background: var(--primary); height: 100%; transform: scaleX(var(--progress)); transform-origin: left; }
@keyframes countdown { from { transform: scaleX(0); } to { transform: scaleX(1); } }
.status { align-items: center; color: var(--ink-muted); display: flex; font: 500 14px/20px var(--font-sans); gap: var(--space-1); margin: 0; min-height: 20px; }
.status.available { color: var(--success); }
.status.invalid, .status.taken { color: var(--danger); }
.icon { fill: none; flex: none; height: 16px; stroke: currentColor; stroke-linecap: round; stroke-linejoin: round; stroke-width: 2; width: 16px; }

.intro { margin: 0; color: var(--ink-muted); }
.apps { list-style: none; margin: 0; padding: 0; border-top: 1px solid var(--line); }
.app { display: grid; grid-template-columns: 40px minmax(0, 1fr) auto; gap: var(--space-4); align-items: center; padding: var(--space-4) 0; border-bottom: 1px solid var(--line); }
.app-icon { width: 40px; height: 40px; }
.spotify { fill: #1db954; }
.app-text { min-width: 0; }
.app-name { margin: 0; font: 600 16px/24px var(--font-sans); }
.app-desc { margin: 0; font: 400 14px/20px var(--font-sans); }
.app-status { margin: var(--space-1) 0 0; font: 500 12px/16px var(--font-sans); color: var(--ink-muted); }

.banner-frame { aspect-ratio: 3 / 1; border-radius: var(--radius-md); overflow: hidden; background: var(--purple-soft); display: grid; place-items: center; margin-bottom: var(--space-4); }
.banner-frame.editing { position: relative; display: block; cursor: grab; touch-action: none; user-select: none; }
.banner-frame.editing.dragging { cursor: grabbing; }
.banner-frame img { display: block; pointer-events: none; }
.adjust { display: flex; flex-direction: column; gap: var(--space-2); margin-bottom: var(--space-4); }
.zoom-row { display: flex; align-items: center; gap: var(--space-3); }
.zoom-label { font: 600 14px/20px var(--font-sans); }
.zoom { flex: 1; accent-color: var(--primary); }
.banner-saved { width: 100%; height: 100%; object-fit: cover; }
.banner-empty { margin: 0; font: 500 14px/20px var(--font-sans); color: var(--ink-muted); }

.check { display: flex; align-items: center; gap: var(--space-2); font: 600 14px/20px var(--font-sans); cursor: pointer; margin-bottom: var(--space-1); }
.check input { width: 16px; height: 16px; accent-color: var(--primary); }

.select-wrap { position: relative; }
.select { appearance: none; -webkit-appearance: none; padding-right: calc(var(--space-4) + 28px); cursor: pointer; }
.select-wrap::after { content: ''; position: absolute; top: 50%; right: calc(var(--space-4) + 8px); width: 12px; height: 12px; transform: translateY(-50%); pointer-events: none; background: var(--ink-muted); -webkit-mask: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='black' stroke-width='3' stroke-linecap='round' stroke-linejoin='round'%3E%3Cpath d='m6 9 6 6 6-6'/%3E%3C/svg%3E") center / contain no-repeat; mask: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='black' stroke-width='3' stroke-linecap='round' stroke-linejoin='round'%3E%3Cpath d='m6 9 6 6 6-6'/%3E%3C/svg%3E") center / contain no-repeat; }

.side { position: sticky; top: calc(88px + env(safe-area-inset-top, 0px)); align-self: start; display: flex; flex-direction: column; gap: var(--space-3); }
.side-title { font: 700 16px/24px var(--font-sans); margin: 0; }

@media (max-width: 1400px) {
  .main-col { grid-template-columns: minmax(0, 1fr); }
}
@media (max-width: 900px) {
  .layout { grid-template-columns: 1fr; }
  .side { position: static; }
}
@media (max-width: 720px) {
  .account-form { gap: var(--space-2); grid-template-columns: minmax(0, 1fr); }
  .form-label { padding-top: 0; text-align: left; }
  .form-field { grid-column: 1; }
  .form-field + .form-label { margin-top: var(--space-2); }
}
@media (max-width: 600px) {
  .app { grid-template-columns: 40px minmax(0, 1fr); }
  .app .btn { grid-column: 2; justify-self: start; }
}
</style>
