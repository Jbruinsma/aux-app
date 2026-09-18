import router from '@/router/index.js'
import { useUserStore } from '@/stores/user.js'
import { useMusicStore } from '@/stores/music.js'

export function rerouteToDashboard() {
  const musicStore = useMusicStore()
  const currentUser = useUserStore().userData?.username
  const url = { name: 'Dashboard' }
  const redirectPath = router.resolve(url).fullPath

  if (currentUser === null || currentUser === undefined){
    router.push({
      name: 'Login',
      query: { redirect: redirectPath }
    })
  } else {
    musicStore.saveLastPlayback(currentUser)
    router.push(url)
  }
}

export function rerouteToSettings(){
  const musicStore = useMusicStore()
  const currentUser = useUserStore().userData?.username
  const url = { name: 'Settings', params: { username: currentUser } }
  const redirectPath = router.resolve(url).fullPath

  if (currentUser === null || currentUser === undefined){
    router.push({
      name: 'Login',
      query: { redirect: redirectPath }
    })
  } else {
    musicStore.saveLastPlayback(currentUser)
    router.push(url)
  }
}

export function rerouteToPublicProfile() {
  const musicStore = useMusicStore()
  const currentUser = useUserStore().userData?.username
  const url = { name: 'Profile', params: { username: currentUser } }
  const redirectPath = router.resolve(url).fullPath

  if (currentUser === null || currentUser === undefined){
    router.push({
      name: 'Login',
      query: { redirect: redirectPath }
    })
  } else {
    musicStore.saveLastPlayback(currentUser)
    router.push(url)
  }
}
