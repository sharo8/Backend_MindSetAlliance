package com.mindsetalliance.core.integration.cnn;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mindsetalliance.core.vitrine.ProjectEventProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CnnEventIngestServiceTest {

    static final String SECRET = "0123456789abcdef0123456789abcdef-secret";
    static final byte[] BODY = """
            {"events":[{"eventId":"evt-1","topic":"colisnanga.courses.events",
              "payload":{"type":"COURSE_LIVREE","courseId":"42","zone":"Gombe"}}]}
            """.getBytes(StandardCharsets.UTF_8);

    @Mock ProjectEventProcessor processor;
    @Mock InboxEventRepository inbox;
    @Mock TransactionTemplate tx;

    CnnEventIngestService service;

    @BeforeEach
    void setUp() {
        CnnIntegrationProperties props = new CnnIntegrationProperties();
        props.setWebhookSecret(SECRET);
        service = new CnnEventIngestService(props, processor, inbox, tx, new ObjectMapper());
        lenient().when(tx.execute(any())).thenAnswer(inv -> ((TransactionCallback<?>) inv.getArgument(0)).doInTransaction(null));
    }

    private static String sign(String ts, byte[] body) {
        return "sha256=" + CnnEventIngestService.hmacHex(SECRET, ts, body);
    }

    @Test
    void evenementSigneEstTraiteUneSeuleFois() {
        String ts = String.valueOf(Instant.now().getEpochSecond());
        when(inbox.existsById("evt-1")).thenReturn(false, true);

        var first = service.ingest(ts, sign(ts, BODY), BODY);
        var second = service.ingest(ts, sign(ts, BODY), BODY);

        assertEquals(List.of("evt-1"), first.accepted());
        assertEquals(List.of("evt-1"), second.duplicates());
        verify(processor).process(eq("colisnanga.courses.events"), anyMap());
    }

    @Test
    void signatureFalsifieeEstRefusee() {
        String ts = String.valueOf(Instant.now().getEpochSecond());
        byte[] altered = new String(BODY, StandardCharsets.UTF_8).replace("Gombe", "Limete").getBytes(StandardCharsets.UTF_8);
        assertThrows(CnnEventIngestService.InvalidSignatureException.class,
                () -> service.ingest(ts, sign(ts, BODY), altered));
        verify(processor, never()).process(any(), anyMap());
    }

    @Test
    void messageRejoueApresLaFenetreEstRefuse() {
        String old = String.valueOf(Instant.now().getEpochSecond() - 3600);
        assertThrows(CnnEventIngestService.InvalidSignatureException.class,
                () -> service.ingest(old, sign(old, BODY), BODY));
    }

    @Test
    void secretAbsentBloqueTout() {
        CnnIntegrationProperties props = new CnnIntegrationProperties();
        var unsecured = new CnnEventIngestService(props, processor, inbox, tx, new ObjectMapper());
        String ts = String.valueOf(Instant.now().getEpochSecond());
        assertThrows(CnnEventIngestService.InvalidSignatureException.class,
                () -> unsecured.ingest(ts, sign(ts, BODY), BODY));
    }
}
