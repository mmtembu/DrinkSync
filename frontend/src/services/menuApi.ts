import { apiClient } from './apiClient';
import type { SpiritItem, MixerItem, PremadeItem } from '../types/menu';

interface SpiritItemRequest { name: string; price: number; }
interface MixerItemRequest { name: string; price: number; }
interface PremadeItemRequest { name: string; description: string; price: number; }

export const menuApi = {
  // Spirits
  createSpirit: (stationId: number, data: SpiritItemRequest, token: string) =>
    apiClient.post<SpiritItem>(`/api/stations/${stationId}/spirits`, data, { token }),
  updateSpirit: (stationId: number, itemId: number, data: SpiritItemRequest, token: string) =>
    apiClient.put<SpiritItem>(`/api/stations/${stationId}/spirits/${itemId}`, data, { token }),
  deleteSpirit: (stationId: number, itemId: number, token: string) =>
    apiClient.delete<void>(`/api/stations/${stationId}/spirits/${itemId}`, { token }),
  toggleSpiritAvailability: (stationId: number, itemId: number, token: string) =>
    apiClient.patch<void>(`/api/stations/${stationId}/spirits/${itemId}/availability`, undefined, { token }),

  // Mixers
  createMixer: (stationId: number, data: MixerItemRequest, token: string) =>
    apiClient.post<MixerItem>(`/api/stations/${stationId}/mixers`, data, { token }),
  updateMixer: (stationId: number, itemId: number, data: MixerItemRequest, token: string) =>
    apiClient.put<MixerItem>(`/api/stations/${stationId}/mixers/${itemId}`, data, { token }),
  deleteMixer: (stationId: number, itemId: number, token: string) =>
    apiClient.delete<void>(`/api/stations/${stationId}/mixers/${itemId}`, { token }),
  toggleMixerAvailability: (stationId: number, itemId: number, token: string) =>
    apiClient.patch<void>(`/api/stations/${stationId}/mixers/${itemId}/availability`, undefined, { token }),

  // Premades
  createPremade: (stationId: number, data: PremadeItemRequest, token: string) =>
    apiClient.post<PremadeItem>(`/api/stations/${stationId}/premades`, data, { token }),
  updatePremade: (stationId: number, itemId: number, data: PremadeItemRequest, token: string) =>
    apiClient.put<PremadeItem>(`/api/stations/${stationId}/premades/${itemId}`, data, { token }),
  deletePremade: (stationId: number, itemId: number, token: string) =>
    apiClient.delete<void>(`/api/stations/${stationId}/premades/${itemId}`, { token }),
  togglePremadeAvailability: (stationId: number, itemId: number, token: string) =>
    apiClient.patch<void>(`/api/stations/${stationId}/premades/${itemId}/availability`, undefined, { token }),
};
