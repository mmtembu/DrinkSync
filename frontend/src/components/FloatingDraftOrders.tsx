import { useState, useEffect, useCallback } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { orderApi } from '../services/orderApi';
import type { Order } from '../types/order';
import { OrderState, isTerminalState } from '../types/order';

interface Props {
  stationId: number;
  sessionId: string | null;
}

export function FloatingDraftOrders({ stationId, sessionId }: Props) {
  const [activeOrders, setActiveOrders] = useState<Order[]>([]);
  const [expanded, setExpanded] = useState(false);
  const navigate = useNavigate();
  const location = useLocation();

  const fetchOrders = useCallback(async () => {
    if (!sessionId) return;
    try {
      const orders = await orderApi.getSessionOrders(sessionId);
      const active = orders.filter((o: Order) => !isTerminalState(o.state));
      setActiveOrders(active);
    } catch {
      // Silently fail — this is a background check
    }
  }, [sessionId]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    fetchOrders();
    const interval = setInterval(() => { fetchOrders(); }, 5000);
    return () => clearInterval(interval);
  }, [fetchOrders]);

  // Don't show on the tracking page (they're already viewing orders)
  if (location.pathname.includes('/tracking')) return null;

  // Don't show if no active orders
  if (activeOrders.length === 0) return null;

  const paidOrders = activeOrders.filter((o) =>
    o.state === OrderState.PAID || o.state === OrderState.PREPARING || o.state === OrderState.READY
  );

  const getStateEmoji = (state: OrderState) => {
    switch (state) {
      case OrderState.DRAFT: return '📝';
      case OrderState.AWAITING_PAYMENT: return '💳';
      case OrderState.PAID: return '✅';
      case OrderState.PREPARING: return '🍹';
      case OrderState.READY: return '🔔';
      default: return '📋';
    }
  };

  const getStateLabel = (state: OrderState) => {
    switch (state) {
      case OrderState.DRAFT: return 'Draft';
      case OrderState.AWAITING_PAYMENT: return 'Awaiting Payment';
      case OrderState.PAID: return 'Paid';
      case OrderState.PREPARING: return 'Preparing';
      case OrderState.READY: return 'Ready!';
      default: return state;
    }
  };

  return (
    <>
      {/* Floating badge */}
      <button
        onClick={() => setExpanded(!expanded)}
        aria-label={`${activeOrders.length} active order${activeOrders.length > 1 ? 's' : ''}`}
        style={{
          position: 'fixed',
          bottom: 20,
          right: 20,
          zIndex: 90,
          width: 56,
          height: 56,
          borderRadius: '50%',
          border: 'none',
          backgroundColor: 'var(--color-primary)',
          color: 'var(--text-inverse)',
          fontSize: '1.3rem',
          cursor: 'pointer',
          boxShadow: 'var(--shadow-lg)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          transition: 'all var(--transition-fast)',
          animation: paidOrders.some((o) => o.state === OrderState.READY) ? 'pulse 1.5s ease-in-out infinite' : undefined,
        }}
      >
        🛒
        <span
          style={{
            position: 'absolute',
            top: -4,
            right: -4,
            width: 22,
            height: 22,
            borderRadius: '50%',
            backgroundColor: 'var(--color-danger)',
            color: '#fff',
            fontSize: '0.75rem',
            fontWeight: 700,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
          }}
        >
          {activeOrders.length}
        </span>
      </button>

      {/* Expanded panel */}
      {expanded && (
        <div
          style={{
            position: 'fixed',
            bottom: 84,
            right: 20,
            zIndex: 89,
            width: 280,
            maxHeight: 360,
            overflowY: 'auto',
            borderRadius: 'var(--radius-lg)',
            padding: 12,
            background: 'var(--bg-glass-strong)',
            backdropFilter: 'var(--glass-blur)',
            WebkitBackdropFilter: 'var(--glass-blur)',
            border: '1px solid var(--glass-border)',
            boxShadow: 'var(--shadow-lg)',
          }}
          className="card-fade-in"
        >
          <div style={{ fontWeight: 700, fontSize: '0.9rem', marginBottom: 8, color: 'var(--text-primary)' }}>
            Active Orders
          </div>

          {activeOrders.map((order) => (
            <button
              key={order.id}
              onClick={() => {
                setExpanded(false);
                if (order.state === OrderState.DRAFT) {
                  navigate(`/station/${stationId}/order?draftOrderId=${order.id}`);
                } else if (order.state === OrderState.AWAITING_PAYMENT) {
                  navigate(`/station/${stationId}/checkout/${order.id}`);
                } else {
                  navigate(`/station/${stationId}/tracking`);
                }
              }}
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 10,
                width: '100%',
                padding: '10px 12px',
                marginBottom: 6,
                borderRadius: 'var(--radius-md)',
                border: '1px solid var(--border-color)',
                backgroundColor: 'var(--bg-card)',
                cursor: 'pointer',
                textAlign: 'left',
                transition: 'all var(--transition-fast)',
              }}
              className="btn"
            >
              <span style={{ fontSize: '1.3rem' }}>{getStateEmoji(order.state)}</span>
              <div style={{ flex: 1 }}>
                <div style={{ fontWeight: 600, fontSize: '0.85rem', color: 'var(--text-primary)' }}>
                  {order.visualOrderNumber || `Order #${order.id}`}
                </div>
                <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>
                  {getStateLabel(order.state)} · R{order.totalPrice.toFixed(2)}
                </div>
              </div>
              <span style={{ color: 'var(--text-muted)', fontSize: '0.8rem' }}>→</span>
            </button>
          ))}

          <button
            onClick={() => {
              setExpanded(false);
              navigate(`/station/${stationId}/tracking`);
            }}
            className="btn btn-primary btn-full"
            style={{ marginTop: 4, fontSize: '0.85rem', padding: '8px 12px' }}
          >
            View All Orders
          </button>
        </div>
      )}
    </>
  );
}
