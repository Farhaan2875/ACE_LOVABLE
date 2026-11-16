import { useEffect, useState } from 'react'
import { api } from '../api.js'

/** Owner can invite people, change their role (EDITOR / VIEWER) and remove them. */
export default function MembersDialog({ projectId, isOwner, currentUserId, onClose }) {
  const [members, setMembers] = useState([])
  const [email, setEmail] = useState('')
  const [role, setRole] = useState('EDITOR')
  const [error, setError] = useState('')

  const load = () => api.members(projectId).then(setMembers).catch((e) => setError(e.message))
  useEffect(() => { load() }, [projectId])

  const attempt = async (fn) => {
    setError('')
    try { await fn(); await load() } catch (e) { setError(e.message) }
  }

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="card modal" onClick={(e) => e.stopPropagation()}>
        <h3>Project members</h3>
        <ul className="members">
          {members.map((m) => (
            <li key={m.userId}>
              <div><strong>{m.name}</strong><br /><small className="muted">{m.username}</small></div>
              {m.projectRole === 'OWNER' || !isOwner
                ? <span className="badge">{m.projectRole}</span>
                : <select value={m.projectRole} onChange={(e) => attempt(() => api.setRole(projectId, m.userId, e.target.value))}>
                    <option>EDITOR</option><option>VIEWER</option>
                  </select>}
              {m.projectRole !== 'OWNER' && (isOwner || m.userId === currentUserId) &&
                <button className="btn ghost danger" onClick={() => attempt(() => api.removeMember(projectId, m.userId))}>{m.userId === currentUserId ? 'Leave' : 'Remove'}</button>}
            </li>
          ))}
        </ul>

        {isOwner && (
          <form className="row" onSubmit={(e) => { e.preventDefault(); attempt(async () => { await api.invite(projectId, email, role); setEmail('') }) }}>
            <input type="email" placeholder="teammate@email.com" value={email} onChange={(e) => setEmail(e.target.value)} required />
            <select value={role} onChange={(e) => setRole(e.target.value)}><option>EDITOR</option><option>VIEWER</option></select>
            <button className="btn primary">Invite</button>
          </form>
        )}
        <small className="muted">Editors can chat with the AI and run previews. Viewers can only look. The person must already have an Ace account.</small>
        {error && <div className="alert">{error}</div>}
        <button className="btn ghost" onClick={onClose}>Close</button>
      </div>
    </div>
  )
}
