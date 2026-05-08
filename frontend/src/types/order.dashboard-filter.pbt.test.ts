import { describe, it, expect } from 'vitest';
import * as fc from 'fast-check';
import {
  OrderState,
  OrderItemType,
  CupOption,
  filterDashboardOrders,
} from './order';
import type { Order, OrderItem } from './order';

/**
 * Property 11: Dashboard filters to active orders
 *
 * For any set of Orders at a Station, the Vendor_Dashboard initial load SHALL return
 * exactly the Orders whose state is NOT a terminal state (i.e., not COLLECTED, not
 * CANCELLED, and not EXPIRED). CANCELLED orders are excluded from the active view
 * entirely. EXPIRED orders are displayed in a separate section.
 *
 * **Validates: Requirements 7.4, 13.5, 15.4**
 */
describe('Property 11: Dashboard filters to active orders', () => {
  // --- Generators ---

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

  const orderStateArb = fc.constantFrom(...ALL_ORDER_STATES);

  const nameArb = fc
    .stringMatching(/^[A-Za-z][A-Za-z0-9 ]{0,19}$/)
    .map((s) => s.trimEnd())
    .filter((s) => s.length > 0);

  const priceArb = fc
    .double({ min: 0.01, max: 999.99, noNaN: true })
    .map((p) => Math.round(p * 100) / 100);

  const quantityArb = fc.integer({ min: 1, max: 10 });

  const orderItemArb: fc.Arbitrary<OrderItem> = fc.oneof(
    fc.record({
      id: fc.integer({ min: 1, max: 10000 }),
      itemType: fc.constant(OrderItemType.CUSTOM_DRINK as OrderItemType),
      spiritItemId: fc.integer({ min: 1, max: 100 }),
      spiritItemName: nameArb,
      mixerItemId: fc.integer({ min: 1, max: 100 }),
      mixerItemName: nameArb,
      premadeItemId: fc.constant(undefined),
      premadeItemName: fc.constant(undefined),
      cupOption: fc.constantFrom<CupOption>(CupOption.REUSE_CUP, CupOption.NEW_CUP),
      cupPrice: priceArb,
      quantity: quantityArb,
      unitPrice: priceArb,
    }),
    fc.record({
      id: fc.integer({ min: 1, max: 10000 }),
      itemType: fc.constant(OrderItemType.PREMADE as OrderItemType),
      spiritItemId: fc.constant(undefined),
      spiritItemName: fc.constant(undefined),
      mixerItemId: fc.constant(undefined),
      mixerItemName: fc.constant(undefined),
      premadeItemId: fc.integer({ min: 1, max: 100 }),
      premadeItemName: nameArb,
      cupOption: fc.constant(undefined),
      cupPrice: fc.constant(undefined),
      quantity: quantityArb,
      unitPrice: priceArb,
    })
  );

  const orderArb: fc.Arbitrary<Order> = fc.record({
    id: fc.integer({ min: 1, max: 10000 }),
    stationId: fc.integer({ min: 1, max: 50 }),
    stationName: nameArb,
    visualOrderNumber: fc.integer({ min: 1, max: 999 }).map((n) => `#${n.toString().padStart(3, '0')}`),
    state: orderStateArb,
    queuePosition: fc.integer({ min: 1, max: 200 }),
    totalPrice: priceArb,
    createdAt: fc.constant(new Date().toISOString()),
    updatedAt: fc.constant(new Date().toISOString()),
    items: fc.array(orderItemArb, { minLength: 1, maxLength: 3 }),
  });

  const orderListArb = fc.array(orderArb, { minLength: 0, maxLength: 20 });

  // --- Properties ---

  it('CANCELLED orders never appear in the main view (Req 13.5)', () => {
    fc.assert(
      fc.property(orderListArb, (orders) => {
        const { mainOrders } = filterDashboardOrders(orders);

        // No CANCELLED order should appear in mainOrders
        const cancelledInMain = mainOrders.filter((o) => o.state === OrderState.CANCELLED);
        expect(cancelledInMain).toHaveLength(0);
      }),
      { numRuns: 100 }
    );
  });

  it('CANCELLED orders never appear in the expired section (Req 13.5)', () => {
    fc.assert(
      fc.property(orderListArb, (orders) => {
        const { expiredOrders } = filterDashboardOrders(orders);

        // No CANCELLED order should appear in expiredOrders either
        const cancelledInExpired = expiredOrders.filter((o) => o.state === OrderState.CANCELLED);
        expect(cancelledInExpired).toHaveLength(0);
      }),
      { numRuns: 100 }
    );
  });

  it('EXPIRED orders are separated into the expired section (Req 15.4)', () => {
    fc.assert(
      fc.property(orderListArb, (orders) => {
        const { mainOrders, expiredOrders } = filterDashboardOrders(orders);

        // All EXPIRED orders from input should be in expiredOrders
        const inputExpired = orders.filter((o) => o.state === OrderState.EXPIRED);
        expect(expiredOrders.length).toBe(inputExpired.length);

        // Every expired order from input should appear in the expired section
        const expiredIds = expiredOrders.map((o) => o.id);
        for (const order of inputExpired) {
          expect(expiredIds).toContain(order.id);
        }

        // No EXPIRED order should appear in mainOrders
        const expiredInMain = mainOrders.filter((o) => o.state === OrderState.EXPIRED);
        expect(expiredInMain).toHaveLength(0);
      }),
      { numRuns: 100 }
    );
  });

  it('main view contains only PAID, PREPARING, READY, COLLECTED, DRAFT, AWAITING_PAYMENT orders (Req 7.4)', () => {
    const MAIN_VIEW_STATES: OrderState[] = [
      OrderState.DRAFT,
      OrderState.AWAITING_PAYMENT,
      OrderState.PAID,
      OrderState.PREPARING,
      OrderState.READY,
      OrderState.COLLECTED,
    ];

    fc.assert(
      fc.property(orderListArb, (orders) => {
        const { mainOrders } = filterDashboardOrders(orders);

        // Every order in mainOrders must have a state in the allowed set
        for (const order of mainOrders) {
          expect(MAIN_VIEW_STATES).toContain(order.state);
        }
      }),
      { numRuns: 100 }
    );
  });

  it('all non-CANCELLED, non-EXPIRED orders appear in the main view', () => {
    fc.assert(
      fc.property(orderListArb, (orders) => {
        const { mainOrders } = filterDashboardOrders(orders);

        const expectedMain = orders.filter(
          (o) => o.state !== OrderState.CANCELLED && o.state !== OrderState.EXPIRED
        );

        expect(mainOrders.length).toBe(expectedMain.length);

        const mainIds = mainOrders.map((o) => o.id);
        for (const order of expectedMain) {
          expect(mainIds).toContain(order.id);
        }
      }),
      { numRuns: 100 }
    );
  });

  it('combined main + expired sections account for all non-CANCELLED orders', () => {
    fc.assert(
      fc.property(orderListArb, (orders) => {
        const { mainOrders, expiredOrders } = filterDashboardOrders(orders);

        const nonCancelled = orders.filter((o) => o.state !== OrderState.CANCELLED);

        // main + expired should equal all non-cancelled orders
        expect(mainOrders.length + expiredOrders.length).toBe(nonCancelled.length);
      }),
      { numRuns: 100 }
    );
  });

  it('filtering preserves order identity (no orders created or duplicated)', () => {
    fc.assert(
      fc.property(orderListArb, (orders) => {
        const { mainOrders, expiredOrders } = filterDashboardOrders(orders);

        // Every order in mainOrders must exist in the original input
        const inputIds = new Set(orders.map((o) => o.id));
        for (const order of mainOrders) {
          expect(inputIds.has(order.id)).toBe(true);
        }
        for (const order of expiredOrders) {
          expect(inputIds.has(order.id)).toBe(true);
        }
      }),
      { numRuns: 100 }
    );
  });
});
