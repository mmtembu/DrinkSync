import { useState, useEffect } from 'react';

interface Props {
  pickupWindowStart: string;
  pickupWindowMinutes: number;
}

export function PickupTimer({ pickupWindowStart, pickupWindowMinutes }: Props) {
  const [remaining, setRemaining] = useState('');
  const [urgency, setUrgency] = useState<'ok' | 'warning' | 'danger'>('ok');

  useEffect(() => {
    const update = () => {
      const start = new Date(pickupWindowStart).getTime();
      const expiresAt = start + pickupWindowMinutes * 60 * 1000;
      const now = Date.now();
      const diff = expiresAt - now;

      if (diff <= 0) {
        setRemaining('Overdue');
        setUrgency('danger');
      } else {
        const minutes = Math.floor(diff / 60000);
        const seconds = Math.floor((diff % 60000) / 1000);
        setRemaining(`${minutes}:${seconds.toString().padStart(2, '0')}`);

        const totalMs = pickupWindowMinutes * 60 * 1000;
        const pct = diff / totalMs;
        if (pct < 0.25) setUrgency('danger');
        else if (pct < 0.5) setUrgency('warning');
        else setUrgency('ok');
      }
    };

    update();
    const interval = setInterval(update, 1000);
    return () => clearInterval(interval);
  }, [pickupWindowStart, pickupWindowMinutes]);

  const colorMap = {
    ok: { color: 'var(--color-success)', bg: 'var(--color-success-light)' },
    warning: { color: '#d97706', bg: 'var(--color-warning-light)' },
    danger: { color: 'var(--color-danger)', bg: 'var(--color-danger-light)' },
  };

  const { color, bg } = colorMap[urgency];

  return (
    <div
      aria-label={`Pickup time remaining: ${remaining}`}
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: 4,
        padding: '4px 10px',
        borderRadius: 'var(--radius-sm)',
        fontSize: '0.85rem',
        fontWeight: 600,
        color,
        backgroundColor: bg,
        transition: 'all var(--transition-normal)',
      }}
    >
      ⏱ {remaining}
    </div>
  );
}
