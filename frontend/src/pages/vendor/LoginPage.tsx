import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../hooks/useAuth';

export function LoginPage() {
  const navigate = useNavigate();
  const { login, loading, error } = useAuth();
  const [stationId, setStationId] = useState('');
  const [accessCode, setAccessCode] = useState('');

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const stId = parseInt(stationId, 10);
    if (isNaN(stId)) return;

    const response = await login(stId, accessCode.toUpperCase());
    if (response) {
      navigate(`/vendor/dashboard`);
    }
  };

  const canSubmit = !loading && stationId && accessCode.length === 6;

  return (
    <div style={{ maxWidth: 400, margin: '0 auto', padding: '60px 16px' }}>
      <div className="glass-card card-fade-in" style={{ padding: 32 }}>
        <div style={{ textAlign: 'center', marginBottom: 24 }}>
          <div style={{ fontSize: '2.5rem', marginBottom: 8 }}>🍸</div>
          <h1 style={{ fontSize: '1.5rem', fontWeight: 700, color: 'var(--text-primary)' }}>
            Vendor Login
          </h1>
          <p style={{ color: 'var(--text-muted)', fontSize: '0.9rem', marginTop: 4 }}>
            Sign in to manage your station
          </p>
        </div>

        <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
          <div>
            <label htmlFor="stationId" style={{ display: 'block', fontWeight: 600, marginBottom: 4, fontSize: '0.9rem', color: 'var(--text-secondary)' }}>
              Station ID
            </label>
            <input
              id="stationId"
              type="number"
              value={stationId}
              onChange={(e) => setStationId(e.target.value)}
              placeholder="Enter station ID"
              required
              className="input"
            />
          </div>

          <div>
            <label htmlFor="accessCode" style={{ display: 'block', fontWeight: 600, marginBottom: 4, fontSize: '0.9rem', color: 'var(--text-secondary)' }}>
              Access Code
            </label>
            <input
              id="accessCode"
              type="text"
              value={accessCode}
              onChange={(e) => setAccessCode(e.target.value.toUpperCase())}
              placeholder="6-digit code"
              maxLength={6}
              required
              className="input"
              style={{ fontSize: '1.2rem', letterSpacing: 4, textAlign: 'center', fontFamily: 'monospace' }}
            />
          </div>

          {error && (
            <p style={{ color: 'var(--color-danger)', fontSize: '0.9rem', textAlign: 'center' }}>{error}</p>
          )}

          <button
            type="submit"
            disabled={!canSubmit}
            className="btn btn-primary btn-full btn-lg"
          >
            {loading ? (
              <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
                <span style={{ display: 'inline-block', width: 16, height: 16, border: '2px solid var(--text-inverse)', borderTopColor: 'transparent', borderRadius: '50%', animation: 'spin 0.6s linear infinite' }} />
                Logging in...
              </span>
            ) : (
              'Login'
            )}
          </button>
        </form>
      </div>
    </div>
  );
}
