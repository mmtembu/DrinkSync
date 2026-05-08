import { CupOption } from '../types/order';

interface Props {
  selected: CupOption | null;
  cupPrice: number;
  onSelect: (option: CupOption | null) => void;
}

export function CupOptionSelector({ selected, cupPrice, onSelect }: Props) {
  const optionStyle = (isSelected: boolean): React.CSSProperties => ({
    flex: 1,
    padding: 12,
    border: `2px solid ${isSelected ? 'var(--color-primary)' : 'var(--border-color)'}`,
    borderRadius: 'var(--radius-md)',
    backgroundColor: isSelected ? 'var(--color-primary-light)' : 'var(--bg-card)',
    cursor: 'pointer',
    transition: 'all var(--transition-fast)',
    textAlign: 'center',
  });

  return (
    <div role="radiogroup" aria-label="Cup option" style={{ display: 'flex', gap: 8, marginBottom: 12 }}>
      <button
        role="radio"
        aria-checked={selected === CupOption.REUSE_CUP}
        onClick={() => onSelect(selected === CupOption.REUSE_CUP ? null : CupOption.REUSE_CUP)}
        style={optionStyle(selected === CupOption.REUSE_CUP)}
      >
        <div style={{ fontWeight: 600, color: 'var(--text-primary)' }}>♻️ Reuse Cup</div>
        <div style={{ fontSize: '0.85rem', color: 'var(--color-success)', fontWeight: 500 }}>Free</div>
      </button>
      <button
        role="radio"
        aria-checked={selected === CupOption.NEW_CUP}
        onClick={() => onSelect(selected === CupOption.NEW_CUP ? null : CupOption.NEW_CUP)}
        style={optionStyle(selected === CupOption.NEW_CUP)}
      >
        <div style={{ fontWeight: 600, color: 'var(--text-primary)' }}>🥤 New Cup</div>
        <div style={{ fontSize: '0.85rem', color: 'var(--color-success)', fontWeight: 500 }}>R{cupPrice.toFixed(2)}</div>
      </button>
    </div>
  );
}
