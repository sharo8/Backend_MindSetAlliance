package com.mindsetalliance.core.auth;

import com.mindsetalliance.core.audit.AuditService;
import com.mindsetalliance.core.common.BusinessException;
import com.mindsetalliance.core.iam.Agent;
import com.mindsetalliance.core.iam.AgentRepository;
import com.mindsetalliance.core.iam.AgentPermissionOverride;
import com.mindsetalliance.core.iam.AgentPermissionOverrideRepository;
import com.mindsetalliance.core.iam.AgentRole;
import com.mindsetalliance.core.iam.AgentRoleRepository;
import com.mindsetalliance.core.notifications.EmailService;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final Set<String> ROLES_2FA = Set.of("FINANCE", "RH", "ADMIN_SYSTEME", "DIRECTION");
    private static final String DUMMY_BCRYPT = "$2b$10$jGq3dW7uxPuBqgomaz1eHu.qUJ71Le7MWNSsCE58wWXxwGovl1AQ.";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AgentRepository agentRepository;
    private final AgentRoleRepository agentRoleRepository;
    private final AgentPermissionOverrideRepository overrideRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AuthChallengeRepository challengeRepository;
    private final PasswordSetupTokenRepository passwordSetupTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final RSAKey rsaKey;
    private final AuditService auditService;
    private final EmailService emailService;

    @Value("${ma.auth.access-token-minutes}")
    private long accessMinutes;
    @Value("${ma.auth.refresh-token-days}")
    private long refreshDays;
    @Value("${ma.auth.issuer}")
    private String issuer;
    @Value("${ma.auth.demo-totp:true}")
    private boolean demoTotp;
    @Value("${ma.auth.demo-totp-code:000000}")
    private String demoTotpCode;
    @Value("${ma.mail.frontend-base-url:http://localhost:5173}")
    private String frontendBaseUrl;

    public AuthService(AgentRepository agentRepository,
                       AgentRoleRepository agentRoleRepository,
                       AgentPermissionOverrideRepository overrideRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       AuthChallengeRepository challengeRepository,
                       PasswordSetupTokenRepository passwordSetupTokenRepository,
                       PasswordEncoder passwordEncoder,
                       JwtEncoder jwtEncoder,
                       RSAKey rsaKey,
                       AuditService auditService,
                       EmailService emailService) {
        this.agentRepository = agentRepository;
        this.agentRoleRepository = agentRoleRepository;
        this.overrideRepository = overrideRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.challengeRepository = challengeRepository;
        this.passwordSetupTokenRepository = passwordSetupTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.rsaKey = rsaKey;
        this.auditService = auditService;
        this.emailService = emailService;
    }

    @Transactional
    public Map<String, Object> login(String email, String password) {
        Agent agent = agentRepository.findByEmailProIgnoreCase(email).orElse(null);
        String hash = agent != null ? agent.getPasswordHash() : DUMMY_BCRYPT;
        boolean ok = passwordEncoder.matches(password, hash)
                && agent != null
                && "ACTIF".equals(agent.getStatut());
        if (!ok) {
            throw new BusinessException("Identifiants invalides", 401);
        }
        if (requiresTwoFactor(agent)) {
            AuthChallenge challenge = new AuthChallenge();
            challenge.setAgent(agent);
            challenge.setChallengeId(UUID.randomUUID().toString());
            challenge.setExpiresAt(Instant.now().plus(5, ChronoUnit.MINUTES));
            boolean smtpPret = emailService.isConfigured();
            if (demoTotp || !smtpPret) {
                challengeRepository.save(challenge);
                if (!demoTotp) {
                    log.warn("SMTP non configuré : 2FA e-mail impossible, code local de secours utilisé.");
                }
            } else {
                String code = sixDigitCode();
                challenge.setCodeHash(sha256(code));
                challengeRepository.save(challenge);
                emailService.envoyerCode2FA(agent.getEmailPro(), code);
                log.info("Code de vérification e-mail émis (valeur jamais journalisée).");
            }
            String message = smtpPret && !demoTotp
                    ? "Un code de vérification à 6 chiffres a été envoyé à votre adresse professionnelle."
                    : "SMTP non configuré. Saisissez le code local 000000 (puis renseignez MAIL_SMTP_PASSWORD).";
            if (demoTotp && smtpPret) {
                message = "Double authentification requise. Saisissez le code à usage unique.";
            }
            return Map.of(
                    "requires2fa", true,
                    "challengeId", challenge.getChallengeId(),
                    "message", message
            );
        }
        return issueTokens(agent, true);
    }

    @Transactional
    public Map<String, Object> verify2fa(String challengeId, String code) {
        AuthChallenge challenge = challengeRepository.findByChallengeId(challengeId)
                .orElseThrow(() -> new BusinessException("Challenge 2FA introuvable", 401));
        if (challenge.isConsumed() || challenge.getExpiresAt().isBefore(Instant.now())) {
            throw new BusinessException("Challenge 2FA expiré", 401);
        }
        Agent agent = challenge.getAgent();
        if (!codeValide(challenge, agent, code)) {
            throw new BusinessException("Code 2FA invalide", 401);
        }
        challenge.setConsumed(true);
        return issueTokens(agent, true);
    }

    @Transactional
    public Map<String, Object> refresh(String refreshToken) {
        String hash = sha256(refreshToken);
        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new BusinessException("Jeton de renouvellement invalide", 401));
        if (stored.isRevoked() || stored.getExpiresAt().isBefore(Instant.now())) {
            throw new BusinessException("Jeton de renouvellement révoqué ou expiré", 401);
        }
        if (!"ACTIF".equals(stored.getAgent().getStatut())) {
            throw new BusinessException("Compte désactivé", 401);
        }
        stored.setRevoked(true);
        return issueTokens(stored.getAgent(), true);
    }

    @Transactional
    public void logout(Long agentId, String refreshToken) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            refreshTokenRepository.findByTokenHash(sha256(refreshToken))
                    .ifPresent(token -> token.setRevoked(true));
        } else {
            refreshTokenRepository.findByAgentIdAndRevokedFalse(agentId)
                    .forEach(token -> token.setRevoked(true));
        }
        auditService.record(agentId, "LOGOUT", "SESSION", agentId, null, Map.of("revoked", true));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> currentAgent(Long agentId) {
        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new BusinessException("Agent introuvable", 404));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", agent.getId());
        body.put("email", agent.getEmailPro());
        body.put("nom", agent.getNom());
        body.put("prenom", agent.getPrenom());
        body.put("statut", agent.getStatut());
        body.put("twoFactorEnabled", agent.isTwoFactorEnabled());
        body.put("mustChangePassword", agent.isDoitChangerMotDePasse());
        body.put("departement", agent.getDepartement() == null ? null : agent.getDepartement().getNom());
        body.put("telephone", agent.getTelephone());
        if (agent.getPhotoProfil() != null && agent.getPhotoProfil().length > 0) {
            String mime = agent.getPhotoMime() == null ? "image/jpeg" : agent.getPhotoMime();
            body.put("photo", "data:" + mime + ";base64," + java.util.Base64.getEncoder().encodeToString(agent.getPhotoProfil()));
        } else {
            body.put("photo", null);
        }
        body.put("roles", agentRoleRepository.findByAgentId(agentId).stream()
                .filter(AgentRole::isActive)
                .map(ar -> {
                    Map<String, Object> role = new LinkedHashMap<>();
                    role.put("role", ar.getRole().getNom());
                    role.put("projectCode", ar.getProject() == null ? null : ar.getProject().getCode());
                    return role;
                })
                .toList());
        return body;
    }

    @Transactional
    public Map<String, Object> changePassword(Long agentId, String currentPassword, String newPassword) {
        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new BusinessException("Agent introuvable", 404));
        if (!passwordEncoder.matches(currentPassword, agent.getPasswordHash())) {
            throw new BusinessException("Le mot de passe actuel est incorrect", 400);
        }
        applyNewPassword(agent, newPassword);
        agent.setDoitChangerMotDePasse(false);
        auditService.record(agentId, "CHANGE_PASSWORD", "AGENT", agentId, null, Map.of("ok", true));
        Map<String, Object> tokens = issueTokens(agent, false);
        tokens.put("message", "Votre mot de passe a été enregistré.");
        return tokens;
    }

    @Transactional
    public Map<String, Object> completePasswordSetup(String token, String newPassword) {
        if (!StringUtils.hasText(token)) {
            throw new BusinessException("Lien de définition de mot de passe invalide", 400);
        }
        PasswordSetupToken stored = passwordSetupTokenRepository.findByTokenHash(sha256(token))
                .orElseThrow(() -> new BusinessException("Lien de définition de mot de passe invalide", 400));
        if (stored.isUsed() || stored.getExpiresAt().isBefore(Instant.now())) {
            throw new BusinessException("Ce lien a expiré. Demandez un nouveau lien depuis « Mot de passe oublié ».", 400);
        }
        Agent agent = stored.getAgent();
        if (!"ACTIF".equals(agent.getStatut())) {
            throw new BusinessException("Compte désactivé", 401);
        }
        applyNewPassword(agent, newPassword);
        agent.setDoitChangerMotDePasse(false);
        stored.setUsed(true);
        auditService.record(agent.getId(), "PASSWORD_SETUP", "AGENT", agent.getId(), null, Map.of("ok", true));
        return Map.of("message", "Votre mot de passe a été défini. Vous pouvez vous connecter.");
    }

    @Transactional
    public Map<String, String> requestPasswordReset(String email) {
        String generic = "Si un compte existe pour cet identifiant, un e-mail de réinitialisation vient d’être envoyé.";
        if (!StringUtils.hasText(email)) {
            return Map.of("message", generic);
        }
        Agent agent = agentRepository.findByEmailProIgnoreCase(email.trim()).orElse(null);
        if (agent == null || !"ACTIF".equals(agent.getStatut())) {
            return Map.of("message", generic);
        }
        if (!emailService.isConfigured()) {
            throw new BusinessException("Envoi d’e-mail indisponible : SMTP non configuré.", 503);
        }
        passwordSetupTokenRepository.findByAgent_IdAndUsedFalse(agent.getId())
                .forEach(token -> token.setUsed(true));
        String raw = UUID.randomUUID() + "." + UUID.randomUUID();
        PasswordSetupToken token = new PasswordSetupToken();
        token.setAgent(agent);
        token.setTokenHash(sha256(raw));
        token.setExpiresAt(Instant.now().plus(24, ChronoUnit.HOURS));
        passwordSetupTokenRepository.save(token);
        String lien = trimSlash(frontendBaseUrl) + "/definir-mot-de-passe?token=" + raw;
        String nom = ((agent.getPrenom() == null ? "" : agent.getPrenom()) + " "
                + (agent.getNom() == null ? "" : agent.getNom())).trim();
        emailService.envoyerReinitialisationMotDePasse(
                agent.getEmailPro(),
                nom.isBlank() ? agent.getEmailPro() : nom,
                lien);
        auditService.record(agent.getId(), "PASSWORD_RESET_REQUEST", "AGENT", agent.getId(), null, Map.of("ok", true));
        log.info("Demande de réinitialisation de mot de passe traitée.");
        return Map.of("message", generic);
    }

    @Transactional
    public boolean envoyerBienvenueSiNecessaire(String email) {
        Agent agent = agentRepository.findByEmailProIgnoreCase(email).orElse(null);
        if (agent == null || agent.isWelcomeEmailSent() || !"ACTIF".equals(agent.getStatut())) {
            return false;
        }
        if (!emailService.isConfigured()) {
            log.warn("E-mail de bienvenue non envoyé : SMTP non configuré.");
            return false;
        }
        String raw = UUID.randomUUID() + "." + UUID.randomUUID();
        PasswordSetupToken token = new PasswordSetupToken();
        token.setAgent(agent);
        token.setTokenHash(sha256(raw));
        token.setExpiresAt(Instant.now().plus(24, ChronoUnit.HOURS));
        passwordSetupTokenRepository.save(token);
        String lien = trimSlash(frontendBaseUrl) + "/definir-mot-de-passe?token=" + raw;
        String nom = ((agent.getPrenom() == null ? "" : agent.getPrenom()) + " "
                + (agent.getNom() == null ? "" : agent.getNom())).trim();
        try {
            emailService.envoyerBienvenue(agent.getEmailPro(), nom.isBlank() ? "Aline Kabacele" : nom, lien);
        } catch (RuntimeException ex) {
            log.warn("E-mail de bienvenue non envoyé (SMTP).");
            return false;
        }
        agent.setWelcomeEmailSent(true);
        return true;
    }

    @Transactional
    public Map<String, Object> updateProfile(Long agentId, String nom, String prenom, String telephone, String photo) {
        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new BusinessException("Agent introuvable", 404));
        if (nom != null) {
            if (nom.isBlank()) {
                throw new BusinessException("Le nom est obligatoire");
            }
            agent.setNom(nom.trim());
        }
        if (prenom != null) {
            if (prenom.isBlank()) {
                throw new BusinessException("Le prénom est obligatoire");
            }
            agent.setPrenom(prenom.trim());
        }
        if (telephone != null) {
            agent.setTelephone(telephone.isBlank() ? null : telephone.trim());
        }
        if (photo != null) {
            if (photo.isBlank()) {
                agent.setPhotoProfil(null);
                agent.setPhotoMime(null);
            } else {
                applyPhoto(agent, photo);
            }
        }
        auditService.record(agentId, "UPDATE_PROFILE", "AGENT", agentId, null, Map.of("ok", true));
        return currentAgent(agentId);
    }

    private void applyPhoto(Agent agent, String photo) {
        int comma = photo.indexOf(',');
        if (comma < 0 || !photo.startsWith("data:")) {
            throw new BusinessException("La photo de profil est invalide");
        }
        String header = photo.substring(5, comma);
        String mime = header.contains(";") ? header.substring(0, header.indexOf(';')) : header;
        if (!Set.of("image/jpeg", "image/png", "image/webp", "image/gif").contains(mime)) {
            throw new BusinessException("Le format de photo n’est pas accepté (JPEG, PNG, WebP ou GIF)");
        }
        byte[] bytes;
        try {
            bytes = java.util.Base64.getDecoder().decode(photo.substring(comma + 1));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("La photo de profil est invalide");
        }
        if (bytes.length > 2_000_000) {
            throw new BusinessException("La photo de profil ne doit pas dépasser 2 Mo");
        }
        agent.setPhotoMime(mime);
        agent.setPhotoProfil(bytes);
    }

    public Map<String, Object> jwks() {
        return new JWKSet(rsaKey.toPublicJWK()).toJSONObject();
    }

    public List<Long> revokedAgentIds() {
        return agentRepository.findAll().stream()
                .filter(agent -> !"ACTIF".equals(agent.getStatut()))
                .map(Agent::getId)
                .toList();
    }

    private boolean requiresTwoFactor(Agent agent) {
        if (agent.isTwoFactorEnabled()) {
            return true;
        }
        return agentRoleRepository.findByAgentId(agent.getId()).stream()
                .filter(AgentRole::isActive)
                .map(ar -> ar.getRole().getNom())
                .anyMatch(ROLES_2FA::contains);
    }

    private boolean codeValide(AuthChallenge challenge, Agent agent, String code) {
        if (challenge.getCodeHash() != null && !challenge.getCodeHash().isBlank()) {
            byte[] expected = challenge.getCodeHash().getBytes(StandardCharsets.UTF_8);
            byte[] actual = sha256(code == null ? "" : code).getBytes(StandardCharsets.UTF_8);
            return MessageDigest.isEqual(expected, actual);
        }
        boolean secoursLocal = demoTotp || !emailService.isConfigured();
        return TotpService.verify(agent.getTotpSecret(), code, secoursLocal, demoTotpCode);
    }

    private void applyNewPassword(Agent agent, String newPassword) {
        if (newPassword == null || newPassword.length() < 8
                || !newPassword.matches(".*[A-Za-z].*") || !newPassword.matches(".*\\d.*")) {
            throw new BusinessException("Le nouveau mot de passe doit contenir au moins 8 caractères, une lettre et un chiffre");
        }
        if (passwordEncoder.matches(newPassword, agent.getPasswordHash())) {
            throw new BusinessException("Le nouveau mot de passe doit être différent de l’actuel");
        }
        agent.setPasswordHash(passwordEncoder.encode(newPassword));
        refreshTokenRepository.findByAgentIdAndRevokedFalse(agent.getId())
                .forEach(token -> token.setRevoked(true));
    }

    private Map<String, Object> issueTokens(Agent agent, boolean loginAudit) {
        List<Map<String, Object>> roles = agentRoleRepository.findByAgentId(agent.getId()).stream()
                .filter(AgentRole::isActive)
                .map(ar -> {
                    Map<String, Object> claim = new LinkedHashMap<>();
                    claim.put("projectCode", ar.getProject() == null ? null : ar.getProject().getCode());
                    claim.put("role", ar.getRole().getNom());
                    return claim;
                })
                .toList();

        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .issuedAt(now)
                .expiresAt(now.plus(accessMinutes, ChronoUnit.MINUTES))
                .subject("agent-id-" + agent.getId())
                .claim("email", agent.getEmailPro())
                .claim("roles", roles)
                .claim("permissionOverrides", overrideRepository.findByAgentId(agent.getId()).stream().map(this::toOverrideClaim).toList())
                .claim("mustChangePassword", agent.isDoitChangerMotDePasse())
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(rsaKey.getKeyID()).build();
        String access = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();

        String refresh = UUID.randomUUID() + "." + UUID.randomUUID();
        RefreshToken entity = new RefreshToken();
        entity.setAgent(agent);
        entity.setTokenHash(sha256(refresh));
        entity.setExpiresAt(now.plus(refreshDays, ChronoUnit.DAYS));
        refreshTokenRepository.save(entity);

        if (loginAudit) {
            auditService.record(agent.getId(), "LOGIN", "SESSION", agent.getId(), null, Map.of("ok", true));
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("requires2fa", false);
        response.put("accessToken", access);
        response.put("refreshToken", refresh);
        response.put("expiresIn", accessMinutes * 60);
        response.put("tokenType", "Bearer");
        response.put("mustChangePassword", agent.isDoitChangerMotDePasse());
        return response;
    }

    private Map<String, Object> toOverrideClaim(AgentPermissionOverride override) {
        Map<String, Object> claim = new LinkedHashMap<>();
        claim.put("permission", override.getPermission() == null ? null : override.getPermission().getCode());
        claim.put("projectCode", override.getProject() == null ? null : override.getProject().getCode());
        claim.put("type", override.getType());
        return claim;
    }

    private static String sixDigitCode() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }

    private static String trimSlash(String url) {
        if (url == null || url.isBlank()) {
            return "http://localhost:5173";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
