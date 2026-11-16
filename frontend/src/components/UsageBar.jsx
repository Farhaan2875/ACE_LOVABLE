import { useEffect, useState } from 'react'
import { api } from '../api.js'

// Small "AI tokens used today" indicator in the header.
export default function UsageBar() {
  const [usage, setUsage] = useState(null)

  useEffect(() => {
    const load = () => api.usage().then(setUsage).catch(() => {})
    load()
    const timer = setInterval(load, 30000)
    window.addEventListener('ace:usage-changed', load) // the chat panel fires this after every answer
    return () => { clearInterval(timer); window.removeEventListener('ace:usage-changed', load) }
  }, [])

  if (!usage) return null
  const unlimited = usage.tokenLimit == null
  const percent = unlimited ? 0 : Math.min(100, Math.round((usage.tokenUsed / usage.tokenLimit) * 100))
  return (
    <div className="usage" title="AI tokens used today (resets 00:00 UTC)">
      <div className="usage-track"><div className={`usage-fill ${percent > 85 ? 'hot' : ''}`} style={{ width: `${percent}%` }} /></div>
      <small className="muted">{usage.tokenUsed.toLocaleString()} {unlimited ? 'tokens (unlimited)' : `/ ${usage.tokenLimit.toLocaleString()}`}</small>
    </div>
  )
}
