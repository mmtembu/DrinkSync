import { Client, type IMessage } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

// SockJS requires http/https, not ws/wss
const RAW_URL = import.meta.env.VITE_WS_URL || 'http://localhost:8080/ws';
const WS_URL = RAW_URL.replace(/^ws:/, 'http:').replace(/^wss:/, 'https:');

let stompClient: Client | null = null;
let connectPromise: Promise<Client> | null = null;

function getConnectedClient(): Promise<Client> {
  if (stompClient?.connected) {
    return Promise.resolve(stompClient);
  }

  if (connectPromise) {
    return connectPromise;
  }

  connectPromise = new Promise<Client>((resolve, reject) => {
    const client = new Client({
      webSocketFactory: () => new SockJS(WS_URL) as unknown as WebSocket,
      reconnectDelay: 2000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      onConnect: () => {
        console.log('WebSocket connected');
        resolve(client);
      },
      onDisconnect: () => {
        console.log('WebSocket disconnected');
        connectPromise = null;
      },
      onStompError: (frame) => {
        console.error('STOMP error:', frame.headers['message']);
        connectPromise = null;
        reject(new Error(frame.headers['message']));
      },
      onWebSocketClose: () => {
        connectPromise = null;
      },
    });

    stompClient = client;
    client.activate();
  });

  return connectPromise;
}

export function connectWebSocket(onConnect?: () => void): Client {
  getConnectedClient().then(() => onConnect?.());
  return stompClient!;
}

export function subscribeToOrderUpdates(
  orderId: number,
  callback: (data: unknown) => void
): { unsubscribe: () => void } {
  const topic = `/topic/orders/${orderId}`;
  let subscription: { unsubscribe: () => void } | null = null;

  getConnectedClient().then((client) => {
    subscription = client.subscribe(topic, (message: IMessage) => {
      try {
        callback(JSON.parse(message.body));
      } catch (e) {
        console.error('Failed to parse order update:', e);
      }
    });
  });

  return {
    unsubscribe: () => subscription?.unsubscribe(),
  };
}

export function subscribeToStationOrders(
  stationId: number,
  callback: (data: unknown) => void
): { unsubscribe: () => void } {
  const topic = `/topic/stations/${stationId}/orders`;
  let subscription: { unsubscribe: () => void } | null = null;

  getConnectedClient().then((client) => {
    subscription = client.subscribe(topic, (message: IMessage) => {
      try {
        callback(JSON.parse(message.body));
      } catch (e) {
        console.error('Failed to parse station order update:', e);
      }
    });
  });

  return {
    unsubscribe: () => subscription?.unsubscribe(),
  };
}

export function disconnectWebSocket(): void {
  if (stompClient) {
    stompClient.deactivate();
    stompClient = null;
    connectPromise = null;
  }
}
