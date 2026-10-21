export default function RequestError({ message, onRetry, retryLabel = "Try again", pending = false }) {
  if (!message) return null;
  return <div className="alert alert-danger d-flex flex-wrap align-items-center justify-content-between gap-2" role="alert">
    <span>{message}</span>
    {onRetry && <button type="button" className="btn btn-sm btn-outline-light" onClick={onRetry} disabled={pending}>{retryLabel}</button>}
  </div>;
}
