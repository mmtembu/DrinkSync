import { useEffect, useRef, useState } from 'react';
import { connectWebSocket, disconnectWebSocket } from '../services/websocketClient';
import type { Client } from '@stomp/stompjs';

export function useWebSocket() {
  const [connected, setConnected] = useState(false);
  const clientRef = useRef<Client | null>(null);

  useEffect(() => {
    const client = connectWebSocket(() => {
      setConnected(true);
    });
    clientRef.current = client;

    return () => {
      disconnectWebSocket();
      setConnected(false);
    };
  }, []);

  return { connected, client: clientRef.current };
}
