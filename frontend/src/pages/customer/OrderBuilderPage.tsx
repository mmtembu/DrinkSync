import { useParams, useNavigate, Navigate, useSearchParams } from 'react-router-dom';
import { useStationMenu } from '../../hooks/useStationMenu';
import { useSession } from '../../hooks/useSession';
import { CupOptionSelector } from '../../components/CupOptionSelector';
import { ToggleButtonGroup } from '../../components/ToggleButtonGroup';
import type { SelectedItemWithQty } from '../../components/ToggleButtonGroup';
import { Skeleton } from '../../components/Skeleton';
import { BackButton } from '../../components/BackButton';
import { FloatingDraftOrders } from '../../components/FloatingDraftOrders';
import { useState, useMemo, useEffect } from 'react';
import { orderApi } from '../../services/orderApi';
import { CupOption, OrderItemType } from '../../types/order';
import { getErrorMessage } from '../../types/apiError';
import type { OrderItemRequest } from '../../types/order';
import type { SpiritItem, MixerItem, PremadeItem } from '../../types/menu';
import {
  styles,
  addDrinkButtonStyles,
  checkoutButtonStyles,
} from './OrderBuilderPage.styles';

// --- Cart item model (discriminated union) ---

interface CustomDrinkCartItem {
  key: string;
  itemType: typeof OrderItemType.CUSTOM_DRINK;
  spirits: SpiritItem[];
  mixers: MixerItem[];
  cupOption: CupOption;
  quantity: number;
  unitPrice: number;
}

interface PremadeCartItem {
  key: string;
  itemType: typeof OrderItemType.PREMADE;
  premade: PremadeItem;
  quantity: number;
  unitPrice: number;
}

type CartItem = CustomDrinkCartItem | PremadeCartItem;

function cartItemLabel(item: CartItem): string {
  if (item.itemType === OrderItemType.CUSTOM_DRINK) {
    const cupLabel = item.cupOption === CupOption.NEW_CUP ? 'New Cup' : 'Reuse Cup';
    const spiritNames = item.spirits.map((s) => s.name).join(' + ');
    const mixerNames = item.mixers.map((m) => m.name).join(' + ');
    return `${spiritNames} + ${mixerNames} (${cupLabel})`;
  }
  return item.premade.name;
}

function toOrderItemRequest(item: CartItem): OrderItemRequest {
  if (item.itemType === OrderItemType.CUSTOM_DRINK) {
    return {
      itemType: item.itemType,
      spiritItemIds: item.spirits.map((s) => s.id),
      mixerItemIds: item.mixers.map((m) => m.id),
      cupOption: item.cupOption,
      quantity: item.quantity,
    };
  }
  return {
    itemType: item.itemType,
    premadeItemId: item.premade.id,
    quantity: item.quantity,
  };
}

/** Generate a stable key for deduplication in the cart */
function customDrinkKey(spirits: SpiritItem[], mixers: MixerItem[], cup: CupOption): string {
  const spiritIds = spirits.map((s) => s.id).sort().join(',');
  const mixerIds = mixers.map((m) => m.id).sort().join(',');
  return `custom-${spiritIds}-${mixerIds}-${cup}`;
}

// --- Component ---

export function OrderBuilderPage() {
  const { stationId } = useParams<{ stationId: string }>();

  if (!stationId || isNaN(Number(stationId))) {
    return <Navigate to="/offline" replace />;
  }

  const stId = parseInt(stationId, 10);

  return <OrderBuilderContent stationId={stId} />;
}

