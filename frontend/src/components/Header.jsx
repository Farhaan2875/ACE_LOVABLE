import { Link } from 'react-router-dom'
import { useAuth } from '../auth.jsx'
import UsageBar from './UsageBar.jsx'

export default function Header({ children }) {
  const { user, logout } = useAuth()
  return (
    <header className="topbar">
      <Link to="/" className="logo small">Ace<span>.</span></Link>
      <div className="topbar-middle">{children}</div>
      <div className="topbar-right">
        <UsageBar />
        <Link to="/billing" className="btn ghost">Plans</Link>
        <span className="muted hide-sm">{user?.name}</span>
        <button className="btn ghost" onClick={logout}>Log out</button>
      </div>
    </header>
  )
}
