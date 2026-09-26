package com.mindsetalliance.core.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @Value("${ma.errors.expose-details:false}")
    private boolean exposeDetails;

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Map<String, Object>> handleBusiness(BusinessException ex) {
        return ResponseEntity.status(ex.getStatus()).body(body(ex.getMessage(), ex.getStatus()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(body("Accès refusé : droits insuffisants.", 403));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(err -> err.getField() + " : " + err.getDefaultMessage())
                .orElse("Requête invalide");
        return ResponseEntity.badRequest().body(body(msg, 400));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> handleMissing(NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body("Ressource introuvable.", 404));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        log.error("Erreur interne", ex);
        String message = exposeDetails
                ? "Erreur interne du serveur : " + (ex.getCause() == null ? ex.getMessage() : ex.getCause().getMessage())
                : "Erreur interne du serveur.";
        return ResponseEntity.status(500).body(body(message, 500));
    }

    private Map<String, Object> body(String message, int status) {
        return Map.of(
                "horodatage", Instant.now().toString(),
                "statut", status,
                "message", message
        );
    }
}
