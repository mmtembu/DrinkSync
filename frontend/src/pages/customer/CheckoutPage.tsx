import { useParams, useNavigate } from 'react-router-dom';
import { useSession } from '../../hooks/useSession';
import { Skeleton } from '../../components/Skeleton';
import { BackButton } from '../../components/BackButton';
import { useState, useEffect } from 'react';
import { orderApi } from '../../services/orderApi';
import { generateIdempotencyKey } from '../../services/idempotencyKeyGenerator';
import type { Order } from '../../types/order';
import { OrderState } from '../../types/order';

export function CheckoutPage() {
  const { stationId, orderId } = useParams<{ stationId: string; orderId: string }>();
  const stId = parseInt(stationId || '0', 10);
  const oId = parseInt(orderId || '0', 10);
  const navigate = useNavigate();
  const { sessionId } = useSession(stId);

  const [order, setOrder] = useState<Order | null>(null);
  const [loading, setLoading] = useState(false);
  const [paymentLoading, setPaymentLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (sessionId && oId) {
      setLoading(true);
      orderApi.getOrder(oId, sessionId).then(setOrder).catch((e) => setError(e.message)).finally(() => setLoading(false));
    }
  }, [oId, sessionId]);

  const handleCheckout = async () => {
    if (!sessionId || !order) return;
    setLoading(true);
    setError(null);
    try {
      const updated = await orderApi.checkout(order.id, sessionId);
      setOrder(updated);
    } catch (e: any) {
      setError(e.body?.message || e.message);
    } finally {
      setLoading(false);
    }
  };

  const handlePay = async () => {
    if (!sessionId || !order) return;
    setPaymentLoading(true);
    setError(null);
    try {
      const key = generateIdempotencyKey();
      const updated = await orderApi.pay(order.id, key, sessionId);
      setOrder(updated);
      navigate(`/station/${stId}/tracking`);
    } catch (e: any) {
      setError(e.body?.message || e.message || 'Payment failed. Please try again.');
    } finally {
      setPaymentLoading(false);
    }
  };

  const handleCancel = async () => {
    if (!sessionId || !order) return;
    try {
      await orderApi.cancel(order.id, sessionId);
      navigate(`/station/${stId}`);
    } catch (e: any) {
      setError(e.body?.message || e.message);
    }
  };

  if (loading && !order) {
    return (
      <div className="page-container">
        <Skeleton variant="heading" width="40%" />
        <div style={{ marginTop: 16 }}>
          <Skeleton variant="card" />
          <Skeleton variant="card" height="50px" />
        </div>
        <Skeleton variant="button" />
      </div>
    );
  }

  return (
    <div className="page-container">
      <BackButton to={`/station/${stId}/order`} label="Back to Order" />
      <h1 style={{ fontSize: '1.3rem', fontWeight: 700, color: 'var(--text-primary)', marginBottom: 16 }}>
        💳 Checkout
      </h1>

      {order && (
        <div className="card-fade-in">
          <div
            className="glass-card"
            style={{ padding: 16, marginBottom: 16 }}
          >
            <h2 style={{ fontSize: '1rem', fontWeight: 600, marginBottom: 12, color: 'var(--text-primary)' }}>
              Order Summary
            </h2>
            {order.items.map((item, i) => (
              <div key={i} style={{ display: 'flex', justifyContent: 'space-between', padding: '8px 0', borderBottom: '1px solid var(--border-light)' }}>
                <span style={{ color: 'var(--text-secondary)' }}>
                  {item.spiritItems && item.spiritItems.length > 0 && item.mixerItems && item.mixerItems.length > 0
                    ? `${item.spiritItems.map(s => s.name).join(' + ')} + ${item.mixerItems.map(m => m.name).join(' + ')}`
                    : item.premadeItemName}
                  {' '}× {item.quantity}
                </span>
                <span style={{ fontWeight: 600, color: 'var(--text-primary)' }}>R{(item.unitPrice * item.quantity).toFixed(2)}</span>
              </div>
            ))}
            <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: 14, fontWeight: 700, fontSize: '1.2rem' }}>
              <span style={{ color: 'var(--text-primary)' }}>Total</span>
              <span style={{ color: 'var(--color-success)' }}>R{order.totalPrice.toFixed(2)}</span>
            </div>
          </div>

          {error && <p style={{ color: 'var(--color-danger)', marginBottom: 12 }}>{error}</p>}

          {order.state === OrderState.DRAFT && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
              <button onClick={handleCheckout} disabled={loading} className="btn btn-primary btn-full btn-lg">
                {loading ? 'Processing...' : 'Proceed to Payment'}
              </button>
              <button onClick={handleCancel} className="btn btn-danger-outline btn-full">
                Cancel Order
              </button>
            </div>
          )}

          {order.state === OrderState.AWAITING_PAYMENT && (
            <div style={{ textAlign: 'center' }}>
              <div
                className="glass-card"
                style={{
                  padding: 24,
                  marginBottom: 16,
                  background: 'linear-gradient(135deg, var(--color-success-light), var(--bg-glass-strong))',
                }}
              >
                <p style={{ fontSize: '1.1rem', fontWeight: 600, color: 'var(--color-success)' }}>
                  💰 Simulated Payment
                </p>
                <p style={{ color: 'var(--text-muted)', marginTop: 4 }}>Total: R{order.totalPrice.toFixed(2)}</p>
              </div>
              <button onClick={handlePay} disabled={paymentLoading} className="btn btn-success btn-full btn-lg">
                {paymentLoading ? 'Processing Payment...' : 'Confirm Payment'}
              </button>
              <button onClick={handleCancel} className="btn btn-danger-outline btn-full" style={{ marginTop: 8 }}>
                Cancel Order
              </button>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
