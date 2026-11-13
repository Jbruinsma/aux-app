<template>
  <dialog ref="dialogEl" class="follows" aria-labelledby="follows-title" @close="onClose" @click.self="close">
    <div class="card">
      <div class="head">
        <h2 id="follows-title">@{{ username }}</h2>
        <button type="button" class="close" aria-label="Close" @click="close">
          <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M18 6 6 18" /><path d="m6 6 12 12" /></svg>
        </button>
      </div>

      <div class="tabs" role="tablist" aria-label="Follow lists">
        <button
          v-for="item in LISTS"
          :key="item.id"
          type="button"
          role="tab"
          class="tab"
          :class="{ active: which === item.id }"
          :aria-selected="which === item.id ? 'true' : 'false'"
          @click="show(item.id)"
        >{{ item.label }} <span class="tab-count">{{ counts[item.id] }}</span></button>
      </div>

      <div class="body" role="tabpanel">
        <p v-if="FOLLOWS_MOCKED" class="preview">Preview: these are sample people until Aux can list followers.</p>
        <EmptyState v-if="list.hidden" icon="users" :title="hiddenTitle" text="They chose who can see the people they follow." />
        <EmptyState v-else-if="list.error" tone="error" icon="users" :title="list.error" text="Check that the server is running, then try again.">
          <button type="button" class="btn secondary" @click="load()">Try again</button>
        </EmptyState>
        <EmptyState v-else-if="list.loading && !list.users.length" tone="loading" icon="users" title="Loading…" />
        <EmptyState v-else-if="!list.users.length" icon="users" :title="emptyTitle" />
        <template v-else>
          <ul class="people">
            <li v-for="user in list.users" :key="user.userId" class="person">
              <router-link class="who" :to="{ name: 'Profile', params: { username: user.username } }" @click="close">
                <img v-if="user.pfpUrl" class="av" :src="resolveCoverURL(user.pfpUrl)" alt="" loading="lazy" />
                <span v-else class="av av-empty" aria-hidden="true">{{ user.username.charAt(0).toUpperCase() }}</span>
                <span class="name">{{ user.username }}</span>
              </router-link>
              <span v-if="isFriend(user)" class="friend">Friends</span>
              <button
                v-if="canFollow(user)"
                type="button"
                class="btn small"
                :class="user.isFollowing ? 'secondary' : 'primary'"
                :aria-pressed="user.isFollowing ? 'true' : 'false'"
                :aria-label="`${user.isFollowing ? 'Unfollow' : 'Follow'} ${user.username}`"
                :disabled="busy.has(user.userId)"
                @click="toggleFollow(user)"
              >{{ user.isFollowing ? 'Following' : 'Follow' }}</button>
            </li>
          </ul>
          <button v-if="list.nextCursor" type="button" class="btn text more" :disabled="list.loading" @click="load(list.nextCursor)">
            {{ list.loading ? 'Loading…' : 'Show more' }}
          </button>
        </template>
      </div>
    </div>
  </dialog>
</template>

<script setup>
import { computed, onBeforeUnmount, reactive, ref } from 'vue'
import EmptyState from '@/components/EmptyState.vue'
import { resolveCoverURL } from '@/utils/display.js'
import { FOLLOWS_MOCKED, fetchFollowList, isFriend, setFollowing } from '@/utils/follows.js'
import { useUserStore } from '@/stores/user.js'
import { useNotificationStore } from '@/stores/notification.js'

// A profile's followers and following, opened from its counts with `open('followers' | 'following')`. Following or
// unfollowing someone here emits `changed`, so the profile can refresh its counts
const props = defineProps({
  username: { type: String, required: true },
  // { followers, following }: the profile's counts, as the server formats them ("1.2K")
  counts: { type: Object, required: true },
})
const emit = defineEmits(['changed'])

const LISTS = [
  { id: 'followers', label: 'Followers' },
  { id: 'following', label: 'Following' },
]

const userStore = useUserStore()
const notification = useNotificationStore()
const dialogEl = ref(null)
const which = ref('followers')
const list = reactive({ users: [], nextCursor: null, loading: false, error: '', hidden: false })
const busy = ref(new Set())
let request = 0
let changed = false

const hiddenTitle = computed(() => (which.value === 'followers'
  ? `@${props.username} keeps their followers private`
  : `@${props.username} keeps who they follow private`))
const emptyTitle = computed(() => (which.value === 'followers'
  ? `No one follows @${props.username} yet`
  : `@${props.username} doesn't follow anyone yet`))

function open(target = 'followers') {
  changed = false
  dialogEl.value.showModal()
  document.documentElement.classList.add('scroll-locked')
  show(target, true)
}

function close() {
  dialogEl.value?.close()
}

