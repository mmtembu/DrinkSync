import { apiClient } from './apiClient';
import type { Station } from '../types/station';
import type { Menu } from '../types/menu';

export const stationApi = {
  getStation: (stationId: number) =>
    apiClient.get<Station>(`/api/stations/${stationId}`),

  getMenu: (stationId: number) =>
    apiClient.get<Menu>(`/api/stations/${stationId}/menu`),

  setCupPrice: (stationId: number, cupPrice: number, token: string) =>
    apiClient.put<void>(`/api/stations/${stationId}/cup-price`, { cupPrice }, { token }),

  setPickupWindow: (stationId: number, pickupWindowMinutes: number, token: string) =>
    apiClient.put<void>(`/api/stations/${stationId}/pickup-window`, { pickupWindowMinutes }, { token }),
};
