import { useEffect, useState } from 'react'
import './App.css'

export default function App() {
  const [cards, setCards] = useState([])
  const [form, setForm] = useState({
    cardNumber: '',
    amount: '',
    merchant: '',
    location: '',
    isForeign: false,
  })
  const [result, setResult] = useState(null)
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    fetch('/api/cards')
      .then((res) => res.json())
      .then((data) => {
        setCards(data)
        if (data.length > 0) {
          setForm((f) => ({ ...f, cardNumber: data[0].cardNumber }))
        }
      })
      .catch(() => setError('Could not reach the fraud detection server.'))
  }, [])

  function updateField(field, value) {
    setForm((f) => ({ ...f, [field]: value }))
  }

  async function handleSubmit(e) {
    e.preventDefault()
    setLoading(true)
    setError(null)
    setResult(null)

    try {
      const res = await fetch('/api/transaction', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          ...form,
          amount: parseFloat(form.amount),
        }),
      })
      const data = await res.json()
      if (data.error) {
        setError(data.error)
      } else {
        setResult(data)
        fetch('/api/cards').then((r) => r.json()).then(setCards)
      }
    } catch {
      setError('Request failed. Is the backend server running on port 8080?')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="container">
      <h1>Credit Card Fraud Detection</h1>
      <p className="subtitle">
        Enter a transaction manually and see how the fraud engine scores it.
      </p>

      <form onSubmit={handleSubmit}>
        <label>
          Card
          <select
            value={form.cardNumber}
            onChange={(e) => updateField('cardNumber', e.target.value)}
            required
          >
            {cards.map((c) => (
              <option key={c.cardNumber} value={c.cardNumber}>
                {c.masked} — {c.owner} ({c.city})
                {c.blocked ? ' [BLOCKED]' : ''}
              </option>
            ))}
          </select>
        </label>

        <label>
          Amount (Rs.)
          <input
            type="number"
            min="0"
            step="0.01"
            placeholder="e.g. 5000"
            value={form.amount}
            onChange={(e) => updateField('amount', e.target.value)}
            required
          />
        </label>

        <label>
          Merchant
          <input
            type="text"
            placeholder="e.g. Amazon"
            value={form.merchant}
            onChange={(e) => updateField('merchant', e.target.value)}
            required
          />
        </label>

        <label>
          Location
          <input
            type="text"
            placeholder="e.g. Mumbai"
            value={form.location}
            onChange={(e) => updateField('location', e.target.value)}
            required
          />
        </label>

        <label className="checkbox">
          <input
            type="checkbox"
            checked={form.isForeign}
            onChange={(e) => updateField('isForeign', e.target.checked)}
          />
          Foreign transaction
        </label>

        <button type="submit" disabled={loading}>
          {loading ? 'Checking...' : 'Submit Transaction'}
        </button>
      </form>

      {error && (
        <div className="result blocked">
          <strong>Error:</strong> {error}
        </div>
      )}

      {result && (
        <div className={`result ${result.status.toLowerCase()}`}>
          <h3>
            {result.status} — {result.transactionId}
          </h3>
          <p>
            <strong>Card:</strong> {result.maskedCard} ({result.cardHolder})
          </p>
          {result.cardBlocked && (
            <p className="warning">⚠ This card has been blocked.</p>
          )}
          {result.reason ? (
            <p className="warning">⚠ {result.reason}</p>
          ) : (
            <p>No fraud indicators detected.</p>
          )}
        </div>
      )}
    </div>
  )
}