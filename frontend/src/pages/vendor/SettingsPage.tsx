import { useAuth } from '../../hooks/useAuth';
import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { stationApi } from '../../services/stationApi';
import type { Station } from '../../types/station';

export function SettingsPage() {
  const { token, stationId, isAuthenticated } = useAuth();
  const navigate = useNavigate();
  const [station, setStation] = useState<Station | null>(null);
  const [cupPrice, setCupPrice] = useState('');
  const [pickupWindow, setPickupWindow] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  useEffect(() => {
    if (!isAuthenticated) navigate('/vendor/login');
  }, [isAuthenticated, navigate]);

  useEffect(() => {
    if (stationId) {
      stationApi.getStation(stationId).then((s) => {
        setStation(s);
        setCupPrice(s.cupPrice.toString());
        setPickupWindow(s.pickupWindowMinutes.toString());
      });
    }
  }, [stationId]);

  const handleSaveCupPrice = async () => {
    if (!token || !stationId) return;
    setError(null);
    setSuccess(null);
    const price = parseFloat(cupPrice);
    if (isNaN(price) || price < 0) { setError('Cup price must be zero or greater'); return; }
    try {
      await stationApi.setCupPrice(stationId, price, token);
      setSuccess('Cup price updated');
    } catch (e: any) {
      setError(e.body?.message || e.message);
    }
  };

  const handleSavePickupWindow = async () => {
    if (!token || !stationId) return;
    setError(null);
    setSuccess(null);
    const minutes = parseInt(pickupWindow, 10);
    if (isNaN(minutes) || minutes < 5 || minutes > 30) { setError('Pickup window must be between 5 and 30 minutes'); return; }
    try {
      await stationApi.setPickupWindow(stationId, minutes, token);
      setSuccess('Pickup window updated');
    } catch (e: any) {
      setError(e.body?.message || e.message);
    }
  };

  return (
    <div style={{ maxWidth: 500, margin: '0 auto', padding: 16 }} className="page-fade-in">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 24 }}>
        <h1 style={{ fontSize: '1.3rem', fontWeight: 700, color: 'var(--text-primary)' }}>⚙️ Station Settings</h1>
        <button onClick={() => navigate('/vendor/dashboard')} className="btn btn-ghost">Back</button>
      </div>

      {station && (
        <p style={{ color: 'var(--text-muted)', marginBottom: 16 }}>Station: {station.name}</p>
      )}

      {error && (
        <div className="toast" style={{ color: 'var(--color-danger)', marginBottom: 12, padding: '8px 12px', backgroundColor: 'var(--color-danger-light)', borderRadius: 'var(--radius-md)' }}>
          {error}
        </div>
      )}
      {success && (
        <div className="toast" style={{ color: 'var(--color-success)', marginBottom: 12, padding: '8px 12px', backgroundColor: 'var(--color-success-light)', borderRadius: 'var(--radius-md)' }}>
          ✓ {success}
        </div>
      )}

      <div className="glass-card card-fade-in" style={{ padding: 16, marginBottom: 16 }}>
        <label htmlFor="cupPrice" style={{ fontWeight: 600, display: 'block', marginBottom: 6, color: 'var(--text-primary)' }}>
          New Cup Price (R)
        </label>
        <div style={{ display: 'flex', gap: 8 }}>
          <input
            id="cupPrice"
            type="number"
            step="0.01"
            min="0"
            value={cupPrice}
            onChange={(e) => setCupPrice(e.target.value)}
            className="input"
            style={{ flex: 1 }}
          />
          <button onClick={handleSaveCupPrice} className="btn btn-primary">Save</button>
        </div>
      </div>

      <div className="glass-card card-fade-in" style={{ padding: 16, animationDelay: '0.1s' }}>
        <label htmlFor="pickupWindow" style={{ fontWeight: 600, display: 'block', marginBottom: 6, color: 'var(--text-primary)' }}>
          Pickup Window (minutes, 5-30)
        </label>
        <div style={{ display: 'flex', gap: 8 }}>
          <input
            id="pickupWindow"
            type="number"
            min="5"
            max="30"
            value={pickupWindow}
            onChange={(e) => setPickupWindow(e.target.value)}
            className="input"
            style={{ flex: 1 }}
          />
          <button onClick={handleSavePickupWindow} className="btn btn-primary">Save</button>
        </div>
      </div>
    </div>
  );
}
