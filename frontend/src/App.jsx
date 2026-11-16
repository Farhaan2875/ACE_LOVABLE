import { Navigate, Route, Routes } from 'react-router-dom'
import { useAuth } from './auth.jsx'
import Login from './pages/Login.jsx'
import Dashboard from './pages/Dashboard.jsx'
import Workspace from './pages/Workspace.jsx'
import Billing from './pages/Billing.jsx'

// Pages that need a logged-in user are wrapped in this guard.
function Protected({ children }) {
  const { user, loading } = useAuth()
  if (loading) return <div className="center muted">Loading...</div>
  return user ? children : <Navigate to="/login" replace />
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route path="/" element={<Protected><Dashboard /></Protected>} />
      <Route path="/projects/:id" element={<Protected><Workspace /></Protected>} />
      <Route path="/billing" element={<Protected><Billing /></Protected>} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
