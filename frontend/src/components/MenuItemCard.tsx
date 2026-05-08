interface Props {
  name: string;
  price: number;
  description?: string;
  available: boolean;
  onSelect?: () => void;
}

export function MenuItemCard({ name, price, description, available, onSelect }: Props) {
  return (
    <button
      onClick={onSelect}
      disabled={!available}
      aria-label={`${name} - R${price.toFixed(2)}${!available ? ' (unavailable)' : ''}`}
      className="card"
      style={{
        display: 'flex',
        flexDirection: 'column',
        padding: 12,
        width: '100%',
        border: '1px solid var(--border-color)',
        cursor: available ? 'pointer' : 'not-allowed',
        opacity: available ? 1 : 0.5,
        textAlign: 'left',
        backgroundColor: available ? 'var(--bg-card)' : 'var(--bg-tertiary)',
        transition: 'all var(--transition-fast)',
      }}
    >
      <div style={{ display: 'flex', justifyContent: 'space-between', width: '100%' }}>
        <span style={{ fontWeight: 600, color: 'var(--text-primary)' }}>{name}</span>
        <span style={{ color: 'var(--color-success)', fontWeight: 600 }}>R{price.toFixed(2)}</span>
      </div>
      {description && (
        <span style={{ fontSize: '0.85rem', color: 'var(--text-muted)', marginTop: 4 }}>{description}</span>
      )}
      {!available && (
        <span style={{ fontSize: '0.75rem', color: 'var(--color-danger)', marginTop: 4 }}>Unavailable</span>
      )}
    </button>
  );
}
