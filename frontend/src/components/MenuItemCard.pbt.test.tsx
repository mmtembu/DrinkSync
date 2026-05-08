import { describe, it, expect, vi, afterEach } from 'vitest';
import { render, cleanup, fireEvent } from '@testing-library/react';
import * as fc from 'fast-check';
import { MenuItemCard } from './MenuItemCard';

afterEach(() => {
  cleanup();
});

/**
 * Property 2: Unavailable items cannot be added to an order
 *
 * For any menu item (Spirit_Item, Mixer_Item, or Premade_Item) with
 * `available = false`, the rendered MenuItemCard SHALL have a disabled
 * button that prevents the onSelect callback from being invoked.
 * Clicking the disabled item SHALL NOT trigger the selection handler,
 * ensuring the order remains unchanged.
 *
 * **Validates: Requirements 2.5**
 */
describe('Property 2: Unavailable items cannot be added to an order', () => {
  // Generator for realistic menu item names
  const nameArb = fc
    .stringMatching(/^[A-Za-z][A-Za-z0-9 ]{0,29}$/)
    .map((s) => s.trimEnd())
    .filter((s) => s.length > 0);

  // Generator for a positive price (menu items have price > 0)
  const priceArb = fc
    .double({ min: 0.01, max: 9999.99, noNaN: true })
    .map((p) => Math.round(p * 100) / 100);

  // Generator for a non-empty description (for premade items)
  const descriptionArb = fc
    .stringMatching(/^[A-Za-z][A-Za-z0-9 ,.-]{0,99}$/)
    .map((s) => s.trimEnd())
    .filter((s) => s.length > 0);

  it('unavailable Spirit_Item: button is disabled and onSelect is not called', () => {
    fc.assert(
      fc.property(nameArb, priceArb, (name, price) => {
        cleanup();
        const onSelect = vi.fn();
        const { container } = render(
          <MenuItemCard name={name} price={price} available={false} onSelect={onSelect} />
        );

        const button = container.querySelector('button');
        expect(button).not.toBeNull();
        expect(button!.disabled).toBe(true);

        // Attempt to click the disabled button
        fireEvent.click(button!);
        expect(onSelect).not.toHaveBeenCalled();
      }),
      { numRuns: 100 }
    );
  });

  it('unavailable Mixer_Item: button is disabled and onSelect is not called', () => {
    fc.assert(
      fc.property(nameArb, priceArb, (name, price) => {
        cleanup();
        const onSelect = vi.fn();
        const { container } = render(
          <MenuItemCard name={name} price={price} available={false} onSelect={onSelect} />
        );

        const button = container.querySelector('button');
        expect(button).not.toBeNull();
        expect(button!.disabled).toBe(true);

        fireEvent.click(button!);
        expect(onSelect).not.toHaveBeenCalled();
      }),
      { numRuns: 100 }
    );
  });

  it('unavailable Premade_Item: button is disabled and onSelect is not called', () => {
    fc.assert(
      fc.property(nameArb, descriptionArb, priceArb, (name, description, price) => {
        cleanup();
        const onSelect = vi.fn();
        const { container } = render(
          <MenuItemCard
            name={name}
            price={price}
            description={description}
            available={false}
            onSelect={onSelect}
          />
        );

        const button = container.querySelector('button');
        expect(button).not.toBeNull();
        expect(button!.disabled).toBe(true);

        fireEvent.click(button!);
        expect(onSelect).not.toHaveBeenCalled();
      }),
      { numRuns: 100 }
    );
  });

  it('available items allow selection (contrast property)', () => {
    fc.assert(
      fc.property(nameArb, priceArb, (name, price) => {
        cleanup();
        const onSelect = vi.fn();
        const { container } = render(
          <MenuItemCard name={name} price={price} available={true} onSelect={onSelect} />
        );

        const button = container.querySelector('button');
        expect(button).not.toBeNull();
        expect(button!.disabled).toBe(false);

        fireEvent.click(button!);
        expect(onSelect).toHaveBeenCalledTimes(1);
      }),
      { numRuns: 100 }
    );
  });
});
