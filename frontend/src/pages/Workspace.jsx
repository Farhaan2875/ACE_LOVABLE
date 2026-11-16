import { useCallback, useEffect, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { api } from '../api.js'
import { useAuth } from '../auth.jsx'
import Header from '../components/Header.jsx'
import ChatPanel from '../components/ChatPanel.jsx'
import CodeViewer from '../components/CodeViewer.jsx'
import PreviewPane from '../components/PreviewPane.jsx'
import MembersDialog from '../components/MembersDialog.jsx'

export default function Workspace() {
  const { id } = useParams()
  const projectId = Number(id)
  const navigate = useNavigate()
  const { user } = useAuth()

  const [project, setProject] = useState(null)
  const [role, setRole] = useState('VIEWER')
  const [files, setFiles] = useState([])
  const [selected, setSelected] = useState(null)
  const [live, setLive] = useState({})          // path -> content that is being written right now
  const [writing, setWriting] = useState([])    // paths being written right now
  const [preview, setPreview] = useState(null)
  const [tab, setTab] = useState('preview')
  const [showMembers, setShowMembers] = useState(false)
  const [watchPreview, setWatchPreview] = useState(false)
  const watchTimer = useRef(null)

  const canEdit = role === 'OWNER' || role === 'EDITOR'

  const refreshFiles = useCallback(async (selectFirst = false) => {
    const list = await api.files(projectId)
    setFiles(list)
    if (selectFirst) setSelected((cur) => cur || list.find((f) => f.path === 'src/App.jsx')?.path || list[0]?.path || null)
  }, [projectId])

  // ---- initial load
  useEffect(() => {
    api.project(projectId).then(setProject).catch(() => navigate('/'))
    api.members(projectId).then((list) => setRole(list.find((m) => m.userId === user.id)?.projectRole || 'VIEWER'))
    api.preview(projectId).then(setPreview).catch(() => {})
    refreshFiles(true)
  }, [projectId])

  // ---- live file events from the chat stream
  const onFileStart = (path) => { setWriting((w) => [...w, path]); setLive((l) => ({ ...l, [path]: '' })); setSelected(path); setTab('code') }
  const onFileDelta = (path, delta) => setLive((l) => ({ ...l, [path]: (l[path] || '') + delta }))
  const onFileEnd = (path) => { setWriting((w) => w.filter((p) => p !== path)); refreshFiles() }

  // After an answer the backend (asynchronously) starts / refreshes the preview: keep polling for a while
  const onDone = () => {
    refreshFiles()
    setWatchPreview(true)
    setTab('preview')
    clearTimeout(watchTimer.current)
    watchTimer.current = setTimeout(() => setWatchPreview(false), 20000)
  }

  // ---- preview polling: while it is starting (or just after an AI answer)
  useEffect(() => {
    if (!watchPreview && preview?.status !== 'CREATING') return
    const timer = setInterval(() => api.preview(projectId).then(setPreview).catch(() => {}), 2500)
    return () => clearInterval(timer)
  }, [watchPreview, preview?.status, projectId])

  useEffect(() => () => clearTimeout(watchTimer.current), [])

  async function rename() {
    const name = window.prompt('Project name', project.name)
    if (name && name.trim()) setProject(await api.renameProject(projectId, name.trim()))
  }

  return (
    <div className="workspace-page">
      <Header>
        {project && (
          <>
            <strong className="project-title" onClick={canEdit ? rename : undefined} title={canEdit ? 'Click to rename' : ''}>{project.name}</strong>
            <span className="badge">{role}</span>
            <button className="btn ghost" onClick={() => setShowMembers(true)}>Members</button>
          </>
        )}
      </Header>

      <div className="workspace">
        <ChatPanel projectId={projectId} canChat={canEdit} onFileStart={onFileStart} onFileDelta={onFileDelta} onFileEnd={onFileEnd} onDone={onDone} />

        <section className="right">
          <div className="tabs">
            <button className={tab === 'preview' ? 'active' : ''} onClick={() => setTab('preview')}>Preview</button>
            <button className={tab === 'code' ? 'active' : ''} onClick={() => setTab('code')}>Code ({files.length})</button>
          </div>
          {tab === 'preview'
            ? <PreviewPane projectId={projectId} preview={preview} canEdit={canEdit} onChange={setPreview} />
            : <CodeViewer projectId={projectId} files={files} live={live} writing={writing} selected={selected} onSelect={setSelected} />}
        </section>
      </div>

      {showMembers && <MembersDialog projectId={projectId} isOwner={role === 'OWNER'} currentUserId={user.id} onClose={() => setShowMembers(false)} />}
    </div>
  )
}
