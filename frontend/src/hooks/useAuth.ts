import { useState, useCallback, useEffect } from 'react';
import { authApi } from '../services/authApi';
import type { AuthResponse } from '../types/auth';

const TOKEN_KEY = 'smart-event-bar-vendor-token';
const STATION_KEY = 'smart-event-bar-vendor-station';
const EXPIRES_KEY = 'smart-event-bar-vendor-expires';

export function useAuth() {
  const [token, setToken] = useState<string | null>(() => localStorage.getItem(TOKEN_KEY));
  const [stationId, setStationId] = useState<number | null>(() => {
    const stored = localStorage.getItem(STATION_KEY);
    return stored ? parseInt(stored, 10) : null;
  });
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const isExpired = useCallback(() => {
    const expires = localStorage.getItem(EXPIRES_KEY);
    if (!expires) return true;
    return new Date(expires) <= new Date();
  }, []);

  const login = useCallback(async (stId: number, accessCode: string) => {
    setLoading(true);
    setError(null);
    try {
      const response: AuthResponse = await authApi.login({ stationId: stId, accessCode });
      localStorage.setItem(TOKEN_KEY, response.token);
      localStorage.setItem(STATION_KEY, response.stationId.toString());
      localStorage.setItem(EXPIRES_KEY, response.expiresAt);
      setToken(response.token);
      setStationId(response.stationId);
      return response;
    } catch (e: unknown) {
      const err = e as { body?: { message?: string }; message?: string };
      setError(err.body?.message || err.message || 'Login failed');
      return null;
    } finally {
      setLoading(false);
    }
  }, []);

  const logout = useCallback(() => {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(STATION_KEY);
    localStorage.removeItem(EXPIRES_KEY);
    setToken(null);
    setStationId(null);
  }, []);

  // Check token expiry periodically
  useEffect(() => {
    if (!token) return;
    const interval = setInterval(() => {
      if (isExpired()) {
        logout();
      }
    }, 60000);
    return () => clearInterval(interval);
  }, [token, isExpired, logout]);

  return {
    token,
    stationId,
    isAuthenticated: !!token && !isExpired(),
    loading,
    error,
    login,
    logout,
  };
}
