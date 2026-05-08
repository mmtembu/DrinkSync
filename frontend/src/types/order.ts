export const OrderState = {
  DRAFT: 'DRAFT',
  AWAITING_PAYMENT: 'AWAITING_PAYMENT',
  PAID: 'PAID',
  PREPARING: 'PREPARING',
  READY: 'READY',
  COLLECTED: 'COLLECTED',
  CANCELLED: 'CANCELLED',
  EXPIRED: 'EXPIRED',
} as const;

export type OrderState = (typeof OrderState)[keyof typeof OrderState];

export const CupOption = {
  REUSE_CUP: 'REUSE_CUP',
  NEW_CUP: 'NEW_CUP',
} as const;

export type CupOption = (typeof CupOption)[keyof typeof CupOption];

export const OrderItemType = {
  CUSTOM_DRINK: 'CUSTOM_DRINK',
  PREMADE: 'PREMADE',
} as const;

export type OrderItemType = (typeof OrderItemType)[keyof typeof OrderItemType];

export interface OrderItem {
  id: number;
  itemType: OrderItemType;
  spiritItems?: { id: number; name: string }[];
  mixerItems?: { id: number; name: string }[];
  premadeItemId?: number;
  premadeItemName?: string;
  cupOption?: CupOption;
  cupPrice?: number;
  quantity: number;
  unitPrice: number;
}

export interface Order {
  id: number;
  stationId: number;
  stationName: string;
  visualOrderNumber?: string;
  state: OrderState;
  queuePosition?: number;
  totalPrice: number;
  pickupWindowStart?: string;
  createdAt: string;
  updatedAt: string;
  items: OrderItem[];
}

export interface OrderItemRequest {
  itemType: OrderItemType;
  spiritItemIds?: number[];
  mixerItemIds?: number[];
  premadeItemId?: number;
  cupOption?: CupOption;
  quantity: number;
}

export const ORDER_STATE_LABELS: Record<OrderState, string> = {
  [OrderState.DRAFT]: 'Draft',
  [OrderState.AWAITING_PAYMENT]: 'Awaiting Payment',
  [OrderState.PAID]: 'Paid',
  [OrderState.PREPARING]: 'Preparing',
  [OrderState.READY]: 'Ready for Pickup',
  [OrderState.COLLECTED]: 'Collected',
  [OrderState.CANCELLED]: 'Cancelled',
  [OrderState.EXPIRED]: 'Expired',
};

export const TERMINAL_STATES: OrderState[] = [
  OrderState.COLLECTED,
  OrderState.CANCELLED,
  OrderState.EXPIRED,
];

export function isTerminalState(state: OrderState): boolean {
  return TERMINAL_STATES.includes(state);
}

export const STATE_PRIORITY: Record<OrderState, number> = {
  [OrderState.PAID]: 0,
  [OrderState.PREPARING]: 1,
  [OrderState.READY]: 2,
  [OrderState.COLLECTED]: 3,
  [OrderState.DRAFT]: 4,
  [OrderState.AWAITING_PAYMENT]: 5,
  [OrderState.CANCELLED]: 6,
  [OrderState.EXPIRED]: 7,
};

export function sortOrdersByStatePriority(orders: Order[]): Order[] {
  return [...orders].sort((a, b) => STATE_PRIORITY[a.state] - STATE_PRIORITY[b.state]);
}

/**
 * Filters orders for the vendor dashboard view.
 *
 * - CANCELLED orders are excluded entirely (Req 13.5)
 * - EXPIRED orders are separated into their own section (Req 15.4)
 * - The main view shows all remaining orders: PAID, PREPARING, READY, COLLECTED,
 *   DRAFT, AWAITING_PAYMENT (Req 7.4)
 */
export interface DashboardFilterResult {
  mainOrders: Order[];
  expiredOrders: Order[];
}

export function filterDashboardOrders(orders: Order[]): DashboardFilterResult {
  const activeOrders = orders.filter((o) => o.state !== OrderState.CANCELLED);
  const expiredOrders = activeOrders.filter((o) => o.state === OrderState.EXPIRED);
  const mainOrders = activeOrders.filter((o) => o.state !== OrderState.EXPIRED);
  return { mainOrders, expiredOrders };
}
