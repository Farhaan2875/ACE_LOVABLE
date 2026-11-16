import { useState } from 'react'
import { Navigate, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth.jsx'

export default function Login() {
  const { user, login, signup } = useAuth()
  const navigate = useNavigate()
  const [mode, setMode] = useState('login') // 'login' | 'signup'
  const [form, setForm] = useState({ username: '', name: '', password: '' })
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  if (user) return <Navigate to="/" replace />

  const set = (field) => (e) => setForm({ ...form, [field]: e.target.value })

  async function submit(e) {
    e.preventDefault()
    setError('')
    setBusy(true)
    try {
      if (mode === 'login') await login(form.username, form.password)
      else await signup(form.username, form.name, form.password)
      navigate('/')
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="center">
      <form className="card auth-card" onSubmit={submit}>
        <h1 className="logo">Ace<span>.</span></h1>
        <p className="muted">Describe an app. Watch it get built.</p>

        <label>Email</label>
        <input type="email" value={form.username} onChange={set('username')} required autoFocus />
        {mode === 'signup' && (
          <>
            <label>Name</label>
            <input value={form.name} onChange={set('name')} required maxLength={30} />
          </>
        )}
        <label>Password</label>
        <input type="password" value={form.password} onChange={set('password')} required minLength={mode === 'signup' ? 8 : 1} />
        {mode === 'signup' && <small className="muted">At least 8 characters.</small>}

        {error && <div className="alert">{error}</div>}
        <button className="btn primary" disabled={busy}>{busy ? 'Please wait...' : mode === 'login' ? 'Log in' : 'Create account'}</button>
        <button type="button" className="btn link" onClick={() => { setMode(mode === 'login' ? 'signup' : 'login'); setError('') }}>
          {mode === 'login' ? 'New here? Create an account' : 'Already have an account? Log in'}
        </button>
      </form>
    </div>
  )
}
