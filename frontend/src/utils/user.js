import router from '@/router/index.js'

export function isLoggedIn(user) { return !(user === undefined || user === null); }
