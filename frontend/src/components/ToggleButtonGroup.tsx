import { useState } from 'react';

interface SelectableItem {
  id: number;
  name: string;
  available: boolean;
}

export interface SelectedItemWithQty<T> {
  item: T;
  quantity: number;
}

interface Props<T extends SelectableItem> {
  label: string;
  items: T[];
  selected: SelectedItemWithQty<T>[];
  onSelectionChange: (items: SelectedItemWithQty<T>[]) => void;
  formatLabel: (item: T) => string;
  emptyMessage?: string;
}

export function ToggleButtonGroup<T extends SelectableItem>({
  label,
  items,
  selected,
  onSelectionChange,
  formatLabel,
  emptyMessage = 'None available',
}: Props<T>) {
  const availableItems = items.filter((item) => item.available);
  const [bouncingId, setBouncingId] = useState<number | null>(null);

  const getEntry = (item: T) => selected.find((s) => s.item.id === item.id);

  const handleSelect = (item: T) => {
    onSelectionChange([...selected, { item, quantity: 1 }]);
  };

  const triggerBounce = (id: number) => {
    setBouncingId(id);
    setTimeout(() => setBouncingId(null), 300);
  };

  const handleIncrement = (item: T, e: React.MouseEvent) => {
    e.stopPropagation();
    triggerBounce(item.id);
    onSelectionChange(
      selected.map((s) => (s.item.id === item.id ? { ...s, quantity: s.quantity + 1 } : s)),
    );
  };

  const handleDecrement = (item: T, e: React.MouseEvent) => {
    e.stopPropagation();
    const entry = getEntry(item);
    if (!entry) return;
    triggerBounce(item.id);
    if (entry.quantity <= 1) {
      onSelectionChange(selected.filter((s) => s.item.id !== item.id));
    } else {
      onSelectionChange(
        selected.map((s) => (s.item.id === item.id ? { ...s, quantity: s.quantity - 1 } : s)),
      );
    }
  };

  const handleDeselect = (item: T) => {
    onSelectionChange(selected.filter((s) => s.item.id !== item.id));
  };

  const totalSelected = selected.reduce((sum, s) => sum + s.quantity, 0);

  return (
    <div style={{ marginBottom: 12 }}>
      <label style={{ fontWeight: 600, fontSize: '0.85rem', display: 'block', marginBottom: 6, color: 'var(--text-primary)' }}>
        {label}
        {totalSelected > 0 && (
          <span style={{ fontWeight: 400, color: 'var(--text-muted)', marginLeft: 6, fontSize: '0.8rem' }}>
            ({totalSelected} selected)
          </span>
        )}
      </label>
      {availableItems.length === 0 ? (
        <p style={{ fontSize: '0.85rem', color: 'var(--text-muted)', marginTop: 4 }}>{emptyMessage}</p>
      ) : (
        <div
          style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(140px, 1fr))', gap: 8 }}
          role="group"
          aria-label={`${label} — tap to select, use +/− for quantity`}
        >
          {availableItems.map((item) => {
            const entry = getEntry(item);
            const isSelected = !!entry;

            if (!isSelected) {
              return (
                <button
                  key={item.id}
                  type="button"
                  onClick={() => handleSelect(item)}
                  style={{
                    padding: '10px 12px',
                    borderRadius: 'var(--radius-md)',
                    border: '2px solid var(--border-color)',
                    backgroundColor: 'var(--bg-card)',
                    color: 'var(--text-secondary)',
                    cursor: 'pointer',
                    fontSize: '0.85rem',
                    fontWeight: 400,
                    textAlign: 'center',
                    transition: 'all var(--transition-fast)',
                    width: '100%',
                  }}
                  aria-label={`Select ${item.name}`}
                >
                  {formatLabel(item)}
                </button>
              );
            }

            return (
              <div
                key={item.id}
                className="slide-in-right"
                style={{
                  display: 'flex',
                  flexDirection: 'column',
                  alignItems: 'center',
                  gap: 6,
                  padding: '8px 10px',
                  borderRadius: 'var(--radius-md)',
                  border: '2px solid var(--color-primary)',
                  backgroundColor: 'var(--color-primary-light)',
                  fontSize: '0.85rem',
                  fontWeight: 600,
                  color: 'var(--color-primary)',
                  transition: 'all var(--transition-fast)',
                  width: '100%',
                  boxSizing: 'border-box',
                }}
              >
                <button
                  type="button"
                  onClick={() => handleDeselect(item)}
                  style={{
                    background: 'none',
                    border: 'none',
                    cursor: 'pointer',
                    color: 'var(--color-primary)',
                    fontWeight: 600,
                    fontSize: '0.85rem',
                    padding: 0,
                    textAlign: 'center',
                    width: '100%',
                  }}
                  aria-label={`Deselect ${item.name}`}
                >
                  ✕ {formatLabel(item)}
                </button>
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8 }}>
                  <button
                    type="button"
                    onClick={(e) => handleDecrement(item, e)}
                    className="btn"
                    style={{
                      width: 26,
                      height: 26,
                      borderRadius: '50%',
                      padding: 0,
                      backgroundColor: 'var(--color-primary-light)',
                      color: 'var(--color-primary)',
                      fontSize: '0.9rem',
                      fontWeight: 700,
                      border: '1px solid var(--color-primary)',
                    }}
                    aria-label={`Decrease ${item.name} quantity`}
                  >
                    −
                  </button>
                  <span
                    className={bouncingId === item.id ? 'number-bounce' : ''}
                    style={{ minWidth: 18, textAlign: 'center', fontWeight: 700 }}
                  >
                    {entry.quantity}
                  </span>
                  <button
                    type="button"
                    onClick={(e) => handleIncrement(item, e)}
                    className="btn"
                    style={{
                      width: 26,
                      height: 26,
                      borderRadius: '50%',
                      padding: 0,
                      backgroundColor: 'var(--color-primary)',
                      color: 'var(--text-inverse)',
                      fontSize: '0.9rem',
                      fontWeight: 700,
                    }}
                    aria-label={`Increase ${item.name} quantity`}
                  >
                    +
                  </button>
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
