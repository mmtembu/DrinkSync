import { OrderState, ORDER_STATE_LABELS } from '../types/order';
import { useEffect, useRef, useState } from 'react';

const STATE_BADGE_CLASS: Record<OrderState, string> = {
  [OrderState.DRAFT]: 'state-badge-draft',
  [OrderState.AWAITING_PAYMENT]: 'state-badge-awaiting',
  [OrderState.PAID]: 'state-badge-paid',
  [OrderState.PREPARING]: 'state-badge-preparing',
  [OrderState.READY]: 'state-badge-ready',
  [OrderState.COLLECTED]: 'state-badge-collected',
  [OrderState.CANCELLED]: 'state-badge-cancelled',
  [OrderState.EXPIRED]: 'state-badge-expired',
};

interface Props {
  state: OrderState;
}

export function OrderStateIndicator({ state }: Props) {
  const prevState = useRef(state);
  const [animating, setAnimating] = useState(false);

  useEffect(() => {
    if (prevState.current !== state) {
      setAnimating(true);
      prevState.current = state;
      const timer = setTimeout(() => setAnimating(false), 600);
      return () => clearTimeout(timer);
    }
  }, [state]);

  return (
    <span
      role="status"
      aria-label={`Order status: ${ORDER_STATE_LABELS[state]}`}
      className={`badge ${STATE_BADGE_CLASS[state]} ${animating ? 'pulse-glow' : ''}`}
      style={animating ? { animation: 'pulseGlow 0.6s ease' } : undefined}
    >
      {ORDER_STATE_LABELS[state]}
    </span>
  );
}
