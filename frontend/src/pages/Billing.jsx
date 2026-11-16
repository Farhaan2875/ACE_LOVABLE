import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { api } from '../api.js'
import Header from '../components/Header.jsx'

export default function Billing() {
  const [params] = useSearchParams()
  const [plans, setPlans] = useState([])
  const [sub, setSub] = useState(null)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  useEffect(() => { api.plans().then(setPlans); api.subscription().then(setSub) }, [])

  // Redirects to Stripe (or straight back in dev mode)
  async function go(action) {
    setError('')
    setBusy(true)
    try {
      const result = await action()
      window.location.href = result.checkoutUrl || result.portalUrl
    } catch (e) {
      setError(e.message)
      setBusy(false)
    }
  }

  const current = sub?.plan?.name
  return (
    <>
      <Header />
      <main className="container">
        <h2>Plans</h2>
        {params.get('status') === 'success' && <div className="notice">Thank you! Your plan will update in a few seconds.</div>}
        {params.get('status') === 'cancelled' && <div className="alert">Checkout was cancelled.</div>}
        {error && <div className="alert">{error}</div>}

        <div className="grid">
          {plans.map((p) => (
            <div key={p.id} className={`card plan ${p.name === current ? 'current' : ''}`}>
              <h3>{p.name}</h3>
              <div className="price">{p.price}</div>
              <ul>
                <li>{p.maxProjects} projects</li>
                <li>{p.unlimitedAi ? 'Unlimited AI usage' : `${p.maxTokensPerDay?.toLocaleString()} AI tokens / day`}</li>
              </ul>
              {p.name === current
                ? <button className="btn ghost" disabled>Current plan</button>
                : p.name === 'FREE'
                  ? <small className="muted">Cancel your paid plan to return to Free.</small>
                  : <button className="btn primary" disabled={busy} onClick={() => go(() => api.checkout(p.id))}>Choose {p.name}</button>}
            </div>
          ))}
        </div>

        {sub && (
          <div className="card" style={{ marginTop: 24 }}>
            <p>Current plan: <strong>{sub.plan.name}</strong> ({sub.status}){sub.periodEnd && <> - renews / ends {new Date(sub.periodEnd).toLocaleDateString()}</>}</p>
            <p className="muted">Tokens used this billing period: {sub.tokenUsedThisCycle.toLocaleString()}</p>
            {sub.plan.name !== 'FREE' && <button className="btn ghost" disabled={busy} onClick={() => go(api.portal)}>Manage / cancel subscription</button>}
          </div>
        )}
      </main>
    </>
  )
}
