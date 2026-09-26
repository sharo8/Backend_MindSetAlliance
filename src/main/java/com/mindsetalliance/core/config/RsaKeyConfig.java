package com.mindsetalliance.core.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

@Configuration
public class RsaKeyConfig {

    private static final Logger log = LoggerFactory.getLogger(RsaKeyConfig.class);

    @Bean
    public RSAKey rsaKey(@Value("${ma.auth.rsa-keystore:./data/rsa.jwk}") String keystore) {
        try {
            Path path = Path.of(keystore);
            if (Files.exists(path)) {
                RSAKey existing = RSAKey.parse(Files.readString(path));
                log.info("Clé RSA JWT rechargée depuis le fichier local (les sessions restent valides).");
                return existing;
            }
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair keyPair = generator.generateKeyPair();
            RSAKey created = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                    .privateKey((RSAPrivateKey) keyPair.getPrivate())
                    .keyID("ma-core-persistent")
                    .build();
            Files.createDirectories(path.getParent());
            Files.writeString(path, created.toJSONString());
            log.info("Nouvelle clé RSA JWT persistée (ne pas versionner ce fichier).");
            return created;
        } catch (Exception e) {
            throw new IllegalStateException("Impossible de charger ou générer la paire de clés RSA", e);
        }
    }

    @Bean
    public JwtEncoder jwtEncoder(RSAKey rsaKey) {
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(rsaKey)));
    }
}
