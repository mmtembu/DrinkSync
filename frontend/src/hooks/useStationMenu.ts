import { useState, useEffect, useCallback } from 'react';
import { stationApi } from '../services/stationApi';
import type { Menu } from '../types/menu';

const MENU_CACHE_KEY = 'smart-event-bar-menu-';

export function useStationMenu(stationId?: number) {
  const [menu, setMenu] = useState<Menu | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const fetchMenu = useCallback(async () => {
    if (!stationId) return;
    setLoading(true);
    setError(null);
    try {
      const data = await stationApi.getMenu(stationId);
      setMenu(data);
      // Cache for offline use
      try {
        localStorage.setItem(MENU_CACHE_KEY + stationId, JSON.stringify(data));
      } catch { /* ignore storage errors */ }
    } catch (e: any) {
      // Try cached data
      try {
        const cached = localStorage.getItem(MENU_CACHE_KEY + stationId);
        if (cached) {
          setMenu(JSON.parse(cached));
          setError('Using cached menu data. Network unavailable.');
          return;
        }
      } catch { /* ignore */ }
      setError(e.message || 'Failed to load menu');
    } finally {
      setLoading(false);
    }
  }, [stationId]);

  useEffect(() => {
    fetchMenu();
  }, [fetchMenu]);

  return { menu, loading, error, refetch: fetchMenu };
}
