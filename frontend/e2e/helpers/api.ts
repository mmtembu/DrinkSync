import { APIRequestContext } from '@playwright/test';

const API_BASE = 'http://localhost:8080';

/**
 * Backend API helper for E2E test setup and teardown.
 * Calls the backend REST API directly to seed data and manipulate state
 * without going through the UI.
 */
export class TestApi {
  constructor(private request: APIRequestContext) {}

  // ── Station ──────────────────────────────────────────────────────────

  async getStation(stationId: number) {
    const res = await this.request.get(`${API_BASE}/api/stations/${stationId}`);
    return res.json();
  }

  async getMenu(stationId: number) {
    const res = await this.request.get(`${API_BASE}/api/stations/${stationId}/menu`);
    return res.json();
  }

  // ── Session ──────────────────────────────────────────────────────────

  async createSession(stationId: number): Promise<{ sessionId: string; stationId: number }> {
    const res = await this.request.post(`${API_BASE}/api/sessions`, {
      data: { stationId },
    });
    return res.json();
  }

  async getSessionOrders(sessionId: string) {
    const res = await this.request.get(`${API_BASE}/api/sessions/${sessionId}/orders`);
    return res.json();
  }

  // ── Orders ───────────────────────────────────────────────────────────

  async createOrder(stationId: number, items: OrderItemPayload[], sessionId: string) {
    const res = await this.request.post(`${API_BASE}/api/stations/${stationId}/orders`, {
      data: items,
      headers: { 'X-Session-Id': sessionId },
    });
    return res.json();
  }

  async checkout(orderId: number, sessionId: string) {
    const res = await this.request.post(`${API_BASE}/api/orders/${orderId}/checkout`, {
      headers: { 'X-Session-Id': sessionId },
    });
    return res.json();
  }

  async pay(orderId: number, sessionId: string) {
    const idempotencyKey = crypto.randomUUID();
    const res = await this.request.post(`${API_BASE}/api/orders/${orderId}/pay`, {
      headers: {
        'X-Session-Id': sessionId,
        'Idempotency-Key': idempotencyKey,
      },
    });
    return res.json();
  }

  async cancelOrder(orderId: number, sessionId: string) {
    const res = await this.request.post(`${API_BASE}/api/orders/${orderId}/cancel`, {
      headers: { 'X-Session-Id': sessionId },
    });
    return res.json();
  }

  async getOrder(orderId: number, sessionId: string) {
    const res = await this.request.get(`${API_BASE}/api/orders/${orderId}`, {
      headers: { 'X-Session-Id': sessionId },
    });
    return res.json();
  }

  // ── Vendor state transitions ─────────────────────────────────────────

  async vendorLogin(stationId: number, accessCode: string): Promise<{ token: string }> {
    const res = await this.request.post(`${API_BASE}/api/auth/vendor/login`, {
      data: { stationId, accessCode },
    });
    return res.json();
  }

  async transitionOrder(orderId: number, targetState: string, token: string) {
    const res = await this.request.patch(`${API_BASE}/api/orders/${orderId}/state`, {
      data: { targetState },
      headers: { Authorization: `Bearer ${token}` },
    });
    return res.json();
  }

  // ── Convenience: full order lifecycle via API ────────────────────────

  async createAndPayOrder(stationId: number, items: OrderItemPayload[], sessionId: string) {
    const order = await this.createOrder(stationId, items, sessionId);
    await this.checkout(order.id, sessionId);
    const paid = await this.pay(order.id, sessionId);
    return paid;
  }

  async completeOrder(orderId: number, sessionId: string, vendorToken: string) {
    await this.transitionOrder(orderId, 'PREPARING', vendorToken);
    await this.transitionOrder(orderId, 'READY', vendorToken);
    await this.transitionOrder(orderId, 'COLLECTED', vendorToken);
  }
}

export interface OrderItemPayload {
  itemType: 'CUSTOM_DRINK' | 'PREMADE';
  spiritItemId?: number;
  mixerItemId?: number;
  premadeItemId?: number;
  cupOption?: 'REUSE_CUP' | 'NEW_CUP';
  quantity: number;
}
