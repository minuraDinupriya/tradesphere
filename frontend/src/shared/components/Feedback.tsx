export function LoadingState({ label = 'Loading products…' }: { label?: string }) {
  return <div className="state-panel" role="status"><span className="spinner" aria-hidden="true" />{label}</div>
}

export function ErrorState({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return <div className="error-panel" role="alert"><strong>Something needs your attention</strong><p>{message}</p>
    {onRetry && <button className="button secondary" onClick={onRetry}>Try again</button>}</div>
}
