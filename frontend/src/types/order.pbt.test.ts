import { describe, it, expect } from 'vitest';
import * as fc from 'fast-check';
import {
  OrderState,
  STATE_PRIORITY,
  sortOrdersByStatePriority,
  OrderItemType,
  CupOption,
} from './order';
import type { Order, OrderItem } from './order';

/**
 * Property 10: Order sorting by state priority
 *
 * For any list of Orders displayed on the Vendor_Dashboard, the Orders SHALL be
 * sorted by Order_State in the priority: PAID first, then PREPARING, then READY,
 * then COLLECTED. Within the same state, ordering is preserved.
 *
 * **Validates: Requirements 7.3**
 */
describe('Property 10: Order sorting by state priority', () => {
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

  it('sorted orders respect state priority ordering for any permutation', () => {
    fc.assert(
      fc.property(orderListArb, (orders) => {
        const sorted = sortOrdersByStatePriority(orders);

        // Every consecutive pair must have non-decreasing priority
        for (let i = 0; i < sorted.length - 1; i++) {
          expect(STATE_PRIORITY[sorted[i].state]).toBeLessThanOrEqual(
            STATE_PRIORITY[sorted[i + 1].state]
          );
        }
      }),
      { numRuns: 100 }
    );
  });

  it('preserves relative order of orders with the same state (stability)', () => {
    fc.assert(
      fc.property(orderListArb, (orders) => {
        const sorted = sortOrdersByStatePriority(orders);

        // For each state, the relative order of orders should be preserved
        for (const state of ALL_ORDER_STATES) {
          const originalInState = orders.filter((o) => o.state === state);
          const sortedInState = sorted.filter((o) => o.state === state);

          // Same count
          expect(sortedInState.length).toBe(originalInState.length);

          // Same order (by id sequence, since these are the same objects)
          for (let i = 0; i < originalInState.length; i++) {
            expect(sortedInState[i].id).toBe(originalInState[i].id);
          }
        }
      }),
      { numRuns: 100 }
    );
  });

  it('PAID orders appear before PREPARING, READY, and COLLECTED', () => {
    fc.assert(
      fc.property(orderListArb, (orders) => {
        const sorted = sortOrdersByStatePriority(orders);

        const lastPaidIndex = sorted.map((o) => o.state).lastIndexOf(OrderState.PAID);
        const firstPreparingIndex = sorted.map((o) => o.state).indexOf(OrderState.PREPARING);
        const firstReadyIndex = sorted.map((o) => o.state).indexOf(OrderState.READY);
        const firstCollectedIndex = sorted.map((o) => o.state).indexOf(OrderState.COLLECTED);

        if (lastPaidIndex !== -1 && firstPreparingIndex !== -1) {
          expect(lastPaidIndex).toBeLessThan(firstPreparingIndex);
        }
        if (lastPaidIndex !== -1 && firstReadyIndex !== -1) {
          expect(lastPaidIndex).toBeLessThan(firstReadyIndex);
        }
        if (lastPaidIndex !== -1 && firstCollectedIndex !== -1) {
          expect(lastPaidIndex).toBeLessThan(firstCollectedIndex);
        }
      }),
      { numRuns: 100 }
    );
  });

  it('PREPARING orders appear before READY and COLLECTED', () => {
    fc.assert(
      fc.property(orderListArb, (orders) => {
        const sorted = sortOrdersByStatePriority(orders);

        const lastPreparingIndex = sorted.map((o) => o.state).lastIndexOf(OrderState.PREPARING);
        const firstReadyIndex = sorted.map((o) => o.state).indexOf(OrderState.READY);
        const firstCollectedIndex = sorted.map((o) => o.state).indexOf(OrderState.COLLECTED);

        if (lastPreparingIndex !== -1 && firstReadyIndex !== -1) {
          expect(lastPreparingIndex).toBeLessThan(firstReadyIndex);
        }
        if (lastPreparingIndex !== -1 && firstCollectedIndex !== -1) {
          expect(lastPreparingIndex).toBeLessThan(firstCollectedIndex);
        }
      }),
      { numRuns: 100 }
    );
  });

  it('READY orders appear before COLLECTED', () => {
    fc.assert(
      fc.property(orderListArb, (orders) => {
        const sorted = sortOrdersByStatePriority(orders);

        const lastReadyIndex = sorted.map((o) => o.state).lastIndexOf(OrderState.READY);
        const firstCollectedIndex = sorted.map((o) => o.state).indexOf(OrderState.COLLECTED);

        if (lastReadyIndex !== -1 && firstCollectedIndex !== -1) {
          expect(lastReadyIndex).toBeLessThan(firstCollectedIndex);
        }
      }),
      { numRuns: 100 }
    );
  });

  it('does not mutate the original array', () => {
    fc.assert(
      fc.property(orderListArb, (orders) => {
        const originalIds = orders.map((o) => o.id);
        sortOrdersByStatePriority(orders);
        const afterIds = orders.map((o) => o.id);

        // Original array should be unchanged
        expect(afterIds).toEqual(originalIds);
      }),
      { numRuns: 100 }
    );
  });

  it('sorted result contains exactly the same orders as the input', () => {
    fc.assert(
      fc.property(orderListArb, (orders) => {
        const sorted = sortOrdersByStatePriority(orders);

        // Same length
        expect(sorted.length).toBe(orders.length);

        // Same set of order IDs (sorted for comparison)
        const inputIds = orders.map((o) => o.id).sort((a, b) => a - b);
        const sortedIds = sorted.map((o) => o.id).sort((a, b) => a - b);
        expect(sortedIds).toEqual(inputIds);
      }),
      { numRuns: 100 }
    );
  });
});
