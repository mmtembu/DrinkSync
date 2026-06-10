import { test as base, expect } from '@playwright/test';
import { TestApi } from './api';

/**
 * Extended Playwright test fixtures for Smart Event Bar E2E tests.
 *
 * Provides:
 * - `api`: A TestApi instance for direct backend calls (test data setup)
 * - `stationId`: The default test station ID (assumes station 1 exists in dev seed data)
 */
export const test = base.extend<{
  api: TestApi;
  stationId: number;
}>({
  /* eslint-disable react-hooks/rules-of-hooks, no-empty-pattern */
  api: async ({ request }, use) => {
    await use(new TestApi(request));
  },
  stationId: async ({}, use) => {
    // Default test station — assumes dev seed data has station with ID 1
    await use(1);
  },
  /* eslint-enable react-hooks/rules-of-hooks, no-empty-pattern */
});

export { expect };

/**
 * Helper to set a session ID in localStorage before navigating.
 * Useful for tests that need to pre-seed a session.
 */
export async function setSessionInStorage(page: import('@playwright/test').Page, sessionId: string) {
  await page.evaluate((sid) => {
    localStorage.setItem('smart-event-bar-session-id', sid);
  }, sessionId);
}

/**
 * Helper to clear the session from localStorage.
 */
export async function clearSession(page: import('@playwright/test').Page) {
  await page.evaluate(() => {
    localStorage.removeItem('smart-event-bar-session-id');
  });
}

/**
 * Helper to get the current session ID from localStorage.
 */
export async function getSessionFromStorage(page: import('@playwright/test').Page): Promise<string | null> {
  return page.evaluate(() => localStorage.getItem('smart-event-bar-session-id'));
}

/**
 * Wait for the menu to be fully loaded on the station page.
 */
export async function waitForMenuLoaded(page: import('@playwright/test').Page) {
  // Wait for at least one section heading (Spirits, Mixers, or Ready-to-Serve)
  await page.waitForSelector('h2', { timeout: 10_000 });
}

/**
 * Helper to build a premade item order item payload from menu data.
 */
export function premadeOrderItem(premadeItemId: number, quantity = 1) {
  return {
    itemType: 'PREMADE' as const,
    premadeItemId,
    quantity,
  };
}

/**
 * Helper to build a custom drink order item payload from menu data.
 */
export function customDrinkOrderItem(
  spiritItemId: number,
  mixerItemId: number,
  cupOption: 'REUSE_CUP' | 'NEW_CUP' = 'REUSE_CUP',
  quantity = 1,
) {
  return {
    itemType: 'CUSTOM_DRINK' as const,
    spiritItemId,
    mixerItemId,
    cupOption,
    quantity,
  };
}
