import { OrderStateIndicator } from './OrderStateIndicator';
import { PickupTimer } from './PickupTimer';
import type { Order } from '../types/order';
import { OrderState } from '../types/order';

interface Props {
  order: Order;
  onTransition?: (orderId: number, targetState: OrderState) => void;
  pickupWindowMinutes?: number;
}

const getNextState = (state: OrderState): OrderState | null => {
  switch (state) {
    case OrderState.PAID: return OrderState.PREPARING;
    case OrderState.PREPARING: return OrderState.READY;
    case OrderState.READY: return OrderState.COLLECTED;
    default: return null;
  }
};

const getActionLabel = (state: OrderState): string => {
  switch (state) {
    case OrderState.PAID: return 'Start Preparing';
    case OrderState.PREPARING: return 'Mark Ready';
    case OrderState.READY: return 'Mark Collected';
    default: return '';
  }
};

const getActionClass = (state: OrderState): string => {
  switch (state) {
    case OrderState.PAID: return 'btn btn-primary';
    case OrderState.PREPARING: return 'btn btn-primary';
    case OrderState.READY: return 'btn btn-success';
    default: return 'btn btn-primary';
  }
};

export function VendorOrderCard({ order, onTransition, pickupWindowMinutes = 10 }: Props) {
  const nextState = getNextState(order.state);
  const isReady = order.state === OrderState.READY;

  return (
    <div
      data-testid="vendor-order-card"
      className={`glass-card card-fade-in ${isReady ? 'pulse-glow' : ''}`}
      style={{
        padding: 16,
        transition: 'all var(--transition-normal)',
      }}
    >
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 10 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <span
            data-testid="visual-order-number"
            style={{ fontSize: '1.3rem', fontWeight: 700, color: 'var(--color-primary)' }}
          >
            {order.visualOrderNumber || `#${order.id}`}
          </span>
          <OrderStateIndicator state={order.state} />
          {order.queuePosition != null && (
            <span data-testid="queue-position" style={{ color: 'var(--text-muted)', fontSize: '0.85rem' }}>
              Queue #{order.queuePosition}
            </span>
          )}
        </div>
        {order.state === OrderState.READY && order.pickupWindowStart && (
          <PickupTimer pickupWindowStart={order.pickupWindowStart} pickupWindowMinutes={pickupWindowMinutes} />
        )}
      </div>

      <div data-testid="order-items" style={{ fontSize: '0.9rem', marginBottom: 10, color: 'var(--text-secondary)' }}>
        {order.items.map((item, i) => (
          <div key={i} data-testid="order-item-row" style={{ padding: '2px 0' }}>
            <span data-testid="item-details">
              {item.spiritItems && item.spiritItems.length > 0 && item.mixerItems && item.mixerItems.length > 0
                ? `${item.spiritItems.map(s => s.name).join(' + ')} + ${item.mixerItems.map(m => m.name).join(' + ')} (${item.cupOption === 'NEW_CUP' ? 'New Cup' : 'Reuse Cup'})`
                : item.premadeItemName}
            </span>
            {' '}
            <span data-testid="item-quantity" style={{ color: 'var(--text-muted)' }}>× {item.quantity}</span>
          </div>
        ))}
      </div>

      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <span data-testid="total-price" style={{ fontWeight: 600, color: 'var(--text-primary)' }}>
          R{order.totalPrice.toFixed(2)}
        </span>
        {nextState && onTransition && (
          <button
            onClick={() => onTransition(order.id, nextState)}
            className={getActionClass(order.state)}
          >
            {getActionLabel(order.state)}
          </button>
        )}
      </div>
    </div>
  );
}
