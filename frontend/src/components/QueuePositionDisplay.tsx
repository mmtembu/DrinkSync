import { useEffect, useRef, useState } from 'react';

interface Props {
  position: number;
}

export function QueuePositionDisplay({ position }: Props) {
  const prevPos = useRef(position);
  const [bouncing, setBouncing] = useState(false);

  useEffect(() => {
    if (prevPos.current !== position) {
      setBouncing(true);
      prevPos.current = position;
      const timer = setTimeout(() => setBouncing(false), 400);
      return () => clearTimeout(timer);
    }
  }, [position]);

  return (
    <div
      aria-label={`Queue position ${position}`}
      className="glass-card"
      style={{
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        padding: '10px 20px',
        background: 'var(--color-primary-light)',
        border: '1px solid var(--border-color)',
      }}
    >
      <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)', fontWeight: 500 }}>Queue Position</span>
      <span
        className={bouncing ? 'number-bounce' : ''}
        style={{
          fontSize: '1.8rem',
          fontWeight: 700,
          color: 'var(--color-primary)',
          lineHeight: 1.2,
        }}
      >
        #{position}
      </span>
    </div>
  );
}
