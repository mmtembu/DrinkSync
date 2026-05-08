import type { CSSProperties } from 'react';

interface SelectableItem {
  id: number;
  name: string;
  available: boolean;
}

interface Props<T extends SelectableItem> {
  label: string;
  items: T[];
  selected: T | null;
  onSelect: (item: T | null) => void;
  formatLabel: (item: T) => string;
  emptyMessage?: string;
}

const styles = {
  container: {
    marginBottom: '12px',
  } satisfies CSSProperties,
  label: {
    fontWeight: 600,
    fontSize: '0.85rem',
  } satisfies CSSProperties,
  buttonGroup: {
    display: 'flex',
    flexWrap: 'wrap',
    gap: '6px',
    marginTop: '4px',
  } satisfies CSSProperties,
  emptyText: {
    fontSize: '0.85rem',
    color: '#6b7280',
    marginTop: '4px',
  } satisfies CSSProperties,
};

function getButtonStyle(isSelected: boolean): CSSProperties {
  return {
    padding: '6px 12px',
    borderRadius: '6px',
    border: `2px solid ${isSelected ? '#3b82f6' : '#e5e7eb'}`,
    backgroundColor: isSelected ? '#eff6ff' : '#fff',
    cursor: 'pointer',
    fontSize: '0.85rem',
    fontWeight: isSelected ? 600 : 400,
  };
}

export function SelectionButtonGroup<T extends SelectableItem>({
  label,
  items,
  selected,
  onSelect,
  formatLabel,
  emptyMessage = 'None available',
}: Props<T>) {
  const availableItems = items.filter((item) => item.available);

  return (
    <div style={styles.container}>
      <label style={styles.label}>{label}</label>
      {availableItems.length === 0 ? (
        <p style={styles.emptyText}>{emptyMessage}</p>
      ) : (
        <div style={styles.buttonGroup} role="radiogroup" aria-label={label}>
          {availableItems.map((item) => {
            const isSelected = selected?.id === item.id;
            return (
              <button
                key={item.id}
                role="radio"
                aria-checked={isSelected}
                onClick={() => onSelect(isSelected ? null : item)}
                style={getButtonStyle(isSelected)}
              >
                {formatLabel(item)}
              </button>
            );
          })}
        </div>
      )}
    </div>
  );
}