function OrderBuilderContent({ stationId }: { stationId: number }) {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const draftOrderId = searchParams.get('draftOrderId');
  const { menu } = useStationMenu(stationId);
  const { ensureSession, sessionId } = useSession(stationId);

  const [cart, setCart] = useState<CartItem[]>([]);
  const [editingOrderId, setEditingOrderId] = useState<number | null>(null);
  const [selectedSpirits, setSelectedSpirits] = useState<SelectedItemWithQty<SpiritItem>[]>([]);
  const [selectedMixers, setSelectedMixers] = useState<SelectedItemWithQty<MixerItem>[]>([]);
  const [selectedCup, setSelectedCup] = useState<CupOption | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [bouncingKey, setBouncingKey] = useState<string | null>(null);

  const cupPrice = menu?.cupPrice ?? 0;

  // Load draft order items when navigating with draftOrderId
  useEffect(() => {
    if (!draftOrderId || !sessionId || !menu) return;
    const orderId = parseInt(draftOrderId, 10);
    if (isNaN(orderId)) return;

    // Don't reload if we're already editing this order
    if (editingOrderId === orderId) return;

    orderApi.getOrder(orderId, sessionId).then((order) => {
      if (order.state !== 'DRAFT') return;

      setEditingOrderId(orderId);
      const loadedCart: CartItem[] = [];

      for (const item of order.items) {
        if (item.itemType === OrderItemType.CUSTOM_DRINK && item.spiritItems && item.mixerItems) {
          const cupLabel = item.cupOption === CupOption.NEW_CUP ? CupOption.NEW_CUP : CupOption.REUSE_CUP;
          const spiritIds = item.spiritItems.map((s) => s.id).sort().join(',');
          const mixerIds = item.mixerItems.map((m) => m.id).sort().join(',');
          const key = `custom-${spiritIds}-${mixerIds}-${cupLabel}`;

          // Map API spirit/mixer refs to full menu items
          const spirits = item.spiritItems
            .map((ref) => menu.spirits.find((s) => s.id === ref.id))
            .filter(Boolean) as SpiritItem[];
          const mixers = item.mixerItems
            .map((ref) => menu.mixers.find((m) => m.id === ref.id))
            .filter(Boolean) as MixerItem[];

          loadedCart.push({
            key,
            itemType: OrderItemType.CUSTOM_DRINK,
            spirits,
            mixers,
            cupOption: cupLabel,
            quantity: item.quantity,
            unitPrice: item.unitPrice,
          });
        } else if (item.itemType === OrderItemType.PREMADE && item.premadeItemId) {
          const premade = menu.premades.find((p) => p.id === item.premadeItemId);
          if (premade) {
            loadedCart.push({
              key: `premade-${premade.id}`,
              itemType: OrderItemType.PREMADE,
              premade,
              quantity: item.quantity,
              unitPrice: item.unitPrice,
            });
          }
        }
      }

      setCart(loadedCart);
    }).catch(() => {
      // Failed to load draft — just show empty builder
    });
  }, [draftOrderId, sessionId, menu, editingOrderId]);

  const total = useMemo(
    () => cart.reduce((sum, item) => sum + item.unitPrice * item.quantity, 0),
    [cart],
  );

  // Running subtotal for the drink being built (accounts for per-item quantities)
  const drinkSubtotal = useMemo(() => {
    const spiritTotal = selectedSpirits.reduce((sum, s) => sum + s.item.price * s.quantity, 0);
    const mixerTotal = selectedMixers.reduce((sum, m) => sum + m.item.price * m.quantity, 0);
    const cupCost = selectedCup === CupOption.NEW_CUP ? cupPrice : 0;
    return spiritTotal + mixerTotal + cupCost;
  }, [selectedSpirits, selectedMixers, selectedCup, cupPrice]);

  const availablePremades = useMemo(
    () => menu?.premades.filter((p) => p.available) ?? [],
    [menu?.premades],
  );

  const triggerBounce = (key: string) => {
    setBouncingKey(key);
    setTimeout(() => setBouncingKey(null), 300);
  };

  // --- Cart helpers ---

  const addToCart = (key: string, newItem: CartItem) => {
    setCart((prev) => {
      const existing = prev.find((i) => i.key === key);
      if (existing) {
        return prev.map((i) =>
          i.key === key ? { ...i, quantity: i.quantity + newItem.quantity } : i,
        );
      }
      return [...prev, newItem];
    });
  };

  const addCustomDrink = () => {
    if (selectedSpirits.length === 0 || selectedMixers.length === 0 || !selectedCup) return;

    const cupCost = selectedCup === CupOption.NEW_CUP ? cupPrice : 0;
    const spiritTotal = selectedSpirits.reduce((sum, s) => sum + s.item.price * s.quantity, 0);
    const mixerTotal = selectedMixers.reduce((sum, m) => sum + m.item.price * m.quantity, 0);
    const unitPrice = spiritTotal + mixerTotal + cupCost;

    // Expand quantities into the spirits/mixers arrays for the cart item
    const spiritItems = selectedSpirits.flatMap((s) => Array(s.quantity).fill(s.item));
    const mixerItems = selectedMixers.flatMap((m) => Array(m.quantity).fill(m.item));
    const key = customDrinkKey(spiritItems, mixerItems, selectedCup);

    addToCart(key, {
      key,
      itemType: OrderItemType.CUSTOM_DRINK,
      spirits: spiritItems,
      mixers: mixerItems,
      cupOption: selectedCup,
      quantity: 1,
      unitPrice,
    });

    setSelectedSpirits([]);
    setSelectedMixers([]);
    setSelectedCup(null);
  };

  const addPremade = (premade: PremadeItem) => {
    const key = `premade-${premade.id}`;
    addToCart(key, {
      key,
      itemType: OrderItemType.PREMADE,
      premade,
      quantity: 1,
      unitPrice: premade.price,
    });
  };

  const updateQuantity = (key: string, delta: number) => {
    triggerBounce(key);
    setCart((prev) =>
      prev.map((i) => (i.key === key ? { ...i, quantity: Math.max(1, i.quantity + delta) } : i)),
    );
  };

  const removeItem = (key: string) => {
    setCart((prev) => prev.filter((i) => i.key !== key));
  };

  // --- Checkout ---

  const handleCheckout = async () => {
    if (cart.length === 0) return;
    setLoading(true);
    setError(null);
    try {
      const sid = await ensureSession(stationId);
      if (!sid) {
        setError('Could not start a session. Please try again.');
        return;
      }

      const items: OrderItemRequest[] = cart.map(toOrderItemRequest);

      let order;
      if (editingOrderId) {
        // Update existing draft order items, then checkout
        order = await orderApi.updateOrderItems(editingOrderId, items, sid);
        navigate(`/station/${stationId}/checkout/${editingOrderId}`);
      } else {
        // Create a new order
        order = await orderApi.createOrder(stationId, items, sid);
        navigate(`/station/${stationId}/checkout/${order.id}`);
      }
    } catch (e: unknown) {
      setError(getErrorMessage(e, 'Failed to create order'));
    } finally {
      setLoading(false);
    }
  };

  // --- Render ---

  if (!menu) {
    return (
      <div className="page-container">
        <Skeleton variant="heading" width="60%" />
        <div style={{ marginTop: 16 }}>
          <Skeleton variant="card" count={2} />
        </div>
        <Skeleton variant="button" />
      </div>
    );
  }

  const canAddDrink = selectedSpirits.length > 0 && selectedMixers.length > 0 && selectedCup !== null;
  const canCheckout = cart.length > 0 && !loading;

  return (
    <div className="page-container">
      <BackButton to={`/station/${stationId}`} label="Back to Menu" />
      <h1 style={styles.heading}>
        {editingOrderId ? `✏️ Editing Order #${editingOrderId}` : 'Build Your Order'}
      </h1>
      {editingOrderId && (
        <p style={{ color: 'var(--text-muted)', fontSize: '0.85rem', marginBottom: 12 }}>
          Modify your items below, then checkout to update this order.
        </p>
      )}

      {/* Custom Drink Builder */}
      <section style={styles.section} aria-label="Build a custom drink" className="card-fade-in">
        <h2 style={styles.sectionTitle}>🍹 Custom Drink</h2>

        <ToggleButtonGroup
          label="Spirits (tap to select multiple)"
          items={menu.spirits}
          selected={selectedSpirits}
          onSelectionChange={setSelectedSpirits}
          formatLabel={(s) => `${s.name} (R${s.price.toFixed(2)})`}
          emptyMessage="No spirits available"
        />

        <ToggleButtonGroup
          label="Mixers (tap to select multiple)"
          items={menu.mixers}
          selected={selectedMixers}
          onSelectionChange={setSelectedMixers}
          formatLabel={(m) => `${m.name} (R${m.price.toFixed(2)})`}
          emptyMessage="No mixers available"
        />

        <CupOptionSelector selected={selectedCup} cupPrice={cupPrice} onSelect={setSelectedCup} />

        {/* Running subtotal */}
        {(selectedSpirits.length > 0 || selectedMixers.length > 0) && (
          <div style={{ fontSize: '0.9rem', color: 'var(--text-secondary)', marginBottom: 8, fontWeight: 500 }}>
            Drink subtotal: <span style={{ color: 'var(--color-success)', fontWeight: 600 }}>R{drinkSubtotal.toFixed(2)}</span>
          </div>
        )}

        <button
          onClick={addCustomDrink}
          disabled={!canAddDrink}
          style={canAddDrink ? addDrinkButtonStyles.enabled : addDrinkButtonStyles.disabled}
          className={canAddDrink ? 'btn' : ''}
        >
          Add Custom Drink
        </button>
      </section>

      {/* Premade Items — inline quantity controls */}
      <section style={styles.premadeSection} aria-label="Ready-to-serve drinks">
        <h2 style={styles.sectionTitle}>🥫 Ready-to-Serve</h2>
        {availablePremades.length === 0 ? (
          <p style={styles.emptyText}>No ready-to-serve items available</p>
        ) : (
          <div style={styles.premadeList}>
            {availablePremades.map((p) => {
              const key = `premade-${p.id}`;
              const cartItem = cart.find((i) => i.key === key);
              const qty = cartItem?.quantity ?? 0;

              const handleIncrement = () => {
                triggerBounce(key);
                if (qty === 0) {
                  addPremade(p);
                } else {
                  updateQuantity(key, 1);
                }
              };

              const handleDecrement = () => {
                triggerBounce(key);
                if (qty <= 1) {
                  removeItem(key);
                } else {
                  updateQuantity(key, -1);
                }
              };

              return (
                <div
                  key={p.id}
                  style={{
                    ...styles.premadeButton,
                    alignItems: 'center',
                    border: qty > 0 ? '2px solid var(--color-primary)' : '1px solid var(--border-color)',
                    backgroundColor: qty > 0 ? 'var(--color-primary-light)' : 'var(--bg-card)',
                  }}
                >
                  <div style={{ flex: 1 }}>
                    <div style={styles.premadeName}>{p.name}</div>
                    <div style={styles.premadeDesc}>{p.description}</div>
                    <div style={styles.premadePrice}>R{p.price.toFixed(2)}</div>
                  </div>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginLeft: 12 }}>
                    {qty > 0 && (
                      <button
                        onClick={handleDecrement}
                        aria-label={`Decrease ${p.name} quantity`}
                        className="btn"
                        style={{
                          width: 32,
                          height: 32,
                          borderRadius: '50%',
                          border: '1px solid var(--border-color)',
                          backgroundColor: 'var(--bg-card)',
                          color: 'var(--text-primary)',
                          padding: 0,
                          fontSize: '1.1rem',
                          fontWeight: 600,
                        }}
                      >
                        −
                      </button>
                    )}
                    {qty > 0 && (
                      <span
                        className={bouncingKey === key ? 'number-bounce' : ''}
                        style={{ fontWeight: 700, fontSize: '1rem', minWidth: 20, textAlign: 'center' }}
                      >
                        {qty}
                      </span>
                    )}
                    <button
                      onClick={handleIncrement}
                      aria-label={`Add ${p.name} to order`}
                      className="btn"
                      style={{
                        width: 32,
                        height: 32,
                        borderRadius: '50%',
                        border: 'none',
                        backgroundColor: 'var(--color-primary)',
                        color: 'var(--text-inverse)',
                        padding: 0,
                        fontSize: '1.1rem',
                        fontWeight: 600,
                      }}
                    >
                      +
                    </button>
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </section>

      {/* Cart */}
      {cart.length > 0 && (
        <section style={styles.cartSection} aria-label="Your order summary" className="card-fade-in">
          <h2 style={styles.sectionTitle}>🛒 Your Order</h2>
          {cart.map((item) => {
            const label = cartItemLabel(item);
            return (
              <div key={item.key} style={styles.cartRow} className="slide-in-right">
                <div>
                  <div style={styles.cartItemName}>{label}</div>
                  <div style={styles.cartItemPrice}>R{item.unitPrice.toFixed(2)} each</div>
                </div>
                <div style={styles.cartControls}>
                  <button onClick={() => updateQuantity(item.key, -1)} aria-label={`Decrease quantity of ${label}`} style={styles.qtyButton}>−</button>
                  <span
                    className={bouncingKey === item.key ? 'number-bounce' : ''}
                    style={styles.qtyDisplay}
                  >
                    {item.quantity}
                  </span>
                  <button onClick={() => updateQuantity(item.key, 1)} aria-label={`Increase quantity of ${label}`} style={styles.qtyButton}>+</button>
                  <button onClick={() => removeItem(item.key)} aria-label={`Remove ${label}`} style={styles.removeButton}>✕</button>
                </div>
              </div>
            );
          })}
          <div style={styles.totalRow}>
            <span>Total</span>
            <span style={{ color: 'var(--color-success)' }}>R{total.toFixed(2)}</span>
          </div>
        </section>
      )}

      {error && <p role="alert" style={styles.errorText}>{error}</p>}

      <button
        onClick={handleCheckout}
        disabled={!canCheckout}
        style={canCheckout ? checkoutButtonStyles.enabled : checkoutButtonStyles.disabled}
      >
        {loading ? 'Creating Order...' : `Checkout (R${total.toFixed(2)})`}
      </button>

      <FloatingDraftOrders stationId={stationId} sessionId={sessionId} />
    </div>
  );
}
