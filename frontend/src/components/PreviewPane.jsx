import { useState } from 'react'
import { api } from '../api.js'

/** Shows the running app in an iframe and lets the user start / stop it. */
export default function PreviewPane({ projectId, preview, canEdit, onChange }) {
  const [logs, setLogs] = useState(null)
  const [error, setError] = useState(null)
  const status = preview?.status || 'NONE'

  async function run(action) {
    setError(null)
    try {
      onChange(await action(projectId))
    } catch (e) {
      setError(e.message)
    }
  }

  return (
    <div className="preview">
      <div className="preview-bar">
        <span className={`dot ${status.toLowerCase()}`} /> <strong>{status === 'NONE' ? 'Not started' : status}</strong>
        {preview?.url && <a href={preview.url} target="_blank" rel="noreferrer" className="muted">{preview.url}</a>}
        <span className="spacer" />
        {canEdit && status !== 'CREATING' && status !== 'RUNNING' && <button className="btn primary" onClick={() => run(api.startPreview)}>▶ Start preview</button>}
        {canEdit && status === 'RUNNING' && <button className="btn ghost" onClick={() => run(api.stopPreview)}>Stop</button>}
        <button className="btn ghost" onClick={() => api.previewLogs(projectId).then(setLogs)}>Logs</button>
      </div>
      {error && <div className="alert">{error}</div>}

      <div className="preview-body">
        {status === 'RUNNING' && preview.url && <iframe key={preview.startedAt} src={preview.url} title="Live preview" />}
        {status === 'CREATING' && <div className="center muted">Starting your app... the first start installs packages and can take a minute.</div>}
        {status === 'FAILED' && <div className="center"><div className="alert">{preview.message || 'Preview failed'}</div></div>}
        {(status === 'NONE' || status === 'TERMINATED') && <div className="center muted">Press "Start preview" to see your app.</div>}
      </div>

      {logs !== null && (
        <div className="logs">
          <button className="btn ghost" onClick={() => setLogs(null)}>Close logs</button>
          <pre>{logs || '(no logs yet)'}</pre>
        </div>
      )}
    </div>
  )
}
