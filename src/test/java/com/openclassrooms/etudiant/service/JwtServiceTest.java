package com.openclassrooms.etudiant.service;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {
    @Test
    void generatesSignedTokenValidForOneHour() {
        SecretKey testKey = new SecretKeySpec(new byte[32], "HmacSHA256");
        JwtService jwtService = new JwtService(new NimbusJwtEncoder(new ImmutableSecret<>(testKey)));

        String token = jwtService.generateToken("agent.p2");

        // Decoding with the test key also checks the signature.
        Jwt jwt = NimbusJwtDecoder.withSecretKey(testKey).macAlgorithm(MacAlgorithm.HS256).build().decode(token);
        assertThat(jwt.getSubject()).isEqualTo("agent.p2");
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofHours(1));
    }
}
