import { createContext, useContext, useEffect, useState } from 'react'
import { api, getToken, setToken } from './api.js'

const AuthContext = createContext(null)
export const useAuth = () => useContext(AuthContext)

// Keeps the logged-in user in one place so every page can use it.
export function AuthProvider({ children }) {
  const [user, setUser] = useState(null)
  const [loading, setLoading] = useState(!!getToken())

  useEffect(() => {
    if (!getToken()) return
    api.me().then(setUser).catch(() => setToken(null)).finally(() => setLoading(false))
  }, [])

  const handleAuth = ({ token, user }) => {
    setToken(token)
    setUser(user)
  }
  const login = async (username, password) => handleAuth(await api.login(username, password))
  const signup = async (username, name, password) => handleAuth(await api.signup(username, name, password))
  const logout = () => {
    setToken(null)
    setUser(null)
  }

  return <AuthContext.Provider value={{ user, loading, login, signup, logout }}>{children}</AuthContext.Provider>
}
