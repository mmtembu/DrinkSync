import { describe, it, expect, afterEach } from 'vitest';
import { render, cleanup } from '@testing-library/react';
import * as fc from 'fast-check';
import { MenuItemCard } from './MenuItemCard';

afterEach(() => {
  cleanup();
});

/**
 * Property 1: Menu item rendering includes all required fields
 *
 * For any menu item (Spirit_Item, Mixer_Item, or Premade_Item), the rendered
 * output SHALL contain the item's name and price. Additionally, for Premade_Items,
 * the rendered output SHALL also contain the item's description.
 *
 * **Validates: Requirements 2.2, 2.3, 2.4**
 */
describe('Property 1: Menu item rendering includes all required fields', () => {
  // Generator for realistic menu item names — no trailing spaces, distinct from price text
  const nameArb = fc
    .stringMatching(/^[A-Za-z][A-Za-z0-9 ]{0,29}$/)
    .map((s) => s.trimEnd())
    .filter((s) => s.length > 0);

  // Generator for a positive price (menu items have price > 0)
  const priceArb = fc
    .double({ min: 0.01, max: 9999.99, noNaN: true })
    .map((p) => Math.round(p * 100) / 100);

  // Generator for a non-empty description — distinct from name by using different character set
  const descriptionArb = fc
    .stringMatching(/^[A-Za-z][A-Za-z0-9 ,.-]{0,99}$/)
    .map((s) => s.trimEnd())
    .filter((s) => s.length > 0);

  it('Spirit_Item: rendered output contains name and price', () => {
    fc.assert(
      fc.property(nameArb, priceArb, fc.boolean(), (name, price, available) => {
        cleanup();
        const { container } = render(
          <MenuItemCard name={name} price={price} available={available} />
        );

        const textContent = container.textContent ?? '';

        // Name must be present in the rendered output
        expect(textContent).toContain(name);

        // Price must be present, formatted as R{price}
        expect(textContent).toContain(`R${price.toFixed(2)}`);
      }),
      { numRuns: 100 }
    );
  });

  it('Mixer_Item: rendered output contains name and price', () => {
    fc.assert(
      fc.property(nameArb, priceArb, fc.boolean(), (name, price, available) => {
        cleanup();
        const { container } = render(
          <MenuItemCard name={name} price={price} available={available} />
        );

        const textContent = container.textContent ?? '';

        expect(textContent).toContain(name);
        expect(textContent).toContain(`R${price.toFixed(2)}`);
      }),
      { numRuns: 100 }
    );
  });

  it('Premade_Item: rendered output contains name, description, and price', () => {
    fc.assert(
      fc.property(
        nameArb,
        descriptionArb,
        priceArb,
        fc.boolean(),
        (name, description, price, available) => {
          cleanup();
          const { container } = render(
            <MenuItemCard
              name={name}
              price={price}
              description={description}
              available={available}
            />
          );

          const textContent = container.textContent ?? '';

          // Name must be present
          expect(textContent).toContain(name);

          // Price must be present
          expect(textContent).toContain(`R${price.toFixed(2)}`);

          // Description must be present for premade items
          expect(textContent).toContain(description);
        }
      ),
      { numRuns: 100 }
    );
  });
});
