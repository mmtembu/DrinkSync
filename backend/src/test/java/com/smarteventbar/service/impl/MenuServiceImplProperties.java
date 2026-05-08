package com.smarteventbar.service.impl;

import com.smarteventbar.dto.MixerItemRequest;
import com.smarteventbar.dto.PremadeItemRequest;
import com.smarteventbar.dto.SpiritItemRequest;
import com.smarteventbar.model.entity.Station;
import com.smarteventbar.repository.MixerItemRepository;
import com.smarteventbar.repository.PremadeItemRepository;
import com.smarteventbar.repository.SpiritItemRepository;
import com.smarteventbar.repository.StationRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Property-based tests for MenuService validation.
 *
 * Property 12: Menu item validation rejects invalid data
 * - Generate items with empty names, zero/negative prices; verify rejection
 *
 * Property 21: Pickup window duration validation
 * - Generate random integers (0–60); verify only 5–30 accepted
 *
 * Validates: Requirements 9.7, 15.6
 */
class MenuServiceImplProperties {

    private static final Validator validator;

    static {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    // --- Arbitraries ---

    @Provide
    Arbitrary<String> emptyOrBlankNames() {
        return Arbitraries.of("", " ", "  ", "\t", "\n", "   \t\n  ");
    }

    @Provide
    Arbitrary<BigDecimal> zeroOrNegativePrices() {
        return Arbitraries.oneOf(
                Arbitraries.just(BigDecimal.ZERO),
                Arbitraries.bigDecimals()
                        .between(BigDecimal.valueOf(-9999.99), BigDecimal.valueOf(-0.01))
                        .ofScale(2)
        );
    }

    @Provide
    Arbitrary<BigDecimal> validPrices() {
        return Arbitraries.bigDecimals()
                .between(BigDecimal.valueOf(0.01), BigDecimal.valueOf(9999.99))
                .ofScale(2);
    }

    @Provide
    Arbitrary<String> validNames() {
        return Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(50);
    }

    // --- Property 12: Menu item validation rejects invalid data ---

    /**
     * Property 12a: SpiritItemRequest with empty/blank name is rejected.
     *
     * **Validates: Requirements 9.7**
     */
    @Property
    void spiritItemWithEmptyNameIsRejected(
            @ForAll("emptyOrBlankNames") String invalidName,
            @ForAll("validPrices") BigDecimal validPrice) {

        SpiritItemRequest request = new SpiritItemRequest();
        request.setName(invalidName);
        request.setPrice(validPrice);

        Set<ConstraintViolation<SpiritItemRequest>> violations = validator.validate(request);

        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("name"));
    }

