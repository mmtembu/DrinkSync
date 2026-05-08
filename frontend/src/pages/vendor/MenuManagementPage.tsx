import { useAuth } from '../../hooks/useAuth';
import { useStationMenu } from '../../hooks/useStationMenu';
import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { menuApi } from '../../services/menuApi';

export function MenuManagementPage() {
  const { token, stationId, isAuthenticated } = useAuth();
  const navigate = useNavigate();
  const { menu, refetch } = useStationMenu(stationId ?? undefined);

  const [newItemName, setNewItemName] = useState('');
  const [newItemPrice, setNewItemPrice] = useState('');
  const [newItemDesc, setNewItemDesc] = useState('');
  const [activeTab, setActiveTab] = useState<'spirits' | 'mixers' | 'premades'>('spirits');
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!isAuthenticated) navigate('/vendor/login');
  }, [isAuthenticated, navigate]);

  const handleCreate = async () => {
    if (!token || !stationId || !newItemName || !newItemPrice) return;
    setError(null);
    const price = parseFloat(newItemPrice);
    if (isNaN(price) || price <= 0) { setError('Price must be greater than zero'); return; }

    try {
      if (activeTab === 'spirits') {
        await menuApi.createSpirit(stationId, { name: newItemName, price }, token);
      } else if (activeTab === 'mixers') {
        await menuApi.createMixer(stationId, { name: newItemName, price }, token);
      } else {
        if (!newItemDesc) { setError('Description is required for premade items'); return; }
        await menuApi.createPremade(stationId, { name: newItemName, description: newItemDesc, price }, token);
      }
      setNewItemName('');
      setNewItemPrice('');
      setNewItemDesc('');
      refetch();
    } catch (e: any) {
      setError(e.body?.message || e.message);
    }
  };

  const handleToggle = async (type: string, itemId: number) => {
    if (!token || !stationId) return;
    try {
      if (type === 'spirits') await menuApi.toggleSpiritAvailability(stationId, itemId, token);
      else if (type === 'mixers') await menuApi.toggleMixerAvailability(stationId, itemId, token);
      else await menuApi.togglePremadeAvailability(stationId, itemId, token);
      refetch();
    } catch { /* ignore */ }
  };

  const handleDelete = async (type: string, itemId: number) => {
    if (!token || !stationId) return;
    try {
      if (type === 'spirits') await menuApi.deleteSpirit(stationId, itemId, token);
      else if (type === 'mixers') await menuApi.deleteMixer(stationId, itemId, token);
      else await menuApi.deletePremade(stationId, itemId, token);
      refetch();
    } catch { /* ignore */ }
  };

  const items = activeTab === 'spirits' ? menu?.spirits : activeTab === 'mixers' ? menu?.mixers : menu?.premades;

  return (
    <div style={{ maxWidth: 700, margin: '0 auto', padding: 16 }} className="page-fade-in">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 24 }}>
        <h1 style={{ fontSize: '1.3rem', fontWeight: 700, color: 'var(--text-primary)' }}>📝 Menu Management</h1>
        <button onClick={() => navigate('/vendor/dashboard')} className="btn btn-ghost">Back</button>
      </div>

      {/* Tab bar */}
      <div style={{ display: 'flex', gap: 8, marginBottom: 16 }}>
        {(['spirits', 'mixers', 'premades'] as const).map((tab) => (
          <button
            key={tab}
            onClick={() => setActiveTab(tab)}
            className="btn"
            style={{
              padding: '8px 16px',
              borderRadius: 'var(--radius-md)',
              border: `2px solid ${activeTab === tab ? 'var(--color-primary)' : 'var(--border-color)'}`,
              backgroundColor: activeTab === tab ? 'var(--color-primary-light)' : 'var(--bg-card)',
              color: activeTab === tab ? 'var(--color-primary)' : 'var(--text-secondary)',
              fontWeight: 600,
              textTransform: 'capitalize',
              transition: 'all var(--transition-fast)',
            }}
          >
            {tab}
          </button>
        ))}
      </div>

      {/* Add new item form */}
      <div className="glass-card" style={{ padding: 16, marginBottom: 16 }}>
        <h3 style={{ fontSize: '0.9rem', fontWeight: 600, marginBottom: 10, color: 'var(--text-primary)' }}>
          Add New {activeTab.slice(0, -1)}
        </h3>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
          <input
            value={newItemName}
            onChange={(e) => setNewItemName(e.target.value)}
            placeholder="Name"
            className="input"
          />
          <input
            value={newItemPrice}
            onChange={(e) => setNewItemPrice(e.target.value)}
            placeholder="Price"
            type="number"
            step="0.01"
            className="input"
          />
          {activeTab === 'premades' && (
            <input
              value={newItemDesc}
              onChange={(e) => setNewItemDesc(e.target.value)}
              placeholder="Description"
              className="input"
            />
          )}
          {error && <p style={{ color: 'var(--color-danger)', fontSize: '0.85rem' }}>{error}</p>}
          <button onClick={handleCreate} className="btn btn-success btn-full">
            Add Item
          </button>
        </div>
      </div>

      {/* Item list */}
      <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
        {items?.map((item, index) => (
          <div
            key={item.id}
            className="card card-fade-in"
            style={{
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              padding: 12,
              opacity: item.available ? 1 : 0.5,
              animationDelay: `${index * 0.04}s`,
            }}
          >
            <div>
              <span style={{ fontWeight: 600, color: 'var(--text-primary)' }}>{item.name}</span>
              {'description' in item && (
                <span style={{ color: 'var(--text-muted)', marginLeft: 8, fontSize: '0.85rem' }}>
                  {(item as any).description}
                </span>
              )}
              <span style={{ color: 'var(--color-success)', marginLeft: 8, fontWeight: 500 }}>
                R{item.price.toFixed(2)}
              </span>
            </div>
            <div style={{ display: 'flex', gap: 6 }}>
              <button
                onClick={() => handleToggle(activeTab, item.id)}
                className="btn btn-ghost"
                style={{ padding: '6px 12px', fontSize: '0.8rem' }}
              >
                {item.available ? 'Disable' : 'Enable'}
              </button>
              <button
                onClick={() => handleDelete(activeTab, item.id)}
                className="btn btn-danger-outline"
                style={{ padding: '6px 12px', fontSize: '0.8rem' }}
              >
                Delete
              </button>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
