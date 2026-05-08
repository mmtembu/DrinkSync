import { useParams, useNavigate } from 'react-router-dom';
import { useStationMenu } from '../../hooks/useStationMenu';
import { useSession } from '../../hooks/useSession';
import { MenuItemCard } from '../../components/MenuItemCard';
import { MenuPageSkeleton } from '../../components/Skeleton';
import { FloatingDraftOrders } from '../../components/FloatingDraftOrders';
import { useState, useEffect } from 'react';
import { stationApi } from '../../services/stationApi';
import type { Station } from '../../types/station';

export function StationMenuPage() {
  const { stationId } = useParams<{ stationId: string }>();
  const stId = stationId ? parseInt(stationId, 10) : undefined;
  const navigate = useNavigate();
  const { menu, loading, error, refetch } = useStationMenu(stId);
  const { ensureSession, sessionId } = useSession(stId);
  const [station, setStation] = useState<Station | null>(null);

  useEffect(() => {
    if (stId) {
      stationApi.getStation(stId).then(setStation).catch(() => {});
    }
  }, [stId]);

  if (!stId || isNaN(stId)) {
    return (
      <div className="page-container" style={{ textAlign: 'center', paddingTop: 48 }}>
        <div style={{ fontSize: '3rem', marginBottom: 16 }}>🔍</div>
        <h1 style={{ color: 'var(--text-primary)' }}>Station Not Found</h1>
        <p style={{ color: 'var(--text-muted)' }}>The QR code you scanned contains an invalid station identifier.</p>
      </div>
    );
  }

  const handleStartOrder = async () => {
    const sid = await ensureSession(stId);
    if (sid) {
      navigate(`/station/${stId}/order`);
    }
  };

  return (
    <div className="page-container">
      {station && (
        <header className="card-fade-in" style={{ marginBottom: 24 }}>
          <div
            className="glass-card"
            style={{
              padding: '20px 16px',
              marginBottom: 12,
              background: 'linear-gradient(135deg, var(--color-primary-light), var(--bg-glass-strong))',
            }}
          >
            <h1 style={{ fontSize: '1.5rem', fontWeight: 700, margin: 0, color: 'var(--text-primary)' }}>
              {station.name}
            </h1>
            {station.locationDescription && (
              <p style={{ color: 'var(--text-muted)', margin: '4px 0 0' }}>{station.locationDescription}</p>
            )}
          </div>
          <div
            style={{
              fontSize: '0.85rem',
              color: '#92400e',
              padding: 10,
              backgroundColor: 'var(--color-warning-light)',
              borderRadius: 'var(--radius-md)',
              display: 'flex',
              alignItems: 'center',
              gap: 6,
            }}
          >
            📍 Please stay near this station for order collection
          </div>
        </header>
      )}

      {loading && <MenuPageSkeleton />}

      {error && (
        <div className="card" style={{ padding: 16, backgroundColor: 'var(--color-danger-light)', marginBottom: 16 }}>
          <p style={{ color: 'var(--color-danger)', marginBottom: 8 }}>{error}</p>
          <button onClick={refetch} className="btn btn-ghost">
            Retry
          </button>
        </div>
      )}

      {menu && (
        <div className="card-fade-in">
          {menu.spirits.length > 0 && (
            <section style={{ marginBottom: 24 }}>
              <h2 className="section-title">🥃 Spirits</h2>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                {menu.spirits.map((item) => (
                  <MenuItemCard key={item.id} name={item.name} price={item.price} available={item.available} />
                ))}
              </div>
            </section>
          )}

          {menu.mixers.length > 0 && (
            <section style={{ marginBottom: 24 }}>
              <h2 className="section-title">🧊 Mixers</h2>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                {menu.mixers.map((item) => (
                  <MenuItemCard key={item.id} name={item.name} price={item.price} available={item.available} />
                ))}
              </div>
            </section>
          )}

          {menu.premades.length > 0 && (
            <section style={{ marginBottom: 24 }}>
              <h2 className="section-title">🥫 Ready-to-Serve</h2>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                {menu.premades.map((item) => (
                  <MenuItemCard key={item.id} name={item.name} price={item.price} description={item.description} available={item.available} />
                ))}
              </div>
            </section>
          )}

          <button
            onClick={handleStartOrder}
            className="btn btn-primary btn-full btn-lg"
            style={{ marginTop: 8 }}
          >
            Start Order
          </button>
        </div>
      )}

      {stId && <FloatingDraftOrders stationId={stId} sessionId={sessionId} />}
    </div>
  );
}
