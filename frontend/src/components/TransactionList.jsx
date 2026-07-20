export default function TransactionList({ transactions, loading }) {
  if (loading) {
    return <p className="empty-state">Loading transactions...</p>
  }

  if (transactions.length === 0) {
    return <p className="empty-state">No transactions yet. Add one to get started.</p>
  }

  return (
    <div className="transaction-list">
      {transactions.map((t) => (
        <div key={t.transactionId} className={`transaction-row ${t.status.toLowerCase()}`}>
          <div className="transaction-main">
            <span className={`status-dot ${t.status.toLowerCase()}`} />
            <div>
              <div className="transaction-merchant">{t.merchant}</div>
              <div className="transaction-meta">
                {t.location} · {t.time}
                {t.isForeign && ' · Foreign'}
              </div>
            </div>
          </div>
          <div className="transaction-right">
            <div className="transaction-amount">Rs. {t.amount.toLocaleString()}</div>
            <div className={`transaction-status ${t.status.toLowerCase()}`}>{t.status}</div>
          </div>
          {t.reason && <div className="transaction-reason">{t.reason}</div>}
        </div>
      ))}
    </div>
  )
}