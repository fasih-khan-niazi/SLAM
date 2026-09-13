import { Button } from './Button'

export function Modal({
  title,
  message,
  children,
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
        {message ? <p className="lede">{message}</p> : null}
        {children}
        <div className="stack" style={{ marginTop: 20 }}>
          {onConfirm ? (
            <Button
              block
              variant={danger ? 'danger' : 'primary'}
              onClick={onConfirm}
            >
              {confirmLabel}
            </Button>
          ) : null}
          {cancelLabel ? (
            <Button variant="ghost" block onClick={onDismiss}>
              {cancelLabel}
            </Button>
          ) : null}
        </div>
      </div>
    </div>
  )
}
