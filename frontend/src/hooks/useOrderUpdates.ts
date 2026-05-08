import { useEffect, useState } from 'react';
import { subscribeToOrderUpdates, subscribeToStationOrders } from '../services/websocketClient';
import type { Order } from '../types/order';

export function useOrderUpdates(orderId?: number) {
  const [latestUpdate, setLatestUpdate] = useState<Order | null>(null);

  useEffect(() => {
    if (!orderId) return;

    const sub = subscribeToOrderUpdates(orderId, (data) => {
      setLatestUpdate(data as Order);
    });

    return () => sub.unsubscribe();
  }, [orderId]);

  return latestUpdate;
}

export function useStationOrderUpdates(stationId?: number) {
  const [latestUpdate, setLatestUpdate] = useState<Order | null>(null);

  useEffect(() => {
    if (!stationId) return;

    const sub = subscribeToStationOrders(stationId, (data) => {
      setLatestUpdate(data as Order);
    });

    return () => sub.unsubscribe();
  }, [stationId]);

  return latestUpdate;
}
