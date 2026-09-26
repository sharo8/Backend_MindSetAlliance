package com.mindsetalliance.core.tickets;

import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Ingest machine-to-machine (PUSH) depuis Colis na Nga.
 * Authentifié par {@code X-MA-Internal-Key}, pas par JWT agent.
 */
@RestController
@RequestMapping("/api/internal/cnn")
public class CnnIncidentIngestController {

    private final TicketService ticketService;

    public CnnIncidentIngestController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @PostMapping("/incidents")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> ingest(@RequestBody CnnIncidentRequest request) {
        Ticket ticket = ticketService.ingestFromColisNaNga(request);
        return Map.of(
                "id", ticket.getId(),
                "reference", ticket.getReference(),
                "systeme", ticket.getSysteme(),
                "categorie", ticket.getCategorie() == null ? "" : ticket.getCategorie(),
                "sourceReference", ticket.getSourceReference() == null ? "" : ticket.getSourceReference()
        );
    }

    public record CnnIncidentRequest(
            @NotBlank String categorie,
            @NotBlank String titre,
            String description,
            String priorite,
            String courseId,
            String referenceExterne,
            String projectCode
    ) {
    }
}
