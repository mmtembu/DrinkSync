/** Design tokens for the DrinkSync theme system */

export interface ThemeTokens {
  // Backgrounds
  bgPrimary: string;
  bgSecondary: string;
  bgTertiary: string;
  bgCard: string;
  bgCardHover: string;
  bgGlass: string;
  bgGlassStrong: string;
  bgInput: string;

  // Text
  textPrimary: string;
  textSecondary: string;
  textMuted: string;
  textInverse: string;

  // Brand colors
  primary: string;
  primaryLight: string;
  primaryDark: string;
  success: string;
  successLight: string;
  successDark: string;
  warning: string;
  warningLight: string;
  danger: string;
  dangerLight: string;

  // Borders
  border: string;
  borderLight: string;
  borderFocus: string;

  // Shadows
  shadowSm: string;
  shadowMd: string;
  shadowLg: string;
  shadowGlow: string;

  // Glass
  glassBlur: string;
  glassBorder: string;
}

export const lightTokens: ThemeTokens = {
  bgPrimary: '#ffffff',
  bgSecondary: '#f9fafb',
  bgTertiary: '#f3f4f6',
  bgCard: 'rgba(255, 255, 255, 0.8)',
  bgCardHover: 'rgba(255, 255, 255, 0.95)',
  bgGlass: 'rgba(255, 255, 255, 0.6)',
  bgGlassStrong: 'rgba(255, 255, 255, 0.85)',
  bgInput: '#ffffff',

  textPrimary: '#111827',
  textSecondary: '#374151',
  textMuted: '#6b7280',
  textInverse: '#ffffff',

  primary: '#2563eb',
  primaryLight: '#eff6ff',
  primaryDark: '#1e40af',
  success: '#059669',
  successLight: '#ecfdf5',
  successDark: '#065f46',
  warning: '#f59e0b',
  warningLight: '#fffbeb',
  danger: '#ef4444',
  dangerLight: '#fef2f2',

  border: '#e5e7eb',
  borderLight: '#f3f4f6',
  borderFocus: '#2563eb',

  shadowSm: '0 1px 2px rgba(0, 0, 0, 0.05)',
  shadowMd: '0 4px 12px rgba(0, 0, 0, 0.08)',
  shadowLg: '0 8px 24px rgba(0, 0, 0, 0.12)',
  shadowGlow: '0 0 20px rgba(37, 99, 235, 0.15)',

  glassBlur: 'blur(12px)',
  glassBorder: 'rgba(255, 255, 255, 0.3)',
};

export const darkTokens: ThemeTokens = {
  bgPrimary: '#0f172a',
  bgSecondary: '#1e293b',
  bgTertiary: '#334155',
  bgCard: 'rgba(30, 41, 59, 0.8)',
  bgCardHover: 'rgba(30, 41, 59, 0.95)',
  bgGlass: 'rgba(15, 23, 42, 0.6)',
  bgGlassStrong: 'rgba(15, 23, 42, 0.85)',
  bgInput: '#1e293b',

  textPrimary: '#f1f5f9',
  textSecondary: '#cbd5e1',
  textMuted: '#94a3b8',
  textInverse: '#0f172a',

  primary: '#3b82f6',
  primaryLight: 'rgba(59, 130, 246, 0.15)',
  primaryDark: '#60a5fa',
  success: '#10b981',
  successLight: 'rgba(16, 185, 129, 0.15)',
  successDark: '#34d399',
  warning: '#fbbf24',
  warningLight: 'rgba(251, 191, 36, 0.15)',
  danger: '#f87171',
  dangerLight: 'rgba(248, 113, 113, 0.15)',

  border: '#334155',
  borderLight: '#1e293b',
  borderFocus: '#3b82f6',

  shadowSm: '0 1px 2px rgba(0, 0, 0, 0.3)',
  shadowMd: '0 4px 12px rgba(0, 0, 0, 0.4)',
  shadowLg: '0 8px 24px rgba(0, 0, 0, 0.5)',
  shadowGlow: '0 0 20px rgba(59, 130, 246, 0.25)',

  glassBlur: 'blur(12px)',
  glassBorder: 'rgba(255, 255, 255, 0.08)',
};
