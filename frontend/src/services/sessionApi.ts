import { apiClient } from './apiClient';
import type { Session } from '../types/session';
import type { Order } from '../types/order';

export const sessionApi = {
  createSession: (stationId: number) =>
    apiClient.post<Session>('/api/sessions', { stationId }),

  getSession: (sessionId: string) =>
    apiClient.get<Session>(`/api/sessions/${sessionId}`),

  getSessionOrders: (sessionId: string) =>
    apiClient.get<Order[]>(`/api/sessions/${sessionId}/orders`),
};
