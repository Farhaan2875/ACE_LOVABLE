import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { api } from '../api.js'
import Header from '../components/Header.jsx'

export default function Dashboard() {
  const navigate = useNavigate()
  const [projects, setProjects] = useState(null)
  const [name, setName] = useState('')
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(false)

  const load = () => api.projects().then(setProjects).catch((e) => setError(e))
  useEffect(() => { load() }, [])

  async function create(e) {
    e.preventDefault()
    setError(null)
    setBusy(true)
    try {
      const project = await api.createProject(name.trim())
      navigate(`/projects/${project.id}`)
    } catch (err) {
      setError(err)
    } finally {
      setBusy(false)
    }
  }

  async function remove(project) {
    if (!window.confirm(`Delete "${project.name}"? This cannot be undone.`)) return
    try {
      await api.deleteProject(project.id)
      load()
    } catch (err) {
      setError(err)
    }
  }

  return (
    <>
      <Header />
      <main className="container">
        <h2>Your projects</h2>

        <form className="row" onSubmit={create}>
          <input placeholder="Name your next app, e.g. Habit Tracker" value={name} onChange={(e) => setName(e.target.value)} maxLength={60} required />
          <button className="btn primary" disabled={busy}>{busy ? 'Creating...' : 'New project'}</button>
        </form>
        {error && (
          <div className="alert">
            {error.message} {error.status === 402 && <Link to="/billing">See plans</Link>}
          </div>
        )}

        {projects === null && <p className="muted">Loading...</p>}
        {projects?.length === 0 && <p className="muted">No projects yet - create your first one above.</p>}
        <div className="grid">
          {projects?.map((p) => (
            <div key={p.id} className="card project-card">
              <Link to={`/projects/${p.id}`}><h3>{p.name}</h3></Link>
              <small className="muted">Updated {new Date(p.updatedAt).toLocaleString()}</small>
              <div className="row">
                <Link className="btn ghost" to={`/projects/${p.id}`}>Open</Link>
                <button className="btn ghost danger" onClick={() => remove(p)}>Delete</button>
              </div>
            </div>
          ))}
        </div>
      </main>
    </>
  )
}
