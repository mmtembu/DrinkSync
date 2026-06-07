import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, cleanup, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { CheckoutPage } from './CheckoutPage';
import { orderApi } from '../../services/orderApi';
import type { Order } from '../../types/order';
import { OrderState } from '../../types/order';

vi.mock('../../services/orderApi', () => ({
  orderApi: {
    getOrder: vi.fn(),
    checkout: vi.fn(),
    pay: vi.fn(),
    cancel: vi.fn(),
  },
}));

vi.mock('../../hooks/useSession', () => ({
  useSession: () => ({ sessionId: 'test-session-id', session: null, loading: false, error: null }),
}));

vi.mock('../../services/idempotencyKeyGenerator', () => ({
  generateIdempotencyKey: () => 'test-key',
}));

const mockOrder: Order = {
  id: 1,
  stationId: 1,
  sessionId: 'test-session-id',
  state: OrderState.DRAFT,
  items: [
    {
      id: 1,
      quantity: 2,
      unitPrice: 50,
      spiritItems: [{ name: 'Vodka', id: 1 }],
      mixerItems: [{ name: 'Tonic', id: 2 }],
      premadeItemName: null,
    },
  ],
  totalPrice: 100,
  visualOrderNumber: 'A01',
  queuePosition: null,
  createdAt: '2024-01-01T00:00:00',
  updatedAt: '2024-01-01T00:00:00',
};

function renderCheckoutPage() {
  return render(
    <MemoryRouter initialEntries={['/station/1/checkout/1']}>
      <Routes>
        <Route path="/station/:stationId/checkout/:orderId" element={<CheckoutPage />} />
        <Route path="/station/:stationId" element={<div>Station Page</div>} />
        <Route path="/station/:stationId/tracking" element={<div>Tracking Page</div>} />
      </Routes>
    </MemoryRouter>
  );
}

describe('CheckoutPage - WhatsApp Opt-In Integration', () => {
  beforeEach(() => {
    sessionStorage.clear();
    vi.mocked(orderApi.getOrder).mockResolvedValue(mockOrder);
    vi.mocked(orderApi.checkout).mockResolvedValue({ ...mockOrder, state: OrderState.AWAITING_PAYMENT });
  });

  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
    sessionStorage.clear();
  });

  it('renders WhatsApp opt-in component on checkout page', async () => {
    renderCheckoutPage();

    await waitFor(() => {
      expect(screen.getByText('📱 WhatsApp Notifications')).toBeInTheDocument();
    });
    expect(screen.getByLabelText('Phone number')).toBeInTheDocument();
    expect(screen.getByLabelText('Send me order updates on WhatsApp')).toBeInTheDocument();
  });

  it('submits phone number and opt-in with checkout request', async () => {
    const user = userEvent.setup();
    renderCheckoutPage();

    await waitFor(() => {
      expect(screen.getByLabelText('Phone number')).toBeInTheDocument();
    });

    // Enter phone number and check opt-in
    const phoneInput = screen.getByLabelText('Phone number');
    await user.type(phoneInput, '821234567');

    const checkbox = screen.getByLabelText('Send me order updates on WhatsApp');
    await user.click(checkbox);

    // Click checkout
    const checkoutBtn = screen.getByRole('button', { name: /proceed to payment/i });
    await user.click(checkoutBtn);

    await waitFor(() => {
      expect(orderApi.checkout).toHaveBeenCalledWith(
        1,
        'test-session-id',
        { customerPhone: '+27821234567', whatsappOptIn: true }
      );
    });
  });

  it('persists phone and opt-in to sessionStorage after successful checkout', async () => {
    const user = userEvent.setup();
    renderCheckoutPage();

    await waitFor(() => {
      expect(screen.getByLabelText('Phone number')).toBeInTheDocument();
    });

    const phoneInput = screen.getByLabelText('Phone number');
    await user.type(phoneInput, '821234567');

    const checkbox = screen.getByLabelText('Send me order updates on WhatsApp');
    await user.click(checkbox);

    const checkoutBtn = screen.getByRole('button', { name: /proceed to payment/i });
    await user.click(checkoutBtn);

    await waitFor(() => {
      expect(sessionStorage.getItem('drinksync-whatsapp-phone')).toBe('+27821234567');
      expect(sessionStorage.getItem('drinksync-whatsapp-optin')).toBe('true');
    });
  });

  it('pre-fills phone and opt-in from sessionStorage on subsequent orders', async () => {
    sessionStorage.setItem('drinksync-whatsapp-phone', '+27821234567');
    sessionStorage.setItem('drinksync-whatsapp-optin', 'true');

    renderCheckoutPage();

    await waitFor(() => {
      expect(screen.getByLabelText('Phone number')).toBeInTheDocument();
    });

    const phoneInput = screen.getByLabelText('Phone number') as HTMLInputElement;
    expect(phoneInput.value).toBe('821234567');

    const checkbox = screen.getByLabelText('Send me order updates on WhatsApp') as HTMLInputElement;
    expect(checkbox.checked).toBe(true);
  });

  it('handles 400 validation error for invalid phone and displays to user', async () => {
    const user = userEvent.setup();
    const error = new Error('Invalid phone number format: must be E.164');
    (error as Error & { status: number }).status = 400;
    (error as Error & { body: { message: string } }).body = { message: 'Invalid phone number format: must be E.164' };
    vi.mocked(orderApi.checkout).mockRejectedValue(error);

    renderCheckoutPage();

    await waitFor(() => {
      expect(screen.getByLabelText('Phone number')).toBeInTheDocument();
    });

    const phoneInput = screen.getByLabelText('Phone number');
    await user.type(phoneInput, '123');

    const checkbox = screen.getByLabelText('Send me order updates on WhatsApp');
    await user.click(checkbox);

    const checkoutBtn = screen.getByRole('button', { name: /proceed to payment/i });
    await user.click(checkoutBtn);

    await waitFor(() => {
      expect(screen.getByRole('alert')).toBeInTheDocument();
      expect(screen.getByRole('alert').textContent).toContain('Invalid phone number format');
    });
  });

  it('shows general error for non-phone-related 400 errors', async () => {
    const user = userEvent.setup();
    const error = new Error('Order cannot be checked out');
    (error as Error & { status: number }).status = 400;
    (error as Error & { body: { message: string } }).body = { message: 'Order cannot be checked out' };
    vi.mocked(orderApi.checkout).mockRejectedValue(error);

    renderCheckoutPage();

    await waitFor(() => {
      expect(screen.getByLabelText('Phone number')).toBeInTheDocument();
    });

    const checkoutBtn = screen.getByRole('button', { name: /proceed to payment/i });
    await user.click(checkoutBtn);

    await waitFor(() => {
      expect(screen.getByText('Order cannot be checked out')).toBeInTheDocument();
    });
  });

  it('does not include customerPhone in body when phone is empty', async () => {
    const user = userEvent.setup();
    renderCheckoutPage();

    await waitFor(() => {
      expect(screen.getByLabelText('Phone number')).toBeInTheDocument();
    });

    // Don't enter a phone number, just click checkout
    const checkoutBtn = screen.getByRole('button', { name: /proceed to payment/i });
    await user.click(checkoutBtn);

    await waitFor(() => {
      expect(orderApi.checkout).toHaveBeenCalledWith(
        1,
        'test-session-id',
        { whatsappOptIn: false }
      );
    });
  });
});
