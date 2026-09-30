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

    /** Identifiants de clés à ne plus jamais accepter (fuite). */
    private static final java.util.Set<String> COMPROMISED_KIDS = java.util.Set.of("ma-core-persistent");

    /**
     * Ordre de priorité :
     * <ol>
     *   <li>{@code MA_AUTH_RSA_JWK} : la clé complète (JSON JWK) en variable d'environnement —
     *       recommandé en production, rien sur disque ni dans Git ;</li>
     *   <li>le fichier {@code ma.auth.rsa-keystore} (hors dépôt, voir .gitignore) ;</li>
     *   <li>sinon, génération d'une nouvelle paire, persistée dans ce fichier.</li>
     * </ol>
     * Le {@code kid} est unique par clé : après une rotation, les services qui mettent la JWKS
     * en cache (Colis na Nga) détectent le nouveau {@code kid} et rechargent aussitôt.
     */
    @Bean
    public RSAKey rsaKey(@Value("${ma.auth.rsa-keystore:./data/rsa.jwk}") String keystore,
                         @Value("${MA_AUTH_RSA_JWK:}") String inlineJwk) {
        try {
            if (inlineJwk != null && !inlineJwk.isBlank()) {
                RSAKey fromEnv = RSAKey.parse(inlineJwk.trim());
                if (!fromEnv.isPrivate()) {
                    throw new IllegalStateException("MA_AUTH_RSA_JWK doit contenir la clé privée");
                }
                log.info("Clé RSA JWT chargée depuis MA_AUTH_RSA_JWK (kid={}).", fromEnv.getKeyID());
                return fromEnv;
            }
            Path path = Path.of(keystore);
            if (Files.exists(path)) {
                RSAKey existing = RSAKey.parse(Files.readString(path));
                if (COMPROMISED_KIDS.contains(existing.getKeyID())) {
                    // Cette clé a été publiée dans le dépôt Git : on la remplace d'office.
                    log.warn("Clé RSA {} compromise (publiée dans Git) : régénération.", existing.getKeyID());
                    Files.delete(path);
                    return rsaKey(keystore, null);
                }
                log.info("Clé RSA JWT rechargée depuis le fichier local (les sessions restent valides).");
                return existing;
            }
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair keyPair = generator.generateKeyPair();
            RSAKey created = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                    .privateKey((RSAPrivateKey) keyPair.getPrivate())
                    .keyID("ma-core-" + java.time.LocalDate.now() + "-" + java.util.UUID.randomUUID().toString().substring(0, 8))
                    .build();
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
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
