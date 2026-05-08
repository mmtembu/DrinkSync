import { useState } from 'react';

export function OfflinePage() {
  const [retrying, setRetrying] = useState(false);

  const handleRetry = () => {
    setRetrying(true);
    setTimeout(() => {
      window.location.reload();
    }, 600);
  };

  return (
    <div className="page-container" style={{ paddingTop: 48, textAlign: 'center' }}>
      <div className="card-fade-in glass-card" style={{ padding: '32px 24px' }}>
        <div style={{ fontSize: '3.5rem', marginBottom: 16 }}>📡</div>
        <h1 style={{ fontSize: '1.5rem', fontWeight: 700, marginBottom: 8, color: 'var(--text-primary)' }}>
          No Connection
        </h1>
        <p style={{ color: 'var(--text-muted)', marginBottom: 24, lineHeight: 1.6 }}>
          Please check your internet connection and try again.
        </p>
        <button
          onClick={handleRetry}
          disabled={retrying}
          className="btn btn-primary btn-lg"
          style={retrying ? { opacity: 0.7 } : undefined}
        >
          {retrying ? (
            <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
              <span style={{ display: 'inline-block', width: 16, height: 16, border: '2px solid var(--text-inverse)', borderTopColor: 'transparent', borderRadius: '50%', animation: 'spin 0.6s linear infinite' }} />
              Retrying...
            </span>
          ) : (
            'Retry'
          )}
        </button>
      </div>
    </div>
  );
}
