package com.mindsetalliance.core.reports;

import com.mindsetalliance.core.iam.Agent;
import com.mindsetalliance.core.iam.AgentRepository;
import com.nimbusds.jose.jwk.RSAKey;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("local")
@TestPropertySource(properties = {
        "ma.kafka.enabled=false",
        "spring.kafka.listener.auto-startup=false"
})
class AdminReportPdfGenerationTest {

    @Autowired
    AdminReportService reports;
    @Autowired
    AgentRepository agentRepository;
    @Autowired
    JwtEncoder jwtEncoder;
    @Autowired
    RSAKey rsaKey;
    @Value("${ma.auth.issuer}")
    String issuer;

    @Test
    void generatesFiveValidPdfs() throws Exception {
        Path dir = Path.of("target/rapports-test");
        Files.createDirectories(dir);
        write(dir.resolve("utilisateurs.pdf"), reports.utilisateurs(null, "ACTIF"));
        write(dir.resolve("permissions.pdf"), reports.permissions());
        write(dir.resolve("acces.pdf"), reports.acces(null));
        write(dir.resolve("organisation.pdf"), reports.organisation());
        write(dir.resolve("statistiques.pdf"), reports.statistiques("30"));
    }

    @Test
    void httpUtilisateursAndPermissionsReturnValidPdfs() throws Exception {
        String token = adminToken();
        Path dir = Path.of("target/rapports-http");
        Files.createDirectories(dir);
        byte[] users = download("http://127.0.0.1:8081/api/admin/rapports/utilisateurs.pdf?statut=ACTIF", token);
        byte[] matrix = download("http://127.0.0.1:8081/api/admin/rapports/permissions.pdf", token);
        Files.write(dir.resolve("utilisateurs.pdf"), users);
        Files.write(dir.resolve("permissions.pdf"), matrix);
        assertPdf(users, "utilisateurs HTTP");
        assertPdf(matrix, "permissions HTTP");
    }

    private String adminToken() {
        Agent agent = agentRepository.findByEmailProIgnoreCase("alinekabacele@gmail.com")
                .orElseGet(() -> agentRepository.findAll().stream().findFirst().orElseThrow());
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .issuedAt(now)
                .expiresAt(now.plus(10, ChronoUnit.MINUTES))
                .subject("agent-id-" + agent.getId())
                .claim("email", agent.getEmailPro())
                .claim("roles", List.of(Map.of("role", "ADMIN_SYSTEME")))
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(rsaKey.getKeyID()).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private static byte[] download(String url, String token) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();
        HttpResponse<byte[]> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, response.statusCode(), () -> "HTTP " + response.statusCode() + " for " + url + " body=" + new String(response.body()).substring(0, Math.min(400, response.body().length)));
        return response.body();
    }

    private static void write(Path path, byte[] bytes) throws Exception {
        Files.write(path, bytes);
        assertPdf(bytes, path.getFileName().toString());
    }

    private static void assertPdf(byte[] bytes, String name) throws Exception {
        assertTrue(bytes.length > 8 && bytes[0] == '%' && bytes[1] == 'P', name + " is not a PDF");
        try (PDDocument pdf = Loader.loadPDF(bytes)) {
            assertTrue(pdf.getNumberOfPages() >= 2, name + " should have cover + content");
            String text = new PDFTextStripper().getText(pdf);
            assertTrue(text.contains("MINDSET ALLIANCE"), name + " missing brand");
            assertTrue(text.contains("Document interne") || text.contains("Internal document"), name + " missing confidentiality");
        }
    }
}