    /**
     * Property 12b: SpiritItemRequest with null name is rejected.
     *
     * **Validates: Requirements 9.7**
     */
    @Property(tries = 1)
    void spiritItemWithNullNameIsRejected() {
        SpiritItemRequest request = new SpiritItemRequest();
        request.setName(null);
        request.setPrice(BigDecimal.valueOf(5.00));

        Set<ConstraintViolation<SpiritItemRequest>> violations = validator.validate(request);

        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("name"));
    }

    /**
     * Property 12c: SpiritItemRequest with zero or negative price is rejected.
     *
     * **Validates: Requirements 9.7**
     */
    @Property
    void spiritItemWithZeroOrNegativePriceIsRejected(
            @ForAll("validNames") String validName,
            @ForAll("zeroOrNegativePrices") BigDecimal invalidPrice) {

        SpiritItemRequest request = new SpiritItemRequest();
        request.setName(validName);
        request.setPrice(invalidPrice);

        Set<ConstraintViolation<SpiritItemRequest>> violations = validator.validate(request);

        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("price"));
    }

    /**
     * Property 12d: MixerItemRequest with empty/blank name is rejected.
     *
     * **Validates: Requirements 9.7**
     */
    @Property
    void mixerItemWithEmptyNameIsRejected(
            @ForAll("emptyOrBlankNames") String invalidName,
            @ForAll("validPrices") BigDecimal validPrice) {

        MixerItemRequest request = new MixerItemRequest();
        request.setName(invalidName);
        request.setPrice(validPrice);

        Set<ConstraintViolation<MixerItemRequest>> violations = validator.validate(request);

        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("name"));
    }

    /**
     * Property 12e: MixerItemRequest with zero or negative price is rejected.
     *
     * **Validates: Requirements 9.7**
     */
    @Property
    void mixerItemWithZeroOrNegativePriceIsRejected(
            @ForAll("validNames") String validName,
            @ForAll("zeroOrNegativePrices") BigDecimal invalidPrice) {

        MixerItemRequest request = new MixerItemRequest();
        request.setName(validName);
        request.setPrice(invalidPrice);

        Set<ConstraintViolation<MixerItemRequest>> violations = validator.validate(request);

        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("price"));
    }

    /**
     * Property 12f: PremadeItemRequest with empty/blank name is rejected.
     *
     * **Validates: Requirements 9.7**
     */
    @Property
    void premadeItemWithEmptyNameIsRejected(
            @ForAll("emptyOrBlankNames") String invalidName,
            @ForAll("validPrices") BigDecimal validPrice) {

        PremadeItemRequest request = new PremadeItemRequest();
        request.setName(invalidName);
        request.setDescription("A valid description");
        request.setPrice(validPrice);

        Set<ConstraintViolation<PremadeItemRequest>> violations = validator.validate(request);

        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("name"));
    }

    /**
     * Property 12g: PremadeItemRequest with zero or negative price is rejected.
     *
     * **Validates: Requirements 9.7**
     */
    @Property
    void premadeItemWithZeroOrNegativePriceIsRejected(
            @ForAll("validNames") String validName,
            @ForAll("zeroOrNegativePrices") BigDecimal invalidPrice) {

        PremadeItemRequest request = new PremadeItemRequest();
        request.setName(validName);
        request.setDescription("A valid description");
        request.setPrice(invalidPrice);

        Set<ConstraintViolation<PremadeItemRequest>> violations = validator.validate(request);

        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("price"));
    }

    /**
     * Property 12h: Valid menu item requests pass validation.
     * Ensures the validator does not reject valid data.
     *
     * **Validates: Requirements 9.7**
     */
    @Property
    void validSpiritItemRequestPassesValidation(
            @ForAll("validNames") String validName,
            @ForAll("validPrices") BigDecimal validPrice) {

        SpiritItemRequest request = new SpiritItemRequest();
        request.setName(validName);
        request.setPrice(validPrice);

        Set<ConstraintViolation<SpiritItemRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    /**
     * Property 12i: Valid mixer item requests pass validation.
     *
     * **Validates: Requirements 9.7**
     */
    @Property
    void validMixerItemRequestPassesValidation(
            @ForAll("validNames") String validName,
            @ForAll("validPrices") BigDecimal validPrice) {

        MixerItemRequest request = new MixerItemRequest();
        request.setName(validName);
        request.setPrice(validPrice);

        Set<ConstraintViolation<MixerItemRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    // --- Property 21: Pickup window duration validation ---

    /**
     * Property 21a: Pickup window values in range 5–30 are accepted.
     *
     * **Validates: Requirements 15.6**
     */
    @Property
    void pickupWindowInValidRangeIsAccepted(
            @ForAll @IntRange(min = 5, max = 30) int validMinutes) {

        StationRepository stationRepository = mock(StationRepository.class);
        SpiritItemRepository spiritItemRepository = mock(SpiritItemRepository.class);
        MixerItemRepository mixerItemRepository = mock(MixerItemRepository.class);
        PremadeItemRepository premadeItemRepository = mock(PremadeItemRepository.class);

        MenuServiceImpl menuService = new MenuServiceImpl(
                stationRepository, spiritItemRepository, mixerItemRepository, premadeItemRepository);

        Station station = new Station("Test Station", "Location", BigDecimal.ONE, "ABC123", 10);
        station.setId(1L);

        when(stationRepository.findById(1L)).thenReturn(Optional.of(station));

        // Should NOT throw
        menuService.setPickupWindowMinutes(1L, validMinutes);

        assertThat(station.getPickupWindowMinutes()).isEqualTo(validMinutes);
    }

    /**
     * Property 21b: Pickup window values below 5 are rejected.
     *
     * **Validates: Requirements 15.6**
     */
    @Property
    void pickupWindowBelowFiveIsRejected(
            @ForAll @IntRange(min = 0, max = 4) int invalidMinutes) {

        StationRepository stationRepository = mock(StationRepository.class);
        SpiritItemRepository spiritItemRepository = mock(SpiritItemRepository.class);
        MixerItemRepository mixerItemRepository = mock(MixerItemRepository.class);
        PremadeItemRepository premadeItemRepository = mock(PremadeItemRepository.class);

        MenuServiceImpl menuService = new MenuServiceImpl(
                stationRepository, spiritItemRepository, mixerItemRepository, premadeItemRepository);

        assertThatThrownBy(() -> menuService.setPickupWindowMinutes(1L, invalidMinutes))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Pickup window must be between 5 and 30 minutes");
    }

    /**
     * Property 21c: Pickup window values above 30 are rejected.
     *
     * **Validates: Requirements 15.6**
     */
    @Property
    void pickupWindowAboveThirtyIsRejected(
            @ForAll @IntRange(min = 31, max = 60) int invalidMinutes) {

        StationRepository stationRepository = mock(StationRepository.class);
        SpiritItemRepository spiritItemRepository = mock(SpiritItemRepository.class);
        MixerItemRepository mixerItemRepository = mock(MixerItemRepository.class);
        PremadeItemRepository premadeItemRepository = mock(PremadeItemRepository.class);

        MenuServiceImpl menuService = new MenuServiceImpl(
                stationRepository, spiritItemRepository, mixerItemRepository, premadeItemRepository);

        assertThatThrownBy(() -> menuService.setPickupWindowMinutes(1L, invalidMinutes))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Pickup window must be between 5 and 30 minutes");
    }

    /**
     * Property 21d: Pickup window boundary values (5 and 30) are accepted.
     *
     * **Validates: Requirements 15.6**
     */
    @Property(tries = 1)
    void pickupWindowBoundaryValuesAreAccepted() {

        StationRepository stationRepository = mock(StationRepository.class);
        SpiritItemRepository spiritItemRepository = mock(SpiritItemRepository.class);
        MixerItemRepository mixerItemRepository = mock(MixerItemRepository.class);
        PremadeItemRepository premadeItemRepository = mock(PremadeItemRepository.class);

        MenuServiceImpl menuService = new MenuServiceImpl(
                stationRepository, spiritItemRepository, mixerItemRepository, premadeItemRepository);

        Station station = new Station("Test Station", "Location", BigDecimal.ONE, "ABC123", 10);
        station.setId(1L);

        when(stationRepository.findById(1L)).thenReturn(Optional.of(station));

        // Lower boundary: 5
        menuService.setPickupWindowMinutes(1L, 5);
        assertThat(station.getPickupWindowMinutes()).isEqualTo(5);

        // Upper boundary: 30
        menuService.setPickupWindowMinutes(1L, 30);
        assertThat(station.getPickupWindowMinutes()).isEqualTo(30);

        // Just outside boundaries should fail
        assertThatThrownBy(() -> menuService.setPickupWindowMinutes(1L, 4))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> menuService.setPickupWindowMinutes(1L, 31))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