function onClose() {
  document.documentElement.classList.remove('scroll-locked')
  request++
  if (changed) emit('changed')
}

onBeforeUnmount(() => document.documentElement.classList.remove('scroll-locked'))

function show(target, force = false) {
  if (which.value === target && !force) return
  which.value = target
  Object.assign(list, { users: [], nextCursor: null, error: '', hidden: false })
  load()
}

async function load(cursor = null) {
  const id = ++request
  const target = which.value
  list.loading = true
  list.error = ''
  try {
    const page = await fetchFollowList(props.username, target, cursor)
    if (id !== request) return
    list.users = cursor ? [...list.users, ...page.users] : page.users
    list.nextCursor = page.nextCursor ?? null
  } catch (err) {
    if (err.sessionEnded || id !== request) return
    if (err.code === 'FOLLOWS_HIDDEN') list.hidden = true
    else list.error = `We couldn't load ${target === 'followers' ? 'followers' : 'who they follow'}.`
  } finally {
    if (id === request) list.loading = false
  }
}

function canFollow(user) {
  return userStore.loggedIn && user.username !== userStore.userData?.username
}

async function toggleFollow(user) {
  const follow = !user.isFollowing
  busy.value = new Set([...busy.value, user.userId])
  try {
    await setFollowing(user.username, follow)
    user.isFollowing = follow
    changed = true
  } catch (err) {
    if (err.sessionEnded) return
    notification.error(`We couldn't ${follow ? 'follow' : 'unfollow'} ${user.username}. Check that the server is running, then try again.`)
  } finally {
    const next = new Set(busy.value)
    next.delete(user.userId)
    busy.value = next
  }
}

defineExpose({ open })
</script>

<style scoped>
.follows { background: none; border: 0; color: var(--ink); font: 400 16px/24px var(--font-sans); max-width: none; overscroll-behavior: contain; padding: 0; width: min(480px, calc(100% - 32px)); }
.follows::backdrop { background: rgb(0 0 0 / 0.45); }
.card { background: var(--surface); border: 1px solid var(--line); border-radius: var(--radius-md); display: flex; flex-direction: column; max-height: min(640px, 85vh); overflow: hidden; }
.head { align-items: center; display: flex; justify-content: space-between; padding: var(--space-4) var(--space-4) 0 var(--space-6); }
h2 { font: 700 20px/28px var(--font-sans); margin: 0; overflow-wrap: anywhere; }
.close { align-items: center; background: none; border: 0; border-radius: var(--radius-pill); color: var(--ink-muted); cursor: pointer; display: flex; height: 36px; justify-content: center; padding: 0; width: 36px; }
.close:hover { background: var(--surface-alt); color: var(--ink); }
.close svg { fill: none; height: 20px; stroke: currentColor; stroke-linecap: round; stroke-width: 2; width: 20px; }
.tabs { border-bottom: 1px solid var(--line); display: flex; gap: var(--space-6); padding: var(--space-3) var(--space-6) 0; }
.tab { background: none; border: 0; border-bottom: 3px solid transparent; color: var(--ink-muted); cursor: pointer; font: 600 14px/20px var(--font-sans); padding: 0 0 var(--space-2); }
.tab:hover { border-bottom-color: var(--primary); color: var(--ink); }
.tab.active { border-bottom-color: var(--primary); color: var(--link); }
.tab-count { font-variant-numeric: tabular-nums; }
.body { display: flex; flex-direction: column; gap: var(--space-3); overflow-y: auto; padding: var(--space-4) var(--space-6) var(--space-6); }
.preview { color: var(--ink-muted); font: 500 12px/16px var(--font-sans); margin: 0; }
.people { list-style: none; margin: 0; padding: 0; }
.person { align-items: center; border-bottom: 1px solid var(--line); display: flex; gap: var(--space-3); padding: var(--space-2) 0; }
.who { align-items: center; color: var(--ink); display: flex; flex: 1; gap: var(--space-3); min-width: 0; text-decoration: none; }
.who:hover .name { color: var(--link); text-decoration: underline; }
.av { border-radius: var(--radius-pill); flex: none; height: 40px; object-fit: cover; width: 40px; }
.av-empty { background: var(--primary); color: var(--on-primary); display: grid; font: 700 16px/1 var(--font-sans); place-items: center; }
.name { font: 600 16px/24px var(--font-sans); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.friend { background: var(--purple-soft); border-radius: var(--radius-pill); color: var(--ink); flex: none; font: 600 12px/16px var(--font-sans); padding: 2px var(--space-2); }
.btn.small { flex: none; font-size: 14px; padding: 6px 12px; }
.more { align-self: center; font-size: 14px; }
</style>
