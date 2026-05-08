import { useAuth } from '../../hooks/useAuth';
import { useStationOrderUpdates } from '../../hooks/useOrderUpdates';
import { OrderStateIndicator } from '../../components/OrderStateIndicator';
import { VendorOrderCard } from '../../components/VendorOrderCard';
import { DashboardSkeleton } from '../../components/Skeleton';
import { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { orderApi } from '../../services/orderApi';
import type { Order } from '../../types/order';
import { OrderState, sortOrdersByStatePriority, filterDashboardOrders } from '../../types/order';

export function DashboardPage() {
  const { token, stationId, isAuthenticated, logout } = useAuth();
  const navigate = useNavigate();
  const [orders, setOrders] = useState<Order[]>([]);
  const [loading, setLoading] = useState(true);
  const latestUpdate = useStationOrderUpdates(stationId ?? undefined);

  const fetchOrders = useCallback(async () => {
    if (!token || !stationId) return;
    try {
      const data = await orderApi.getStationOrders(stationId, token);
      setOrders(sortOrdersByStatePriority(data));
    } catch { /* ignore */ }
    finally { setLoading(false); }
  }, [token, stationId]);

  useEffect(() => { fetchOrders(); }, [fetchOrders]);

  // Refresh when WebSocket update arrives
  useEffect(() => {
    if (latestUpdate) fetchOrders();
  }, [latestUpdate, fetchOrders]);

  // Poll every 10 seconds as fallback
  useEffect(() => {
    const interval = setInterval(fetchOrders, 10000);
    return () => clearInterval(interval);
  }, [fetchOrders]);

  useEffect(() => {
    if (!isAuthenticated) navigate('/vendor/login');
  }, [isAuthenticated, navigate]);

  const handleTransition = async (orderId: number, targetState: OrderState) => {
    if (!token) return;
    try {
      await orderApi.transitionState(orderId, targetState, token);
      fetchOrders();
    } catch { /* ignore */ }
  };

  const { mainOrders, expiredOrders } = filterDashboardOrders(orders);

  if (loading) return <DashboardSkeleton />;

  return (
    <div className="page-container-wide">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 24, flexWrap: 'wrap', gap: 8 }}>
        <h1 style={{ fontSize: '1.5rem', fontWeight: 700, color: 'var(--text-primary)' }}>
          📊 Vendor Dashboard
        </h1>
        <div style={{ display: 'flex', gap: 8 }}>
          <button onClick={() => navigate('/vendor/menu')} className="btn btn-ghost">Menu</button>
          <button onClick={() => navigate('/vendor/settings')} className="btn btn-ghost">Settings</button>
          <button onClick={logout} className="btn btn-danger-outline">Logout</button>
        </div>
      </div>

      {mainOrders.length === 0 && (
        <div className="card" style={{ padding: 32, textAlign: 'center' }}>
          <div style={{ fontSize: '2.5rem', marginBottom: 8 }}>🍹</div>
          <p style={{ color: 'var(--text-muted)', fontSize: '1rem' }}>No active orders. Waiting for customers...</p>
        </div>
      )}

      <div style={{ display: 'grid', gap: 12 }}>
        {mainOrders.map((order, index) => (
          <div key={order.id} style={{ animationDelay: `${index * 0.05}s` }} className="card-fade-in">
            <VendorOrderCard order={order} onTransition={handleTransition} />
          </div>
        ))}
      </div>

      {expiredOrders.length > 0 && (
        <section style={{ marginTop: 32 }}>
          <h2 style={{ fontSize: '1.1rem', fontWeight: 600, color: 'var(--text-muted)', marginBottom: 12 }}>
            Expired Orders
          </h2>
          {expiredOrders.map((order) => (
            <div
              key={order.id}
              className="card"
              style={{ padding: 12, marginBottom: 8, opacity: 0.6 }}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
                <span style={{ fontWeight: 600, color: 'var(--text-primary)' }}>{order.visualOrderNumber || `#${order.id}`}</span>
                <OrderStateIndicator state={order.state} />
                <span style={{ color: 'var(--text-muted)' }}>R{order.totalPrice.toFixed(2)}</span>
              </div>
            </div>
          ))}
        </section>
      )}
    </div>
  );
}
