const API_BASE = import.meta.env.VITE_API_BASE || 'http://localhost:8080';

interface RequestOptions extends RequestInit {
  sessionId?: string;
  token?: string;
}

async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { sessionId, token, ...fetchOptions } = options;

  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(options.headers as Record<string, string>),
  };

  if (sessionId) {
    headers['X-Session-Id'] = sessionId;
  }

  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  const response = await fetch(`${API_BASE}${path}`, {
    ...fetchOptions,
    headers,
  });

  if (!response.ok) {
    const errorBody = await response.json().catch(() => ({ message: response.statusText }));
    const error = new Error(errorBody.message || `Request failed: ${response.status}`);
    (error as Error & { status: number }).status = response.status;
    (error as Error & { body: unknown }).body = errorBody;
    throw error;
  }

  if (response.status === 204) {
    return undefined as unknown as T;
  }

  return response.json();
}

export const apiClient = {
  get: <T>(path: string, options?: RequestOptions) =>
    request<T>(path, { ...options, method: 'GET' }),

  post: <T>(path: string, body?: unknown, options?: RequestOptions) =>
    request<T>(path, { ...options, method: 'POST', body: body ? JSON.stringify(body) : undefined }),

  put: <T>(path: string, body?: unknown, options?: RequestOptions) =>
    request<T>(path, { ...options, method: 'PUT', body: body ? JSON.stringify(body) : undefined }),

  patch: <T>(path: string, body?: unknown, options?: RequestOptions) =>
    request<T>(path, { ...options, method: 'PATCH', body: body ? JSON.stringify(body) : undefined }),

  delete: <T>(path: string, options?: RequestOptions) =>
    request<T>(path, { ...options, method: 'DELETE' }),
};
