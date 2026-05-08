import type { CSSProperties } from 'react';

export const styles = {
  page: {
    maxWidth: '480px',
    margin: '0 auto',
    padding: '16px',
  } satisfies CSSProperties,
  heading: {
    fontSize: '1.3rem',
    fontWeight: 700,
    color: 'var(--text-primary)',
    marginBottom: 4,
  } satisfies CSSProperties,
  section: {
    marginBottom: '24px',
    padding: '16px',
    border: '1px solid var(--border-color)',
    borderRadius: 'var(--radius-lg)',
    backgroundColor: 'var(--bg-card)',
    backdropFilter: 'var(--glass-blur)',
    WebkitBackdropFilter: 'var(--glass-blur)',
    boxShadow: 'var(--shadow-sm)',
    transition: 'all var(--transition-normal)',
  } satisfies CSSProperties,
  sectionTitle: {
    fontSize: '1rem',
    fontWeight: 600,
    marginBottom: '12px',
    color: 'var(--text-primary)',
  } satisfies CSSProperties,
  premadeSection: {
    marginBottom: '24px',
  } satisfies CSSProperties,
  premadeList: {
    display: 'flex',
    flexDirection: 'column' as const,
    gap: '8px',
  } satisfies CSSProperties,
  premadeButton: {
    display: 'flex',
    justifyContent: 'space-between',
    padding: '12px',
    border: '1px solid var(--border-color)',
    borderRadius: 'var(--radius-md)',
    backgroundColor: 'var(--bg-card)',
    cursor: 'default',
    textAlign: 'left' as const,
    transition: 'all var(--transition-fast)',
    boxShadow: 'var(--shadow-sm)',
  } satisfies CSSProperties,
  premadeName: {
    fontWeight: 600,
    color: 'var(--text-primary)',
  } satisfies CSSProperties,
  premadeDesc: {
    fontSize: '0.8rem',
    color: 'var(--text-muted)',
  } satisfies CSSProperties,
  premadePrice: {
    fontWeight: 600,
    color: 'var(--color-success)',
  } satisfies CSSProperties,
  emptyText: {
    fontSize: '0.85rem',
    color: 'var(--text-muted)',
    padding: '8px 0',
  } satisfies CSSProperties,
  cartSection: {
    marginBottom: '24px',
    padding: '16px',
    border: '1px solid var(--border-color)',
    borderRadius: 'var(--radius-lg)',
    backgroundColor: 'var(--bg-glass-strong)',
    backdropFilter: 'var(--glass-blur)',
    WebkitBackdropFilter: 'var(--glass-blur)',
    boxShadow: 'var(--shadow-md)',
  } satisfies CSSProperties,
  cartRow: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
    padding: '10px 0',
    borderBottom: '1px solid var(--border-light)',
  } satisfies CSSProperties,
  cartItemName: {
    fontWeight: 600,
    fontSize: '0.9rem',
    color: 'var(--text-primary)',
  } satisfies CSSProperties,
  cartItemPrice: {
    fontSize: '0.8rem',
    color: 'var(--text-muted)',
  } satisfies CSSProperties,
  cartControls: {
    display: 'flex',
    alignItems: 'center',
    gap: '8px',
  } satisfies CSSProperties,
  qtyButton: {
    width: '28px',
    height: '28px',
    borderRadius: 'var(--radius-sm)',
    border: '1px solid var(--border-color)',
    backgroundColor: 'var(--bg-card)',
    color: 'var(--text-primary)',
    cursor: 'pointer',
    transition: 'all var(--transition-fast)',
  } satisfies CSSProperties,
  qtyDisplay: {
    fontWeight: 600,
    minWidth: '20px',
    textAlign: 'center' as const,
    color: 'var(--text-primary)',
  } satisfies CSSProperties,
  removeButton: {
    color: 'var(--color-danger)',
    border: 'none',
    background: 'none',
    cursor: 'pointer',
    fontWeight: 600,
    fontSize: '1rem',
    transition: 'all var(--transition-fast)',
  } satisfies CSSProperties,
  totalRow: {
    display: 'flex',
    justifyContent: 'space-between',
    marginTop: '12px',
    fontWeight: 700,
    fontSize: '1.1rem',
    color: 'var(--text-primary)',
  } satisfies CSSProperties,
  errorText: {
    color: 'var(--color-danger)',
    marginBottom: '12px',
  } satisfies CSSProperties,
};

const addDrinkButtonBase: CSSProperties = {
  marginTop: '12px',
  width: '100%',
  padding: '10px',
  borderRadius: 'var(--radius-md)',
  border: 'none',
  color: 'var(--text-inverse)',
  fontWeight: 600,
  transition: 'all var(--transition-fast)',
};

export const addDrinkButtonStyles = {
  enabled: {
    ...addDrinkButtonBase,
    backgroundColor: 'var(--color-primary)',
    cursor: 'pointer',
    boxShadow: 'var(--shadow-sm)',
  } satisfies CSSProperties,
  disabled: {
    ...addDrinkButtonBase,
    backgroundColor: 'var(--bg-tertiary)',
    color: 'var(--text-muted)',
    cursor: 'not-allowed',
  } satisfies CSSProperties,
};

const checkoutButtonBase: CSSProperties = {
  width: '100%',
  padding: '14px',
  borderRadius: 'var(--radius-lg)',
  border: 'none',
  color: 'var(--text-inverse)',
  fontWeight: 700,
  fontSize: '1rem',
  transition: 'all var(--transition-fast)',
};

export const checkoutButtonStyles = {
  enabled: {
    ...checkoutButtonBase,
    backgroundColor: 'var(--color-success)',
    cursor: 'pointer',
    boxShadow: 'var(--shadow-md)',
  } satisfies CSSProperties,
  disabled: {
    ...checkoutButtonBase,
    backgroundColor: 'var(--bg-tertiary)',
    color: 'var(--text-muted)',
    cursor: 'not-allowed',
  } satisfies CSSProperties,
};
