import { useEffect, useRef } from 'react'
import type { OrderAction, OrderStatus } from './orderApi'
import { orderActionLabels } from './orderApi'

export function OrderActionDialog({ action, status, onDismiss, onConfirm }: {
  action: OrderAction; status: OrderStatus; onDismiss: () => void; onConfirm: () => void
}) {
  const dialogRef = useRef<HTMLDialogElement>(null)
  const cancelRef = useRef<HTMLButtonElement>(null)
  useEffect(() => {
    const previous = document.activeElement instanceof HTMLElement ? document.activeElement : null
    const dialog = dialogRef.current!
    dialog.showModal()
    cancelRef.current?.focus()
    return () => { dialog.close(); previous?.focus() }
  }, [])
  const descriptions: Record<OrderAction, string> = {
    confirm: 'Reserve all order items together. Physical on-hand stock will not decrease.',
    process: 'Begin preparing this order. Cancellation will no longer be available once processing starts.',
    cancel: status === 'PENDING' ? 'Cancel this pending order. No stock is reserved, so stock will not change.' : 'Cancel this order and release all its reserved stock for other orders.',
    fulfill: 'Complete this order in full. All its reservations will be consumed and physical on-hand stock will decrease. This cannot be undone here.',
  }
  return <dialog ref={dialogRef} className="order-action-dialog" aria-labelledby="order-action-title" aria-describedby="order-action-description"
    onCancel={event => { event.preventDefault(); onDismiss() }}>
    <h2 id="order-action-title">{orderActionLabels[action]}?</h2><p id="order-action-description">{descriptions[action]}</p>
    <div className="inline-actions"><button ref={cancelRef} type="button" className="button secondary" onClick={onDismiss}>Go back</button>
      <button type="button" className="button primary" onClick={onConfirm}>{orderActionLabels[action]}</button></div>
  </dialog>
}
