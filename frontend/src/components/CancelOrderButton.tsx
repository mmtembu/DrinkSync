import { OrderState } from '../types/order';

interface Props {
  orderState: OrderState;
  onCancel: () => void;
  loading?: boolean;
}

export function CancelOrderButton({ orderState, onCancel, loading }: Props) {
  const canCancel = orderState === OrderState.DRAFT || orderState === OrderState.AWAITING_PAYMENT;

  if (!canCancel) return null;

  return (
    <button
      onClick={onCancel}
      disabled={loading}
      aria-label="Cancel order"
      className="btn btn-danger-outline"
      style={{ fontSize: '0.85rem', padding: '8px 16px' }}
    >
      {loading ? 'Cancelling...' : 'Cancel Order'}
    </button>
  );
}
