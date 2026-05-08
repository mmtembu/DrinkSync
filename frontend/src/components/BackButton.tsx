import { useNavigate } from 'react-router-dom';

interface Props {
  /** Override the default browser back behavior with a specific path */
  to?: string;
  label?: string;
}

export function BackButton({ to, label = 'Back' }: Props) {
  const navigate = useNavigate();

  const handleClick = () => {
    if (to) {
      navigate(to);
    } else {
      navigate(-1);
    }
  };

  return (
    <button
      onClick={handleClick}
      aria-label={label}
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: 4,
        padding: '6px 12px',
        borderRadius: 'var(--radius-md)',
        border: '1px solid var(--border-color)',
        backgroundColor: 'var(--bg-card)',
        color: 'var(--text-secondary)',
        cursor: 'pointer',
        fontSize: '0.85rem',
        fontWeight: 500,
        transition: 'all var(--transition-fast)',
        marginBottom: 12,
      }}
      className="btn"
    >
      ← {label}
    </button>
  );
}
