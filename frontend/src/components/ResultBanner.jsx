export default function ResultBanner({ result, onDismiss }) {
  if (!result) return null

  const status = result.status.toLowerCase()
  const title =
    status === 'approved'
      ? 'Transaction Approved'
      : status === 'flagged'
      ? 'Transaction Flagged'
      : 'Transaction Blocked'

  return (
    <div className={`banner ${status}`}>
      <div className="banner-content">
        <h3>
          {status !== 'approved' && '⚠ '}
          {title} — {result.transactionId}
        </h3>
        <p>
          <strong>Card:</strong> {result.maskedCard} ({result.cardHolder})
        </p>
        {result.cardBlocked && (
          <p className="banner-warning">This card has been blocked.</p>
        )}
        {result.reason && <p className="banner-warning">{result.reason}</p>}
      </div>
      <button className="banner-dismiss" onClick={onDismiss} aria-label="Dismiss">
        &times;
      </button>
    </div>
  )
}