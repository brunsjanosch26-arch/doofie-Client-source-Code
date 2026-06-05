import { writable } from 'svelte/store'

export interface Account {
  id: string
  username: string
  uuid: string
  account_type: string
  is_active: boolean
  access_token?: string
  refresh_token?: string
  skin_url?: string
  expires_at?: number
}

export type Page = 'home' | 'mods' | 'shaders' | 'servers' | 'capes' | 'account' | 'settings' | 'profiles' | 'resourcepacks'

export const activeAccount = writable<Account | null>(null)
export const gameDir = writable<string>('')
export const currentPage = writable<Page>('home')
export const onlineCount = writable<number>(0)

const STORED_ACCENT = typeof localStorage !== 'undefined'
  ? (localStorage.getItem('accentColor') ?? '#3d7ff0')
  : '#3d7ff0'

const STORED_BG = typeof localStorage !== 'undefined'
  ? (localStorage.getItem('backgroundEffect') ?? 'retrogrid')
  : 'retrogrid'

export const accentColor = writable<string>(STORED_ACCENT)
export const backgroundEffect = writable<string>(STORED_BG)

accentColor.subscribe(v => {
  if (typeof localStorage !== 'undefined') localStorage.setItem('accentColor', v)
  if (typeof document !== 'undefined') document.documentElement.style.setProperty('--accent', v)
})

backgroundEffect.subscribe(v => {
  if (typeof localStorage !== 'undefined') localStorage.setItem('backgroundEffect', v)
})
