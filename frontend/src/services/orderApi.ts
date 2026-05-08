import { apiClient } from './apiClient';
import type { Order, OrderItemRequest, OrderState } from '../types/order';

export const orderApi = {
  createOrder: (stationId: number, items: OrderItemRequest[], sessionId: string) =>
    apiClient.post<Order>(`/api/stations/${stationId}/orders`, items, { sessionId }),

  getOrder: (orderId: number, sessionId?: string) =>
    apiClient.get<Order>(`/api/orders/${orderId}`, { sessionId }),

  updateOrderItems: (orderId: number, items: OrderItemRequest[], sessionId: string) =>
    apiClient.put<Order>(`/api/orders/${orderId}/items`, items, { sessionId }),

  checkout: (orderId: number, sessionId: string) =>
    apiClient.post<Order>(`/api/orders/${orderId}/checkout`, undefined, { sessionId }),

  pay: (orderId: number, idempotencyKey: string, sessionId: string) =>
    apiClient.post<Order>(`/api/orders/${orderId}/pay`, undefined, {
      sessionId,
      headers: { 'Idempotency-Key': idempotencyKey },
    }),

  cancel: (orderId: number, sessionId: string) =>
    apiClient.post<Order>(`/api/orders/${orderId}/cancel`, undefined, { sessionId }),

  transitionState: (orderId: number, targetState: OrderState, token: string) =>
    apiClient.patch<Order>(`/api/orders/${orderId}/state`, { targetState }, { token }),

  getStationOrders: (stationId: number, token: string, state?: OrderState) => {
    const query = state ? `?state=${state}` : '';
    return apiClient.get<Order[]>(`/api/stations/${stationId}/orders${query}`, { token });
  },

  getSessionOrders: (sessionId: string) =>
    apiClient.get<Order[]>(`/api/sessions/${sessionId}/orders`),
};
