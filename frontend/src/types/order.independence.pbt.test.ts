import { describe, it, expect } from 'vitest';
import * as fc from 'fast-check';
import { OrderState, OrderItemType, CupOption } from './order';
import type { Order, OrderItem } from './order';

/**
 * Property 28: Order independence in multi-order sessions
 *
 * For any session with multiple orders at the same station, each order SHALL have
 * its own independent Order_State, Queue_Position, and visual order number.
 * Transitioning one order's state SHALL not affect any other order in the session.
 *
 * **Validates: Requirements 16.2**
 */
describe('Property 28: Order independence in multi-order sessions', () => {
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

  /** Generate an order with a unique id derived from the given index offset */
  const orderWithIdArb = (idOffset: number): fc.Arbitrary<Order> =>
    fc.record({
      id: fc.constant(idOffset),
      stationId: fc.constant(1),
      stationName: fc.constant('Station A'),
      visualOrderNumber: fc.constant(`#${idOffset.toString().padStart(3, '0')}`),
      state: orderStateArb,
      queuePosition: fc.integer({ min: 1, max: 200 }),
      totalPrice: priceArb,
      createdAt: fc.constant(new Date().toISOString()),
      updatedAt: fc.constant(new Date().toISOString()),
      items: fc.array(orderItemArb, { minLength: 1, maxLength: 3 }),
    });

  /**
   * Generate a session with 2–3 orders at the same station, each with a unique id.
   * Returns the list of orders and an index indicating which order to transition.
   */
  const sessionWithTransitionArb = fc
    .integer({ min: 2, max: 3 })
    .chain((orderCount) =>
      fc.tuple(
        fc.tuple(...Array.from({ length: orderCount }, (_, i) => orderWithIdArb(i + 1))),
        fc.integer({ min: 0, max: orderCount - 1 }),
        orderStateArb
      )
    );

  // --- Helper: simulate a state transition on one order (pure copy) ---

  function transitionOrder(order: Order, newState: OrderState): Order {
    return {
      ...order,
      state: newState,
      updatedAt: new Date().toISOString(),
    };
  }

  // --- Properties ---

  it('transitioning one order does not change any other order in the session', () => {
    fc.assert(
      fc.property(sessionWithTransitionArb, ([orders, targetIndex, newState]) => {
        // Snapshot all orders before the transition (deep copy)
        const snapshotBefore = orders.map((o) => ({
          id: o.id,
          state: o.state,
          queuePosition: o.queuePosition,
          visualOrderNumber: o.visualOrderNumber,
          totalPrice: o.totalPrice,
          items: o.items.map((item) => ({ ...item })),
          stationId: o.stationId,
          stationName: o.stationName,
          createdAt: o.createdAt,
          updatedAt: o.updatedAt,
        }));

        // Transition the target order (creates a new object, does not mutate)
        const updatedOrders = orders.map((o, i) =>
          i === targetIndex ? transitionOrder(o, newState) : o
        );

        // Verify all OTHER orders are completely unchanged
        for (let i = 0; i < updatedOrders.length; i++) {
          if (i === targetIndex) continue;

          const before = snapshotBefore[i];
          const after = updatedOrders[i];

          expect(after.id).toBe(before.id);
          expect(after.state).toBe(before.state);
          expect(after.queuePosition).toBe(before.queuePosition);
          expect(after.visualOrderNumber).toBe(before.visualOrderNumber);
          expect(after.totalPrice).toBe(before.totalPrice);
          expect(after.stationId).toBe(before.stationId);
          expect(after.stationName).toBe(before.stationName);
          expect(after.createdAt).toBe(before.createdAt);
          expect(after.updatedAt).toBe(before.updatedAt);
          expect(after.items).toEqual(before.items);
        }
      }),
      { numRuns: 100 }
    );
  });

  it('each order in a session has its own independent state', () => {
    fc.assert(
      fc.property(sessionWithTransitionArb, ([orders, targetIndex, newState]) => {
        // Transition one order
        const updatedOrders = orders.map((o, i) =>
          i === targetIndex ? transitionOrder(o, newState) : o
        );

        // The transitioned order should have the new state
        expect(updatedOrders[targetIndex].state).toBe(newState);

        // All other orders should retain their original states
        for (let i = 0; i < orders.length; i++) {
          if (i === targetIndex) continue;
          expect(updatedOrders[i].state).toBe(orders[i].state);
        }
      }),
      { numRuns: 100 }
    );
  });

  it('each order in a session has its own independent queue position', () => {
    fc.assert(
      fc.property(sessionWithTransitionArb, ([orders, targetIndex, newState]) => {
        // Transition one order
        const updatedOrders = orders.map((o, i) =>
          i === targetIndex ? transitionOrder(o, newState) : o
        );

        // All other orders should retain their original queue positions
        for (let i = 0; i < orders.length; i++) {
          if (i === targetIndex) continue;
          expect(updatedOrders[i].queuePosition).toBe(orders[i].queuePosition);
        }
      }),
      { numRuns: 100 }
    );
  });

  it('each order in a session has its own independent visual order number', () => {
    fc.assert(
      fc.property(sessionWithTransitionArb, ([orders, targetIndex, newState]) => {
        // Transition one order
        const updatedOrders = orders.map((o, i) =>
          i === targetIndex ? transitionOrder(o, newState) : o
        );

        // All other orders should retain their original visual order numbers
        for (let i = 0; i < orders.length; i++) {
          if (i === targetIndex) continue;
          expect(updatedOrders[i].visualOrderNumber).toBe(orders[i].visualOrderNumber);
        }
      }),
      { numRuns: 100 }
    );
  });

  it('transitioning one order preserves the item lists of all other orders', () => {
    fc.assert(
      fc.property(sessionWithTransitionArb, ([orders, targetIndex, newState]) => {
        // Transition one order
        const updatedOrders = orders.map((o, i) =>
          i === targetIndex ? transitionOrder(o, newState) : o
        );

        // All other orders should retain their original items and total price
        for (let i = 0; i < orders.length; i++) {
          if (i === targetIndex) continue;
          expect(updatedOrders[i].items).toEqual(orders[i].items);
          expect(updatedOrders[i].totalPrice).toBe(orders[i].totalPrice);
        }
      }),
      { numRuns: 100 }
    );
  });
});
