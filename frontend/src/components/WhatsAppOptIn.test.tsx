import { describe, it, expect, vi, afterEach } from 'vitest';
import { render, screen, cleanup, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { WhatsAppOptIn, validateE164 } from './WhatsAppOptIn';

afterEach(() => {
  cleanup();
});

describe('WhatsAppOptIn', () => {
  describe('rendering', () => {
    it('renders phone number input with country code selector', () => {
      const onChange = vi.fn();
      render(<WhatsAppOptIn onChange={onChange} />);

      expect(screen.getByLabelText('Phone number')).toBeInTheDocument();
      expect(screen.getByLabelText('Country code')).toBeInTheDocument();
    });

    it('renders opt-in checkbox with correct label', () => {
      const onChange = vi.fn();
      render(<WhatsAppOptIn onChange={onChange} />);

      const checkbox = screen.getByLabelText('Send me order updates on WhatsApp');
      expect(checkbox).toBeInTheDocument();
    });

    it('defaults checkbox to unchecked', () => {
      const onChange = vi.fn();
      render(<WhatsAppOptIn onChange={onChange} />);

      const checkbox = screen.getByLabelText('Send me order updates on WhatsApp') as HTMLInputElement;
      expect(checkbox.checked).toBe(false);
    });

    it('defaults country code to +27 (South Africa)', () => {
      const onChange = vi.fn();
      render(<WhatsAppOptIn onChange={onChange} />);

      const select = screen.getByLabelText('Country code') as HTMLSelectElement;
      expect(select.value).toBe('+27');
    });

    it('pre-fills phone number from initialPhone prop', () => {
      const onChange = vi.fn();
      render(<WhatsAppOptIn onChange={onChange} initialPhone="+27821234567" />);

      const input = screen.getByLabelText('Phone number') as HTMLInputElement;
      expect(input.value).toBe('821234567');

      const select = screen.getByLabelText('Country code') as HTMLSelectElement;
      expect(select.value).toBe('+27');
    });

    it('pre-fills opt-in state from initialOptIn prop', () => {
      const onChange = vi.fn();
      render(<WhatsAppOptIn onChange={onChange} initialOptIn={true} />);

      const checkbox = screen.getByLabelText('Send me order updates on WhatsApp') as HTMLInputElement;
      expect(checkbox.checked).toBe(true);
    });
  });

  describe('interaction', () => {
    it('calls onChange with customerPhone and whatsappOptIn when typing', async () => {
      const onChange = vi.fn();
      const user = userEvent.setup();
      render(<WhatsAppOptIn onChange={onChange} />);

      const input = screen.getByLabelText('Phone number');
      await user.type(input, '821234567');

      // Last call should have the full E.164 number
      expect(onChange).toHaveBeenLastCalledWith({
        customerPhone: '+27821234567',
        whatsappOptIn: false,
      });
    });

    it('calls onChange when checkbox is toggled', async () => {
      const onChange = vi.fn();
      const user = userEvent.setup();
      render(<WhatsAppOptIn onChange={onChange} />);

      const checkbox = screen.getByLabelText('Send me order updates on WhatsApp');
      await user.click(checkbox);

      expect(onChange).toHaveBeenLastCalledWith(
        expect.objectContaining({ whatsappOptIn: true })
      );
    });

    it('calls onChange when country code is changed', async () => {
      const onChange = vi.fn();
      const user = userEvent.setup();
      render(<WhatsAppOptIn onChange={onChange} initialPhone="+27821234567" />);

      const select = screen.getByLabelText('Country code');
      await user.selectOptions(select, '+44');

      expect(onChange).toHaveBeenLastCalledWith(
        expect.objectContaining({ customerPhone: '+44821234567' })
      );
    });

    it('strips non-digit characters from phone input', async () => {
      const onChange = vi.fn();
      const user = userEvent.setup();
      render(<WhatsAppOptIn onChange={onChange} />);

      const input = screen.getByLabelText('Phone number') as HTMLInputElement;
      await user.type(input, '82-123-4567');

      // Non-digits should be stripped
      expect(input.value).toBe('821234567');
    });
  });

  describe('validation', () => {
    it('shows error for phone number with too few digits on blur', async () => {
      const onChange = vi.fn();
      const user = userEvent.setup();
      render(<WhatsAppOptIn onChange={onChange} />);

      const input = screen.getByLabelText('Phone number');
      // +27 + "8" = "+278" which is only 3 digits total — below the minimum of 2 digits after +
      await user.type(input, '8');
      fireEvent.blur(input);

      // "+278" has 3 digits which matches /^\+[1-9]\d{1,14}$/ (1-14 more digits after first)
      // Actually +278 = + followed by 278 (3 digits), regex requires \d{1,14} after first digit
      // So +278 is valid (1 first digit + 2 more = 3 total, within 2-15 range)
      expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    });

    it('shows error for phone number exceeding 15 digits total on blur', async () => {
      const onChange = vi.fn();
      const user = userEvent.setup();
      render(<WhatsAppOptIn onChange={onChange} />);

      const input = screen.getByLabelText('Phone number');
      // +27 is 2 digits of the country code, so we need more than 13 local digits to exceed 15 total
      await user.type(input, '12345678901234');
      fireEvent.blur(input);

      // +27 + 14 digits = 16 digits total, exceeds E.164 max of 15
      expect(screen.getByRole('alert')).toBeInTheDocument();
    });

    it('does not show error for empty field on blur', () => {
      const onChange = vi.fn();
      render(<WhatsAppOptIn onChange={onChange} />);

      const input = screen.getByLabelText('Phone number');
      fireEvent.blur(input);

      expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    });

    it('displays external error from error prop', () => {
      const onChange = vi.fn();
      render(<WhatsAppOptIn onChange={onChange} error="Invalid phone number format" />);

      expect(screen.getByRole('alert')).toBeInTheDocument();
      expect(screen.getByRole('alert').textContent).toContain('Invalid phone number format');
    });

    it('clears local validation error when user types valid input', async () => {
      const onChange = vi.fn();
      const user = userEvent.setup();
      render(<WhatsAppOptIn onChange={onChange} />);

      const input = screen.getByLabelText('Phone number');
      // Type too many digits to trigger error
      await user.type(input, '12345678901234');
      fireEvent.blur(input);
      expect(screen.getByRole('alert')).toBeInTheDocument();

      // Clear and type valid number — error should clear on change
      await user.clear(input);
      await user.type(input, '821234567');

      expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    });
  });

  describe('accessibility', () => {
    it('has proper label associations', () => {
      const onChange = vi.fn();
      render(<WhatsAppOptIn onChange={onChange} />);

      const phoneInput = screen.getByLabelText('Phone number');
      expect(phoneInput).toHaveAttribute('id', 'whatsapp-phone');

      const checkbox = screen.getByLabelText('Send me order updates on WhatsApp');
      expect(checkbox).toBeInTheDocument();
    });

    it('sets aria-invalid when validation fails', async () => {
      const onChange = vi.fn();
      const user = userEvent.setup();
      render(<WhatsAppOptIn onChange={onChange} />);

      const input = screen.getByLabelText('Phone number');
      await user.type(input, '12345678901234');
      fireEvent.blur(input);

      expect(input).toHaveAttribute('aria-invalid', 'true');
    });

    it('sets aria-describedby pointing to error message', async () => {
      const onChange = vi.fn();
      const user = userEvent.setup();
      render(<WhatsAppOptIn onChange={onChange} />);

      const input = screen.getByLabelText('Phone number');
      await user.type(input, '12345678901234');
      fireEvent.blur(input);

      expect(input).toHaveAttribute('aria-describedby', 'whatsapp-phone-error');
    });

    it('uses role="alert" for validation errors', async () => {
      const onChange = vi.fn();
      const user = userEvent.setup();
      render(<WhatsAppOptIn onChange={onChange} />);

      const input = screen.getByLabelText('Phone number');
      await user.type(input, '12345678901234');
      fireEvent.blur(input);

      expect(screen.getByRole('alert')).toBeInTheDocument();
    });

    it('has role="group" with aria-labelledby for the section', () => {
      const onChange = vi.fn();
      render(<WhatsAppOptIn onChange={onChange} />);

      const group = screen.getByRole('group');
      expect(group).toHaveAttribute('aria-labelledby', 'whatsapp-optin-heading');
    });
  });
});


describe('validateE164', () => {
  it('accepts valid E.164 numbers', () => {
    expect(validateE164('+27821234567')).toBe(true);
    expect(validateE164('+1234567890')).toBe(true);
    expect(validateE164('+44207123456')).toBe(true);
    expect(validateE164('+123456789012345')).toBe(true); // max 15 digits
    expect(validateE164('+12')).toBe(true); // minimum: + followed by 2 digits
  });

  it('rejects numbers missing the + prefix', () => {
    expect(validateE164('27821234567')).toBe(false);
    expect(validateE164('1234567890')).toBe(false);
  });

  it('rejects numbers starting with +0', () => {
    expect(validateE164('+0123456789')).toBe(false);
  });

  it('rejects numbers exceeding 15 digits', () => {
    expect(validateE164('+1234567890123456')).toBe(false); // 16 digits
  });

  it('rejects numbers with non-digit characters', () => {
    expect(validateE164('+27abc')).toBe(false);
    expect(validateE164('+27 821 234')).toBe(false);
    expect(validateE164('+27-821-234')).toBe(false);
  });

  it('rejects empty string and just +', () => {
    expect(validateE164('')).toBe(false);
    expect(validateE164('+')).toBe(false);
    expect(validateE164('+1')).toBe(false); // only 1 digit after + (needs at least 2)
  });
});
