export function Modal({
  title,
  message,
  confirmLabel = 'OK',
  cancelLabel = '',
  onConfirm,
  onDismiss,
  danger = false,
}) {
  return (
    <div className="modal-backdrop" onClick={onDismiss} role="presentation">
      <div className="modal" role="dialog" aria-modal="true" onClick={(event) => event.stopPropagation()}>
        <h2>{title}</h2>
        <p className="lede">{message}</p>
        <div className="stack" style={{ marginTop: 20 }}>
          <button
            type="button"
            className="btn btn-block"
            style={danger ? { background: 'var(--danger)', color: '#fff' } : undefined}
            onClick={onConfirm}
          >
            {confirmLabel}
          </button>
          {cancelLabel ? (
            <button type="button" className="btn btn-ghost btn-block" onClick={onDismiss}>
              {cancelLabel}
            </button>
          ) : null}
        </div>
      </div>
    </div>
  )
}
