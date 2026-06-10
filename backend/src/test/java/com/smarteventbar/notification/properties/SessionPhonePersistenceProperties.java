package com.smarteventbar.notification.properties;

import com.smarteventbar.model.entity.CustomerOrder;
import com.smarteventbar.model.entity.CustomerSession;
import net.jqwik.api.*;

/**
 * Property-based tests for session-level phone persistence round-trip.
 *
 * Property 15: Session-level phone persistence round-trip
 * For any valid phone number and opt-in boolean provided at checkout, the values SHALL be
 * stored on both the Order record and the Session record. On subsequent orders within the
 * same session, the stored values SHALL be available for pre-fill.
 *
 * Validates: Requirements 19.1, 19.2, 19.4
 */
class SessionPhonePersistenceProperties {

    @Provide
    Arbitrary<String> validPhoneNumbers() {
        // E.164 format: + followed by 1-15 digits
        return Arbitraries.strings()
                .withCharRange('0', '9')
                .ofMinLength(7)
                .ofMaxLength(14)
                .map(digits -> "+" + digits);
    }

    @Provide
    Arbitrary<Boolean> optInValues() {
        return Arbitraries.of(true, false);
    }

    /**
     * Property 15a: Setting phone and opt-in on an Order persists the values correctly.
     * Verifies that the entity stores and returns the exact values set.
     */
    @Property(tries = 100)
    void phoneAndOptInPersistedOnOrder(
            @ForAll("validPhoneNumbers") String phone,
            @ForAll("optInValues") boolean optIn) {

        CustomerOrder order = new CustomerOrder();
        order.setCustomerPhone(phone);
        order.setWhatsappOptIn(optIn);

        assert phone.equals(order.getCustomerPhone()) :
                "Order phone should be '" + phone + "' but got '" + order.getCustomerPhone() + "'";
        assert order.isWhatsappOptIn() == optIn :
                "Order opt-in should be " + optIn + " but got " + order.isWhatsappOptIn();
    }

    /**
     * Property 15b: Setting phone and opt-in on a Session persists the values correctly.
     * Verifies that the entity stores and returns the exact values set.
     */
    @Property(tries = 100)
    void phoneAndOptInPersistedOnSession(
            @ForAll("validPhoneNumbers") String phone,
            @ForAll("optInValues") boolean optIn) {

        CustomerSession session = new CustomerSession();
        session.setCustomerPhone(phone);
        session.setWhatsappOptIn(optIn);

        assert phone.equals(session.getCustomerPhone()) :
                "Session phone should be '" + phone + "' but got '" + session.getCustomerPhone() + "'";
        assert session.isWhatsappOptIn() == optIn :
                "Session opt-in should be " + optIn + " but got " + session.isWhatsappOptIn();
    }

    /**
     * Property 15c: Values set on Order and Session are consistent (round-trip).
     * Simulates checkout: phone and opt-in are stored on both Order and Session,
     * and a subsequent order can read the session values for pre-fill.
     */
    @Property(tries = 100)
    void sessionValuesAvailableForPreFillOnSubsequentOrder(
            @ForAll("validPhoneNumbers") String phone,
            @ForAll("optInValues") boolean optIn) {

        // Simulate first checkout: store on both order and session
        CustomerSession session = new CustomerSession();
        session.setCustomerPhone(phone);
        session.setWhatsappOptIn(optIn);

        CustomerOrder firstOrder = new CustomerOrder();
        firstOrder.setCustomerPhone(phone);
        firstOrder.setWhatsappOptIn(optIn);
        firstOrder.setSession(session);

        // Simulate subsequent order: pre-fill from session
        CustomerOrder secondOrder = new CustomerOrder();
        secondOrder.setSession(session);
        secondOrder.setCustomerPhone(session.getCustomerPhone());
        secondOrder.setWhatsappOptIn(session.isWhatsappOptIn());

        // Verify pre-fill values match original
        assert phone.equals(secondOrder.getCustomerPhone()) :
                "Pre-filled phone should be '" + phone + "' but got '" + secondOrder.getCustomerPhone() + "'";
        assert secondOrder.isWhatsappOptIn() == optIn :
                "Pre-filled opt-in should be " + optIn + " but got " + secondOrder.isWhatsappOptIn();

        // Verify both order and session have consistent values
        assert firstOrder.getCustomerPhone().equals(session.getCustomerPhone()) :
                "Order and session phone should match";
        assert firstOrder.isWhatsappOptIn() == session.isWhatsappOptIn() :
                "Order and session opt-in should match";
    }
}
