import { createContext, useContext } from 'react'

export interface SessionUser { username: string; organizationName: string; canWriteCatalog: boolean; canWriteInventory: boolean }
export interface AuthState {
  user: SessionUser | null
  loading: boolean
  error: string | null
  refresh: () => Promise<void>
  signIn: (username: string, password: string) => Promise<void>
  signOut: () => Promise<void>
}

export const AuthContext = createContext<AuthState | null>(null)
export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useAuth requires AuthProvider')
  return context
}
