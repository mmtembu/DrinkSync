import { describe, it, expect, afterEach } from 'vitest';
import { render, cleanup } from '@testing-library/react';
import * as fc from 'fast-check';
import { VendorOrderCard } from './VendorOrderCard';
import type { Order, OrderItem } from '../types/order';
import { OrderState, CupOption, OrderItemType, ORDER_STATE_LABELS } from '../types/order';

afterEach(() => {
  cleanup();
});

/**
 * Property 9: Vendor dashboard order display completeness
 *
 * For any Order displayed on the Vendor_Dashboard, the rendered view SHALL include
 * the visual order number, list of Order_Items with quantities, drink details and
 * cup choice, total price, Queue_Position, and current Order_State.
 *
 * **Validates: Requirements 7.2**
 */
describe('Property 9: Vendor dashboard order display completeness', () => {
  // --- Generators ---

  const nameArb = fc
    .stringMatching(/^[A-Za-z][A-Za-z0-9 ]{0,19}$/)
    .map((s) => s.trimEnd())
    .filter((s) => s.length > 0);

  const priceArb = fc
    .double({ min: 0.01, max: 999.99, noNaN: true })
    .map((p) => Math.round(p * 100) / 100);

  const quantityArb = fc.integer({ min: 1, max: 10 });

  const cupOptionArb = fc.constantFrom<CupOption>(CupOption.REUSE_CUP, CupOption.NEW_CUP);

  const customDrinkItemArb: fc.Arbitrary<OrderItem> = fc.record({
    id: fc.integer({ min: 1, max: 10000 }),
    itemType: fc.constant(OrderItemType.CUSTOM_DRINK as OrderItemType),
    spiritItems: fc.array(fc.record({ id: fc.integer({ min: 1, max: 100 }), name: nameArb }), { minLength: 1, maxLength: 3 }),
    mixerItems: fc.array(fc.record({ id: fc.integer({ min: 1, max: 100 }), name: nameArb }), { minLength: 1, maxLength: 3 }),
    premadeItemId: fc.constant(undefined),
    premadeItemName: fc.constant(undefined),
    cupOption: cupOptionArb,
    cupPrice: priceArb,
    quantity: quantityArb,
    unitPrice: priceArb,
  });

  const premadeItemArb: fc.Arbitrary<OrderItem> = fc.record({
    id: fc.integer({ min: 1, max: 10000 }),
    itemType: fc.constant(OrderItemType.PREMADE as OrderItemType),
    spiritItems: fc.constant(undefined),
    mixerItems: fc.constant(undefined),
    premadeItemId: fc.integer({ min: 1, max: 100 }),
    premadeItemName: nameArb,
    cupOption: fc.constant(undefined),
    cupPrice: fc.constant(undefined),
    quantity: quantityArb,
    unitPrice: priceArb,
  });

  const orderItemArb = fc.oneof(customDrinkItemArb, premadeItemArb);

  // Vendor-visible states (orders that appear on the dashboard)
  const vendorVisibleStates: OrderState[] = [
    OrderState.PAID,
    OrderState.PREPARING,
    OrderState.READY,
    OrderState.COLLECTED,
  ];

  const vendorStateArb = fc.constantFrom(...vendorVisibleStates);

  const visualOrderNumberArb = fc
    .integer({ min: 1, max: 999 })
    .map((n) => `#${n.toString().padStart(3, '0')}`);

  const orderArb: fc.Arbitrary<Order> = fc
    .record({
      id: fc.integer({ min: 1, max: 10000 }),
      stationId: fc.integer({ min: 1, max: 50 }),
      stationName: nameArb,
      visualOrderNumber: visualOrderNumberArb,
      state: vendorStateArb,
      queuePosition: fc.integer({ min: 1, max: 200 }),
      totalPrice: priceArb,
      createdAt: fc.constant(new Date().toISOString()),
      updatedAt: fc.constant(new Date().toISOString()),
      items: fc.array(orderItemArb, { minLength: 1, maxLength: 5 }),
    })
    .map((o) => ({
      ...o,
      totalPrice: o.items.reduce((sum, item) => sum + item.unitPrice * item.quantity, 0),
      totalPriceRounded: undefined,
    }))
    .map((o) => ({
      ...o,
      totalPrice: Math.round(o.totalPrice * 100) / 100,
    }));

  it('displays the visual order number for any order', () => {
    fc.assert(
      fc.property(orderArb, (order) => {
        cleanup();
        const { getByTestId } = render(<VendorOrderCard order={order} />);

        const visualOrderEl = getByTestId('visual-order-number');
        expect(visualOrderEl.textContent).toBe(order.visualOrderNumber);
      }),
      { numRuns: 100 }
    );
  });

  it('displays all order items with quantities for any order', () => {
    fc.assert(
      fc.property(orderArb, (order) => {
        cleanup();
        const { getAllByTestId } = render(<VendorOrderCard order={order} />);

        const itemRows = getAllByTestId('order-item-row');
        // Must have exactly as many item rows as order items
        expect(itemRows.length).toBe(order.items.length);

        // Each item row must show the quantity
        const quantityEls = getAllByTestId('item-quantity');
        order.items.forEach((item, i) => {
          expect(quantityEls[i].textContent).toContain(`${item.quantity}`);
        });
      }),
      { numRuns: 100 }
    );
  });

  it('displays drink details and cup choice for custom drink items', () => {
    // Use only custom drink items to ensure we always test drink details
    const customOnlyOrderArb = orderArb.map((o) => ({
      ...o,
      items: o.items.map((item) => ({
        ...item,
        itemType: OrderItemType.CUSTOM_DRINK as OrderItemType,
        spiritItems: item.spiritItems ?? [{ id: 1, name: 'Vodka' }],
        mixerItems: item.mixerItems ?? [{ id: 1, name: 'Tonic' }],
        cupOption: item.cupOption ?? CupOption.NEW_CUP,
        premadeItemId: undefined,
        premadeItemName: undefined,
      })),
    }));

    fc.assert(
      fc.property(customOnlyOrderArb, (order) => {
        cleanup();
        const { getAllByTestId } = render(<VendorOrderCard order={order} />);

        const detailEls = getAllByTestId('item-details');
        order.items.forEach((item, i) => {
          const text = detailEls[i].textContent ?? '';
          // Must contain all spirit names and mixer names
          for (const spirit of item.spiritItems ?? []) {
            expect(text).toContain(spirit.name);
          }
          for (const mixer of item.mixerItems ?? []) {
            expect(text).toContain(mixer.name);
          }
          // Must contain cup choice
          const expectedCupLabel = item.cupOption === CupOption.NEW_CUP ? 'New Cup' : 'Reuse Cup';
          expect(text).toContain(expectedCupLabel);
        });
      }),
      { numRuns: 100 }
    );
  });

  it('displays premade item name for premade items', () => {
    const premadeOnlyOrderArb = orderArb.map((o) => ({
      ...o,
      items: o.items.map((item) => ({
        ...item,
        itemType: OrderItemType.PREMADE as OrderItemType,
        spiritItems: undefined,
        mixerItems: undefined,
        cupOption: undefined,
        premadeItemId: item.premadeItemId ?? 1,
        premadeItemName: item.premadeItemName ?? 'Beer',
      })),
    }));

    fc.assert(
      fc.property(premadeOnlyOrderArb, (order) => {
        cleanup();
        const { getAllByTestId } = render(<VendorOrderCard order={order} />);

        const detailEls = getAllByTestId('item-details');
        order.items.forEach((item, i) => {
          const text = detailEls[i].textContent ?? '';
          expect(text).toContain(item.premadeItemName);
        });
      }),
      { numRuns: 100 }
    );
  });

  it('displays the total price for any order', () => {
    fc.assert(
      fc.property(orderArb, (order) => {
        cleanup();
        const { getByTestId } = render(<VendorOrderCard order={order} />);

        const priceEl = getByTestId('total-price');
        expect(priceEl.textContent).toBe(`R${order.totalPrice.toFixed(2)}`);
      }),
      { numRuns: 100 }
    );
  });

  it('displays the queue position for any order', () => {
    fc.assert(
      fc.property(orderArb, (order) => {
        cleanup();
        const { getByTestId } = render(<VendorOrderCard order={order} />);

        const queueEl = getByTestId('queue-position');
        expect(queueEl.textContent).toContain(`${order.queuePosition}`);
      }),
      { numRuns: 100 }
    );
  });

  it('displays the current order state for any order', () => {
    fc.assert(
      fc.property(orderArb, (order) => {
        cleanup();
        const { getByRole } = render(<VendorOrderCard order={order} />);

        // OrderStateIndicator renders with role="status"
        const stateEl = getByRole('status');
        expect(stateEl.textContent).toBe(ORDER_STATE_LABELS[order.state]);
      }),
      { numRuns: 100 }
    );
  });
});
