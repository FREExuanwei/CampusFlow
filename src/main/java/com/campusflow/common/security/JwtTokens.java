package com.campusflow.common.security;

import com.campusflow.common.config.DatabaseWebModule;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

@Component
@DatabaseWebModule
public class JwtTokens {

    static final String ACCOUNT_VERSION_CLAIM = "account_version";
    private static final Duration TTL = Duration.ofMinutes(10);
    private static final Duration CLOCK_SKEW = Duration.ofSeconds(30);
    private static final MacAlgorithm ALGORITHM = MacAlgorithm.HS256;

    private final String issuer;
    private final String audience;
    private final JwtEncoder encoder;
    private final JwtDecoder decoder;

    public JwtTokens(@Value("${campusflow.auth.signing-key-base64}") String encodedKey,
                     @Value("${campusflow.auth.issuer}") String issuer,
                     @Value("${campusflow.auth.audience}") String audience) {
        if (issuer.isBlank() || audience.isBlank()) {
            throw new IllegalStateException("CampusFlow JWT issuer and audience are required");
        }
        this.issuer = issuer;
        this.audience = audience;
        SecretKey key = createKey(encodedKey);
        this.encoder = NimbusJwtEncoder.withSecretKey(key).algorithm(ALGORITHM).build();
        var verifier = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(ALGORITHM).build();
        verifier.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(CLOCK_SKEW), this::validateClaims));
        this.decoder = verifier;
    }

    public AccessToken issue(long userId, long tokenVersion) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        var claims = JwtClaimsSet.builder().subject(Long.toString(userId))
                .issuer(issuer).audience(List.of(audience))
                .issuedAt(now).expiresAt(now.plus(TTL))
                .claim(ACCOUNT_VERSION_CLAIM, Long.toString(tokenVersion)).build();
        var header = JwsHeader.with(ALGORITHM).type("JWT").build();
        Jwt token = encoder.encode(JwtEncoderParameters.from(header, claims));
        return new AccessToken(token.getTokenValue(), "Bearer", TTL.toSeconds());
    }

    public JwtDecoder decoder() {
        return decoder;
    }

    private OAuth2TokenValidatorResult validateClaims(Jwt jwt) {
        var issuedAt = jwt.getIssuedAt();
        var expiresAt = jwt.getExpiresAt();
        String subject = jwt.getSubject();
        boolean valid = issuer.equals(jwt.getClaimAsString("iss"))
                && List.of(audience).equals(jwt.getAudience())
                && validSubject(subject)
                && issuedAt != null && expiresAt != null
                && !issuedAt.isAfter(Instant.now().plus(CLOCK_SKEW))
                && expiresAt.isAfter(issuedAt)
                && Duration.between(issuedAt, expiresAt).compareTo(TTL) <= 0;
        return valid ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
    }

    private static boolean validSubject(String subject) {
        if (subject == null || !subject.matches("[1-9][0-9]{0,18}")) {
            return false;
        }
        try {
            Long.parseLong(subject);
            return true;
        } catch (NumberFormatException invalid) {
            return false;
        }
    }

    private static SecretKey createKey(String encoded) {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException invalid) {
            throw invalidKey();
        }
        try {
            if (bytes.length < 32) {
                throw invalidKey();
            }
            return new SecretKeySpec(bytes, "HmacSHA256");
        } finally {
            Arrays.fill(bytes, (byte) 0);
        }
    }

    private static IllegalStateException invalidKey() {
        return new IllegalStateException("CampusFlow JWT requires a Base64 signing key of at least 32 bytes");
    }

    public record AccessToken(String accessToken, String tokenType, long expiresIn) {
        @Override
        public String toString() {
            return "AccessToken[redacted]";
        }
    }
}
