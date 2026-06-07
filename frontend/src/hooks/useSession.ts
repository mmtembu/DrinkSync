import { useState, useCallback, useEffect } from 'react';
import { sessionApi } from '../services/sessionApi';
import type { Session } from '../types/session';

const SESSION_KEY = 'smart-event-bar-session-id';

// eslint-disable-next-line @typescript-eslint/no-unused-vars
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
    } catch (e: unknown) {
      const err = e as { message?: string };
      setError(err.message ?? 'Failed to create session');
      return null;
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (!sessionId) return;
    let cancelled = false;
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setLoading(true);
    sessionApi.getSession(sessionId).then((s) => {
      if (!cancelled) setSession(s);
    }).catch((e: unknown) => {
      if (cancelled) return;
      const err = e as { status?: number; message?: string };
      if (err.status === 404 || err.status === 410) {
        localStorage.removeItem(SESSION_KEY);
        setSessionId(null);
        setSession(null);
      }
      setError(err.message ?? 'Failed to load session');
    }).finally(() => {
      if (!cancelled) setLoading(false);
    });
    return () => { cancelled = true; };
  }, [sessionId]);

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
