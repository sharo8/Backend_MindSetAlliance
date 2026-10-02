package com.mindsetalliance.core.integration.cnn;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * {@code POST /api/internal/cnn/events} — lot d'événements signés envoyé par Colis na Nga.
 *
 * Double protection : {@code X-MA-Internal-Key} (filtre commun à /api/internal/**)
 * puis signature HMAC {@code X-CNN-Signature} sur {@code X-CNN-Timestamp + "." + corps}.
 */
@RestController
@RequestMapping("/api/internal/cnn")
public class CnnEventIngestController {

    private final CnnEventIngestService ingestService;

    public CnnEventIngestController(CnnEventIngestService ingestService) {
        this.ingestService = ingestService;
    }

    @PostMapping("/events")
    public CnnEventIngestService.IngestResult ingest(
            @RequestHeader(value = "X-CNN-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "X-CNN-Signature", required = false) String signature,
            @RequestBody byte[] body) {
        return ingestService.ingest(timestamp, signature, body);
    }

    @ExceptionHandler(CnnEventIngestService.InvalidSignatureException.class)
    public ResponseEntity<Map<String, Object>> invalidSignature(CnnEventIngestService.InvalidSignatureException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> invalidBody(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
    }
}
