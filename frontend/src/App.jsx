import { useEffect, useState, useCallback } from 'react'
import TransactionModal from './components/TransactionModal'
import ResultBanner from './components/ResultBanner'
import TransactionList from './components/TransactionList'
import './App.css'

export default function App() {
  const [cards, setCards] = useState([])
  const [selectedCard, setSelectedCard] = useState('')
  const [transactions, setTransactions] = useState([])
  const [loadingTxns, setLoadingTxns] = useState(false)
  const [modalOpen, setModalOpen] = useState(false)
  const [lastResult, setLastResult] = useState(null)
  const [connectionError, setConnectionError] = useState(null)

  const loadCards = useCallback(() => {
    return fetch('/api/cards')
      .then((res) => res.json())
      .then((data) => {
        setCards(data)
        setConnectionError(null)
        setSelectedCard((current) => current || data[0]?.cardNumber || '')
        return data
      })
      .catch(() => {
        setConnectionError('Could not reach the fraud detection server. Is it running on port 8080?')
      })
  }, [])

  const loadTransactions = useCallback((cardNumber) => {
    if (!cardNumber) return
    setLoadingTxns(true)
    fetch(`/api/transactions?cardNumber=${encodeURIComponent(cardNumber)}`)
      .then((res) => res.json())
      .then((data) => setTransactions(Array.isArray(data) ? data : []))
      .catch(() => setTransactions([]))
      .finally(() => setLoadingTxns(false))
  }, [])

  useEffect(() => {
    loadCards()
  }, [loadCards])

  useEffect(() => {
    if (selectedCard) loadTransactions(selectedCard)
  }, [selectedCard, loadTransactions])

  function handleSubmitted(result) {
    setModalOpen(false)
    setLastResult(result)
    loadCards()
    loadTransactions(selectedCard)
  }

  const currentCard = cards.find((c) => c.cardNumber === selectedCard)

  return (
    <div className="page">
      <div className="dashboard">
        <header className="dashboard-header">
          <div>
            <h1>Your Transactions</h1>
            {currentCard && (
              <p className="subtitle">
                {currentCard.masked} — {currentCard.owner} ({currentCard.city})
                {currentCard.blocked && <span className="blocked-tag"> BLOCKED</span>}
              </p>
            )}
          </div>
          <button className="add-btn" onClick={() => setModalOpen(true)}>
            + New Transaction
          </button>
        </header>

        {connectionError && <div className="banner blocked"><div className="banner-content"><p>{connectionError}</p></div></div>}

        {cards.length > 1 && (
          <div className="card-switcher">
            {cards.map((c) => (
              <button
                key={c.cardNumber}
                className={`card-chip ${c.cardNumber === selectedCard ? 'active' : ''}`}
                onClick={() => setSelectedCard(c.cardNumber)}
              >
                {c.masked} — {c.owner}
              </button>
            ))}
          </div>
        )}

        <ResultBanner result={lastResult} onDismiss={() => setLastResult(null)} />

        <TransactionList transactions={transactions} loading={loadingTxns} />
      </div>

      {modalOpen && (
        <TransactionModal
          cards={cards}
          onClose={() => setModalOpen(false)}
          onSubmitted={handleSubmitted}
        />
      )}
    </div>
  )
}