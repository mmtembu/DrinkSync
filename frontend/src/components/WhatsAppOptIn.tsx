import { useState, useEffect } from 'react';

export interface WhatsAppOptInData {
  customerPhone: string;
  whatsappOptIn: boolean;
}

interface WhatsAppOptInProps {
  /** Initial phone number to pre-fill (e.g. from session storage) */
  initialPhone?: string;
  /** Initial opt-in state to pre-fill */
  initialOptIn?: boolean;
  /** Called whenever the phone or opt-in value changes */
  onChange: (data: WhatsAppOptInData) => void;
  /** External validation error message (e.g. from backend 400 response) */
  error?: string | null;
}

const COUNTRY_CODES = [
  { code: '+27', label: '🇿🇦 +27' },
  { code: '+1', label: '🇺🇸 +1' },
  { code: '+44', label: '🇬🇧 +44' },
  { code: '+61', label: '🇦🇺 +61' },
  { code: '+91', label: '🇮🇳 +91' },
  { code: '+49', label: '🇩🇪 +49' },
  { code: '+33', label: '🇫🇷 +33' },
  { code: '+81', label: '🇯🇵 +81' },
  { code: '+55', label: '🇧🇷 +55' },
  { code: '+234', label: '🇳🇬 +234' },
];

/**
 * Extracts the country code and local number from a full E.164 phone number.
 */
function parsePhone(fullPhone: string): { countryCode: string; localNumber: string } {
  if (!fullPhone || !fullPhone.startsWith('+')) {
    return { countryCode: '+27', localNumber: fullPhone || '' };
  }
  // Try to match known country codes (longest first)
  const sorted = [...COUNTRY_CODES].sort((a, b) => b.code.length - a.code.length);
  for (const { code } of sorted) {
    if (fullPhone.startsWith(code)) {
      return { countryCode: code, localNumber: fullPhone.slice(code.length) };
    }
  }
  return { countryCode: '+27', localNumber: fullPhone.slice(1) };
}

/**
 * Validates a phone number against E.164 format.
 * E.164: starts with +, followed by 1-15 digits (first digit non-zero).
 */
// eslint-disable-next-line react-refresh/only-export-components
export function validateE164(phoneNumber: string): boolean {
  return /^\+[1-9]\d{1,14}$/.test(phoneNumber);
}

/**
 * Validates phone number format client-side, returning an error message or null.
 */
function validatePhone(fullPhone: string): string | null {
  if (!fullPhone || fullPhone === '+') return null; // Empty is valid (optional field)
  if (!validateE164(fullPhone)) {
    return 'Please enter a valid phone number (e.g. +27821234567)';
  }
  return null;
}

export function WhatsAppOptIn({ initialPhone, initialOptIn, onChange, error }: WhatsAppOptInProps) {
  const { countryCode: initCode, localNumber: initLocal } = parsePhone(initialPhone || '');

  const [countryCode, setCountryCode] = useState(initCode);
  const [localNumber, setLocalNumber] = useState(initLocal);
  const [optIn, setOptIn] = useState(initialOptIn ?? false);
  const [localError, setLocalError] = useState<string | null>(null);

  // Sync initial values when they change (e.g. session data loads async)
  useEffect(() => {
    if (initialPhone) {
      const { countryCode: code, localNumber: num } = parsePhone(initialPhone);
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setCountryCode(code);
      setLocalNumber(num);
    }
  }, [initialPhone]);

  useEffect(() => {
    if (initialOptIn !== undefined) {
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setOptIn(initialOptIn);
    }
  }, [initialOptIn]);

  const fullPhone = localNumber ? `${countryCode}${localNumber}` : '';

  // Notify parent on changes
  useEffect(() => {
    onChange({ customerPhone: fullPhone, whatsappOptIn: optIn });
  }, [fullPhone, optIn]); // eslint-disable-line react-hooks/exhaustive-deps

  const handleLocalNumberChange = (value: string) => {
    // Strip non-digit characters
    const digits = value.replace(/\D/g, '');
    setLocalNumber(digits);
    // Clear local validation error on change
    if (localError) setLocalError(null);
  };

  const handleBlur = () => {
    if (fullPhone) {
      const err = validatePhone(fullPhone);
      setLocalError(err);
    } else {
      setLocalError(null);
    }
  };

  const displayError = error || localError;

  return (
    <div
      className="glass-card"
      style={{ padding: 16, marginBottom: 16 }}
      role="group"
      aria-labelledby="whatsapp-optin-heading"
    >
      <h3
        id="whatsapp-optin-heading"
        style={{ fontSize: '0.95rem', fontWeight: 600, marginBottom: 12, color: 'var(--text-primary)' }}
      >
        📱 WhatsApp Notifications
      </h3>

      <div style={{ marginBottom: 12 }}>
        <label
          htmlFor="whatsapp-phone"
          style={{ display: 'block', fontSize: '0.85rem', color: 'var(--text-secondary)', marginBottom: 4 }}
        >
          Phone number
        </label>
        <div style={{ display: 'flex', gap: 8 }}>
          <select
            value={countryCode}
            onChange={(e) => setCountryCode(e.target.value)}
            aria-label="Country code"
            style={{
              padding: '8px 4px',
              borderRadius: 'var(--radius-md)',
              border: '1px solid var(--border-color)',
              backgroundColor: 'var(--bg-card)',
              color: 'var(--text-primary)',
              fontSize: '0.9rem',
              minWidth: 80,
            }}
          >
            {COUNTRY_CODES.map(({ code, label }) => (
              <option key={code} value={code}>
                {label}
              </option>
            ))}
          </select>
          <input
            id="whatsapp-phone"
            type="tel"
            inputMode="numeric"
            placeholder="821234567"
            value={localNumber}
            onChange={(e) => handleLocalNumberChange(e.target.value)}
            onBlur={handleBlur}
            aria-describedby={displayError ? 'whatsapp-phone-error' : undefined}
            aria-invalid={!!displayError}
            style={{
              flex: 1,
              padding: '8px 12px',
              borderRadius: 'var(--radius-md)',
              border: `1px solid ${displayError ? 'var(--color-danger)' : 'var(--border-color)'}`,
              backgroundColor: 'var(--bg-card)',
              color: 'var(--text-primary)',
              fontSize: '0.9rem',
            }}
          />
        </div>
        {displayError && (
          <p
            id="whatsapp-phone-error"
            role="alert"
            style={{ color: 'var(--color-danger)', fontSize: '0.8rem', marginTop: 4 }}
          >
            {displayError}
          </p>
        )}
      </div>

      <label
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 8,
          cursor: 'pointer',
          fontSize: '0.85rem',
          color: 'var(--text-secondary)',
        }}
      >
        <input
          type="checkbox"
          checked={optIn}
          onChange={(e) => setOptIn(e.target.checked)}
          aria-label="Send me order updates on WhatsApp"
          style={{ width: 18, height: 18, accentColor: 'var(--color-success)' }}
        />
        Send me order updates on WhatsApp
      </label>
    </div>
  );
}
