import { useState, useCallback, useEffect } from 'react';
import { sessionApi } from '../services/sessionApi';
import type { Session } from '../types/session';

const SESSION_KEY = 'smart-event-bar-session-id';

export function useSession(_stationId?: number) {
  const [sessionId, setSessionId] = useState<string | null>(() =>
    localStorage.getItem(SESSION_KEY)
  );
  const [session, setSession] = useState<Session | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const createSession = useCallback(async (stId: number) => {
    setLoading(true);
    setError(null);
    try {
      const newSession = await sessionApi.createSession(stId);
      localStorage.setItem(SESSION_KEY, newSession.sessionId);
      setSessionId(newSession.sessionId);
      setSession(newSession);
      return newSession;
    } catch (e: any) {
      setError(e.message);
      return null;
    } finally {
      setLoading(false);
    }
  }, []);

  const loadSession = useCallback(async () => {
    if (!sessionId) return;
    setLoading(true);
    try {
      const s = await sessionApi.getSession(sessionId);
      setSession(s);
    } catch (e: any) {
      if (e.status === 404 || e.status === 410) {
        localStorage.removeItem(SESSION_KEY);
        setSessionId(null);
        setSession(null);
      }
      setError(e.message);
    } finally {
      setLoading(false);
    }
  }, [sessionId]);

  useEffect(() => {
    if (sessionId) {
      loadSession();
    }
  }, [sessionId, loadSession]);

  const ensureSession = useCallback(async (stId: number) => {
    if (sessionId) return sessionId;
    const newSession = await createSession(stId);
    return newSession?.sessionId ?? null;
  }, [sessionId, createSession]);

  const clearSession = useCallback(() => {
    localStorage.removeItem(SESSION_KEY);
    setSessionId(null);
    setSession(null);
  }, []);

  return { sessionId, session, loading, error, createSession, ensureSession, clearSession };
}
