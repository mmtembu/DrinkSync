import { useOnlineStatus } from '../hooks/useOnlineStatus';

export function ConnectionStatus() {
  const isOnline = useOnlineStatus();

  if (isOnline) return null;

  return (
    <div
      role="alert"
      className="connection-banner"
      style={{
        backgroundColor: 'var(--color-warning-light)',
        color: '#92400e',
      }}
    >
      ⚠️ You are offline. Some features may be unavailable.
    </div>
  );
}
