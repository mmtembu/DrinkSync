import { apiClient } from './apiClient';
import type { AuthResponse, VendorLoginRequest } from '../types/auth';

export const authApi = {
  login: (data: VendorLoginRequest) =>
    apiClient.post<AuthResponse>('/api/auth/vendor/login', data),

  refresh: (token: string) =>
    apiClient.post<AuthResponse>('/api/auth/vendor/refresh', undefined, { token }),
};
