import { useEffect, useState } from 'react'
import { api } from '../api.js'

/** File list on the left, code on the right. While the AI writes a file its live content is shown. */
export default function CodeViewer({ projectId, files, live, writing, selected, onSelect }) {
  const [content, setContent] = useState('')
  const [loading, setLoading] = useState(false)

  // load the saved content whenever the selected file (or its saved version) changes
  const selectedVersion = files.find((f) => f.path === selected)?.modifiedAt
  useEffect(() => {
    if (!selected) return
    setLoading(true)
    api.file(projectId, selected).then((r) => setContent(r.content)).catch(() => setContent('(could not load file)')).finally(() => setLoading(false))
  }, [projectId, selected, selectedVersion])

  const isWriting = writing.includes(selected)
  const shown = isWriting ? live[selected] || '' : content

  return (
    <div className="code">
      <ul className="tree">
        {files.map((f) => (
          <li key={f.path}>
            <button className={f.path === selected ? 'active' : ''} onClick={() => onSelect(f.path)} title={f.path}>
              <span className="indent" style={{ width: (f.path.split('/').length - 1) * 12 }} />
              {f.path.split('/').pop()}
              {writing.includes(f.path) && <em> ✎</em>}
            </button>
          </li>
        ))}
        {/* files that are being created right now and are not saved yet */}
        {writing.filter((p) => !files.some((f) => f.path === p)).map((p) => (
          <li key={p}><button className={p === selected ? 'active' : ''} onClick={() => onSelect(p)}>{p} <em>✎</em></button></li>
        ))}
      </ul>
      <div className="editor">
        <div className="editor-title">{selected || 'Select a file'}{loading && <span className="muted"> loading...</span>}</div>
        <pre><code>{shown}</code></pre>
      </div>
    </div>
  )
}
