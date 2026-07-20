import { useState } from 'react'

export default function TransactionModal({ cards, onClose, onSubmitted }) {
  const [form, setForm] = useState({
    cardNumber: cards[0]?.cardNumber || '',
    amount: '',
    merchant: '',
    location: '',
    isForeign: false,
  })
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState(null)

  function updateField(field, value) {
    setForm((f) => ({ ...f, [field]: value }))
  }

  async function handleSubmit(e) {
    e.preventDefault()
    setSubmitting(true)
    setError(null)

    try {
      const res = await fetch('/api/transaction', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ ...form, amount: parseFloat(form.amount) }),
      })
      const data = await res.json()

      if (data.error) {
        setError(data.error)
        setSubmitting(false)
        return
      }

      onSubmitted(data)
    } catch {
      setError('Request failed. Is the backend server running on port 8080?')
      setSubmitting(false)
    }
  }

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        <div className="modal-header">
          <h2>New Transaction</h2>
          <button className="modal-close" onClick={onClose} aria-label="Close">
            &times;
          </button>
        </div>

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
              autoFocus
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

          {error && <p className="modal-error">{error}</p>}

          <button type="submit" disabled={submitting}>
            {submitting ? 'Checking...' : 'Submit Transaction'}
          </button>
        </form>
      </div>
    </div>
  )
}