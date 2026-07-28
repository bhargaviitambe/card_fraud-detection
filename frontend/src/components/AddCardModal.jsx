import { useEffect, useState } from 'react'

export default function AddCardModal({ onClose, onCreated }) {
  const [customers, setCustomers] = useState([])
  const [form, setForm] = useState({ customerId: '', cardNumber: '', creditLimit: '' })
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState(null)

  useEffect(() => {
    fetch('/api/customers')
      .then((res) => res.json())
      .then((data) => {
        setCustomers(data)
        if (data.length > 0) setForm((f) => ({ ...f, customerId: data[0].id }))
      })
  }, [])

  function updateField(field, value) {
    setForm((f) => ({ ...f, [field]: value }))
  }

  async function handleSubmit(e) {
    e.preventDefault()
    setSubmitting(true)
    setError(null)

    try {
      const res = await fetch('/api/cards', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ ...form, creditLimit: parseFloat(form.creditLimit) }),
      })
      const data = await res.json()
      if (data.error) {
        setError(data.error)
        setSubmitting(false)
        return
      }
      onCreated(data)
    } catch {
      setError('Request failed. Is the backend server running on port 8080?')
      setSubmitting(false)
    }
  }

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        <div className="modal-header">
          <h2>Add Card to Existing Customer</h2>
          <button className="modal-close" onClick={onClose} aria-label="Close">&times;</button>
        </div>

        <form onSubmit={handleSubmit}>
          <label>
            Customer
            <select value={form.customerId} onChange={(e) => updateField('customerId', e.target.value)} required>
              {customers.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name} ({c.city}) — {c.cardCount} card{c.cardCount !== 1 ? 's' : ''}
                </option>
              ))}
            </select>
          </label>

          <label>
            Card Number
            <input
              type="text"
              placeholder="e.g. 4111000000000002"
              value={form.cardNumber}
              onChange={(e) => updateField('cardNumber', e.target.value)}
              autoFocus
              required
            />
          </label>

          <label>
            Credit Limit (Rs.)
            <input
              type="number"
              min="0"
              step="0.01"
              placeholder="e.g. 50000"
              value={form.creditLimit}
              onChange={(e) => updateField('creditLimit', e.target.value)}
              required
            />
          </label>

          {error && <p className="modal-error">{error}</p>}

          <button type="submit" disabled={submitting}>
            {submitting ? 'Adding...' : 'Add Card'}
          </button>
        </form>
      </div>
    </div>
  )
}