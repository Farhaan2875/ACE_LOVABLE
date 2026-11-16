import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { api, streamChat } from '../api.js'

const SUGGESTIONS = ['Build a landing page for a coffee shop', 'Make a todo list with filters', 'Create a weather dashboard with mock data']

/**
 * Left side of the workspace: the conversation with the AI.
 * File events are passed up to the Workspace so the code tab can show files being written live.
 */
export default function ChatPanel({ projectId, canChat, onFileStart, onFileDelta, onFileEnd, onDone }) {
  const [messages, setMessages] = useState([])
  const [input, setInput] = useState('')
  const [busy, setBusy] = useState(false)
  const [status, setStatus] = useState('')
  const [error, setError] = useState(null)
  const abortRef = useRef(null)
  const bottomRef = useRef(null)

  useEffect(() => {
    api.messages(projectId).then((list) => setMessages(list.map((m) => ({ role: m.role, text: m.content, files: m.files })))).catch(() => {})
    return () => abortRef.current?.abort()
  }, [projectId])

  useEffect(() => { bottomRef.current?.scrollIntoView({ behavior: 'smooth' }) }, [messages, status])

  // changes the LAST message (the assistant bubble that is currently being streamed)
  const patchLast = (change) => setMessages((list) => list.map((m, i) => (i === list.length - 1 ? change(m) : m)))

  async function send(text) {
    if (!text.trim() || busy) return
    setError(null)
    setInput('')
    setBusy(true)
    setStatus('Thinking...')
    setMessages((list) => [...list, { role: 'USER', text }, { role: 'ASSISTANT', text: '', files: [], writing: [] }])

    const controller = new AbortController()
    abortRef.current = controller
    try {
      await streamChat(projectId, text, (event, data) => {
        switch (event) {
          case 'text': patchLast((m) => ({ ...m, text: m.text + data.delta })); setStatus('Writing...'); break
          case 'tool': setStatus(`Looking at the project (${data.name})...`); break
          case 'file_start': patchLast((m) => ({ ...m, writing: [...m.writing, data.path] })); setStatus(`Creating ${data.path}`); onFileStart(data.path); break
          case 'file_delta': onFileDelta(data.path, data.delta); break
          case 'file_end':
            patchLast((m) => ({ ...m, writing: m.writing.filter((p) => p !== data.path), files: [...m.files, data.path] }))
            onFileEnd(data.path)
            break
          case 'file_error': setError({ message: `${data.path}: ${data.message}` }); onFileEnd(data.path); break
          case 'warning': setError({ message: data.message }); break
          case 'error': setError({ message: data.message }); break
          default: break
        }
      }, controller.signal)
    } catch (err) {
      if (err.name !== 'AbortError') setError(err)
    } finally {
      setBusy(false)
      setStatus('')
      abortRef.current = null
      onDone()
      window.dispatchEvent(new Event('ace:usage-changed'))
    }
  }

  return (
    <section className="chat">
      <div className="chat-log">
        {messages.length === 0 && (
          <div className="empty">
            <p className="muted">Tell Ace what to build.</p>
            {canChat && SUGGESTIONS.map((s) => <button key={s} className="chip" onClick={() => send(s)}>{s}</button>)}
          </div>
        )}
        {messages.map((m, i) => (
          <div key={i} className={`bubble ${m.role === 'USER' ? 'user' : 'assistant'}`}>
            {m.text && <p>{m.text}</p>}
            {m.files?.map((f) => <span key={f} className="file-chip done">✓ {f}</span>)}
            {m.writing?.map((f) => <span key={f} className="file-chip">✎ {f}</span>)}
          </div>
        ))}
        {status && <div className="muted status">{status}</div>}
        <div ref={bottomRef} />
      </div>

      {error && (
        <div className="alert">
          {error.message} {error.status === 402 && <Link to="/billing">Upgrade</Link>}
        </div>
      )}

      {canChat ? (
        <form className="chat-input" onSubmit={(e) => { e.preventDefault(); send(input) }}>
          <textarea
            rows={2}
            placeholder="Describe a change... (Enter to send, Shift+Enter for a new line)"
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={(e) => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); send(input) } }}
            maxLength={4000}
          />
          {busy
            ? <button type="button" className="btn danger" onClick={() => abortRef.current?.abort()}>Stop</button>
            : <button className="btn primary" disabled={!input.trim()}>Send</button>}
        </form>
      ) : (
        <div className="chat-input muted">You have view-only access to this project.</div>
      )}
    </section>
  )
}
