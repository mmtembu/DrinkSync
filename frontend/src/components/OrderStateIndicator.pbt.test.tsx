import { describe, it, expect, afterEach } from 'vitest';
import { render, cleanup } from '@testing-library/react';
import * as fc from 'fast-check';
import { OrderStateIndicator } from './OrderStateIndicator';
import { OrderState, ORDER_STATE_LABELS } from '../types/order';

afterEach(() => {
  cleanup();
});

/**
 * Property 7: Order state display label mapping
 *
 * For any Order_State value, the display label mapping SHALL produce a non-empty
 * human-readable string. Specifically: PAID → "Paid", PREPARING → "Preparing",
 * READY → "Ready for Pickup", COLLECTED → "Collected", CANCELLED → "Cancelled",
 * EXPIRED → "Expired".
 *
 * **Validates: Requirements 5.2, 13.6, 15.5**
 */
describe('Property 7: Order state display label mapping', () => {
  const ALL_ORDER_STATES: OrderState[] = [
    OrderState.DRAFT,
    OrderState.AWAITING_PAYMENT,
    OrderState.PAID,
    OrderState.PREPARING,
    OrderState.READY,
    OrderState.COLLECTED,
    OrderState.CANCELLED,
    OrderState.EXPIRED,
  ];

  const EXPECTED_LABELS: Record<OrderState, string> = {
    [OrderState.DRAFT]: 'Draft',
    [OrderState.AWAITING_PAYMENT]: 'Awaiting Payment',
    [OrderState.PAID]: 'Paid',
    [OrderState.PREPARING]: 'Preparing',
    [OrderState.READY]: 'Ready for Pickup',
    [OrderState.COLLECTED]: 'Collected',
    [OrderState.CANCELLED]: 'Cancelled',
    [OrderState.EXPIRED]: 'Expired',
  };

  // Generator: randomly pick from all 8 OrderState values
  const orderStateArb = fc.constantFrom(...ALL_ORDER_STATES);

  it('renders the correct human-readable label for any OrderState', () => {
    fc.assert(
      fc.property(orderStateArb, (state) => {
        cleanup();
        const { getByRole } = render(<OrderStateIndicator state={state} />);

        const statusElement = getByRole('status');
        const textContent = statusElement.textContent ?? '';

        // The label must be non-empty
        expect(textContent.length).toBeGreaterThan(0);

        // The label must match the expected human-readable string
        expect(textContent).toBe(EXPECTED_LABELS[state]);
      }),
      { numRuns: 100 }
    );
  });

  it('ORDER_STATE_LABELS mapping matches expected labels for any OrderState', () => {
    fc.assert(
      fc.property(orderStateArb, (state) => {
        // The ORDER_STATE_LABELS constant must produce the correct label
        expect(ORDER_STATE_LABELS[state]).toBe(EXPECTED_LABELS[state]);

        // The label must be a non-empty string
        expect(ORDER_STATE_LABELS[state].length).toBeGreaterThan(0);
      }),
      { numRuns: 100 }
    );
  });

  it('sets the correct aria-label for accessibility for any OrderState', () => {
    fc.assert(
      fc.property(orderStateArb, (state) => {
        cleanup();
        const { getByRole } = render(<OrderStateIndicator state={state} />);

        const statusElement = getByRole('status');
        const ariaLabel = statusElement.getAttribute('aria-label');

        expect(ariaLabel).toBe(`Order status: ${EXPECTED_LABELS[state]}`);
      }),
      { numRuns: 100 }
    );
  });
});
