import { useNavigate } from 'react-router-dom';

interface Props {
  lockedStationId?: number;
}

export function StationLockedPage({ lockedStationId }: Props) {
  const navigate = useNavigate();

  return (
    <div className="page-container" style={{ paddingTop: 48, textAlign: 'center' }}>
      <div className="card-fade-in glass-card" style={{ padding: '32px 24px' }}>
        <div style={{ fontSize: '3.5rem', marginBottom: 16 }}>🔒</div>
        <h1 style={{ fontSize: '1.5rem', fontWeight: 700, marginBottom: 8, color: 'var(--text-primary)' }}>
          Station Locked
        </h1>
        <p style={{ color: 'var(--text-muted)', marginBottom: 24, lineHeight: 1.6 }}>
          You have active orders at another station. Please collect or cancel all orders at your current station before ordering from a different one.
        </p>
        {lockedStationId && (
          <button
            onClick={() => navigate(`/station/${lockedStationId}/tracking`)}
            className="btn btn-primary btn-lg"
          >
            Go to Current Station
          </button>
        )}
      </div>
    </div>
  );
}
