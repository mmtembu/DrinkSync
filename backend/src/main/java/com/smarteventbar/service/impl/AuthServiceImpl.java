package com.smarteventbar.service.impl;

import com.smarteventbar.dto.AuthResponse;
import com.smarteventbar.exception.InvalidAccessCodeException;
import com.smarteventbar.model.entity.Station;
import com.smarteventbar.repository.StationRepository;
import com.smarteventbar.service.AuthService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

@Service
public class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    private static final String ACCESS_CODE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int ACCESS_CODE_LENGTH = 6;

    private final StationRepository stationRepository;
    private final SecretKey signingKey;
    private final int expirationHours;
    private final SecureRandom secureRandom;

    public AuthServiceImpl(StationRepository stationRepository,
                           @Value("${jwt.secret}") String secret,
                           @Value("${jwt.expiration-hours}") int expirationHours) {
        this.stationRepository = stationRepository;
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationHours = expirationHours;
        this.secureRandom = new SecureRandom();
    }

    @Override
    public AuthResponse authenticate(Long stationId, String accessCode) {
        Station station = stationRepository.findById(stationId)
                .orElseThrow(() -> {
                    MDC.put("stationId", stationId.toString());
                    log.warn("Vendor login failed: stationId={} reason=station_not_found", stationId);
                    return new InvalidAccessCodeException();
                });

        if (!station.getAccessCode().equals(accessCode)) {
            MDC.put("stationId", stationId.toString());
            log.warn("Vendor login failed: stationId={} reason=invalid_access_code", stationId);
            throw new InvalidAccessCodeException();
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusHours(expirationHours);

        String token = Jwts.builder()
                .subject(stationId.toString())
                .issuedAt(toDate(now))
                .expiration(toDate(expiresAt))
                .signWith(signingKey)
                .compact();

        MDC.put("stationId", stationId.toString());
        log.info("Vendor login success: stationId={}", stationId);

        return new AuthResponse(token, expiresAt, stationId);
    }

    @Override
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    @Override
    public Long getStationIdFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return Long.parseLong(claims.getSubject());
    }

    @Override
    public String generateAccessCode() {
        StringBuilder code = new StringBuilder(ACCESS_CODE_LENGTH);
        for (int i = 0; i < ACCESS_CODE_LENGTH; i++) {
            int index = secureRandom.nextInt(ACCESS_CODE_CHARS.length());
            code.append(ACCESS_CODE_CHARS.charAt(index));
        }
        return code.toString();
    }

    private Date toDate(LocalDateTime localDateTime) {
        return Date.from(localDateTime.atZone(ZoneId.systemDefault()).toInstant());
    }
}
