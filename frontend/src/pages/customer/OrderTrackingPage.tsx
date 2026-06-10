import { useParams, useNavigate } from 'react-router-dom';
import { useSession } from '../../hooks/useSession';
import { OrderStateIndicator } from '../../components/OrderStateIndicator';
import { QueuePositionDisplay } from '../../components/QueuePositionDisplay';
import { CancelOrderButton } from '../../components/CancelOrderButton';
import { PickupTimer } from '../../components/PickupTimer';
import { OrderCardSkeleton } from '../../components/Skeleton';
import { BackButton } from '../../components/BackButton';
import { useState, useEffect, useCallback } from 'react';
import { sessionApi } from '../../services/sessionApi';
import { orderApi } from '../../services/orderApi';
import type { Order } from '../../types/order';
import { OrderState } from '../../types/order';

export function OrderTrackingPage() {
  const { stationId } = useParams<{ stationId: string }>();
  const stId = parseInt(stationId || '0', 10);
  const { sessionId } = useSession(stId);
  const navigate = useNavigate();
  const [orders, setOrders] = useState<Order[]>([]);
  const [loading, setLoading] = useState(true);

  const fetchOrders = useCallback(async () => {
    if (!sessionId) return;
    try {
      const data = await sessionApi.getSessionOrders(sessionId);
      setOrders(data);
    } catch { /* ignore */ }
    finally { setLoading(false); }
  }, [sessionId]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    fetchOrders();
  }, [fetchOrders]);

  // Poll for updates every 5 seconds as a fallback
  useEffect(() => {
    const interval = setInterval(fetchOrders, 5000);
    return () => clearInterval(interval);
  }, [fetchOrders]);

  const handleCancel = async (orderId: number) => {
    if (!sessionId) return;
    try {
      await orderApi.cancel(orderId, sessionId);
      fetchOrders();
    } catch { /* ignore */ }
  };

  if (loading) {
    return (
      <div className="page-container">
        <div style={{ marginBottom: 16 }}>
          <div className="skeleton skeleton-heading" style={{ width: '50%' }} />
        </div>
        <OrderCardSkeleton />
        <OrderCardSkeleton />
      </div>
    );
  }

  return (
    <div className="page-container">
      <BackButton to={`/station/${stId}/order`} label="Back to Order" />
      <h1 style={{ fontSize: '1.3rem', fontWeight: 700, marginBottom: 16, color: 'var(--text-primary)' }}>
        📋 Your Orders
      </h1>

      {orders.length === 0 && (
        <div className="card" style={{ padding: 24, textAlign: 'center' }}>
          <div style={{ fontSize: '2rem', marginBottom: 8 }}>🍹</div>
          <p style={{ color: 'var(--text-muted)' }}>No orders yet. Start ordering!</p>
        </div>
      )}

      {orders.map((order, index) => (
        <div
          key={order.id}
          className={`glass-card card-fade-in ${order.state === OrderState.READY ? 'pulse-glow' : ''}`}
          style={{
            padding: 16,
            marginBottom: 12,
            animationDelay: `${index * 0.08}s`,
            background: order.state === OrderState.READY
              ? 'linear-gradient(135deg, var(--color-success-light), var(--bg-glass-strong))'
              : undefined,
          }}
        >
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 10 }}>
            {order.visualOrderNumber && (
              <span style={{ fontSize: '1.3rem', fontWeight: 700, color: 'var(--color-primary)' }}>
                {order.visualOrderNumber}
              </span>
            )}
            <OrderStateIndicator state={order.state} />
          </div>

          {order.queuePosition && (order.state === OrderState.PAID || order.state === OrderState.PREPARING) && (
            <div style={{ marginBottom: 10 }}>
              <QueuePositionDisplay position={order.queuePosition} />
            </div>
          )}

          {order.state === OrderState.READY && (
            <div
              className="bounce-in"
              style={{
                padding: 14,
                backgroundColor: 'var(--color-success-light)',
                borderRadius: 'var(--radius-md)',
                marginTop: 8,
                textAlign: 'center',
                border: '1px solid var(--color-success)',
              }}
            >
              <p style={{ fontWeight: 700, color: 'var(--color-success)', fontSize: '1.1rem' }}>🎉 Your order is ready!</p>
              <p style={{ color: 'var(--color-success-dark)', fontSize: '0.9rem', marginTop: 4 }}>
                Show order number <strong>{order.visualOrderNumber}</strong> at the bar
              </p>
              {order.pickupWindowStart && (
                <div style={{ marginTop: 8 }}>
                  <PickupTimer pickupWindowStart={order.pickupWindowStart} pickupWindowMinutes={10} />
                </div>
              )}
            </div>
          )}

          {order.state === OrderState.CANCELLED && (
            <p style={{ color: 'var(--color-danger)', fontWeight: 600, marginTop: 8 }}>This order has been cancelled.</p>
          )}

          {order.state === OrderState.EXPIRED && (
            <p style={{ color: 'var(--text-muted)', fontWeight: 600, marginTop: 8 }}>This order has expired. Please contact the vendor.</p>
          )}

          <div style={{ marginTop: 10, fontSize: '0.85rem', color: 'var(--text-muted)' }}>
            {order.items.map((item, i) => (
              <div key={i}>
                {item.spiritItems && item.spiritItems.length > 0 && item.mixerItems && item.mixerItems.length > 0
                  ? `${item.spiritItems.map(s => s.name).join(' + ')} + ${item.mixerItems.map(m => m.name).join(' + ')}`
                  : item.premadeItemName}
                {' '}× {item.quantity}
              </div>
            ))}
            <div style={{ fontWeight: 600, marginTop: 4, color: 'var(--text-secondary)' }}>
              Total: <span style={{ color: 'var(--color-success)' }}>R{order.totalPrice.toFixed(2)}</span>
            </div>
          </div>

          <div style={{ marginTop: 10, display: 'flex', gap: 8, flexWrap: 'wrap' }}>
            {order.state === OrderState.DRAFT && (
              <button
                onClick={() => navigate(`/station/${stId}/order?draftOrderId=${order.id}`)}
                className="btn btn-primary"
                style={{ fontSize: '0.85rem', padding: '8px 16px' }}
              >
                Continue Editing
              </button>
            )}
            <CancelOrderButton orderState={order.state} onCancel={() => handleCancel(order.id)} />
          </div>
        </div>
      ))}
    </div>
  );
}
