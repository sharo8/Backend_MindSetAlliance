package com.mindsetalliance.core.tickets;

import com.mindsetalliance.core.common.security.JwtRoles;
import com.mindsetalliance.core.common.security.RequireRoles;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @RequireRoles({"SUPPORT", "DIRECTION", "ADMIN_SYSTEME", "DEV", "COMMERCIAL", "FINANCE", "RH", "JURIDIQUE"})
    @PostMapping
    public Ticket create(@RequestBody TicketService.TicketCreateRequest request) {
        JwtRoles.assertCanSeeProject(request.projectCode());
        return ticketService.create(JwtRoles.agentId(), request);
    }

    @RequireRoles({"SUPPORT", "DIRECTION", "ADMIN_SYSTEME"})
    @PostMapping("/from-incident")
    public Ticket fromIncident(@RequestBody TicketService.TicketCreateRequest request) {
        return ticketService.createFromIncident(request, JwtRoles.agentId());
    }

    @RequireRoles({"SUPPORT", "DIRECTION", "ADMIN_SYSTEME", "DEV", "COMMERCIAL", "FINANCE", "RH", "JURIDIQUE"})
    @GetMapping
    public List<Ticket> list(@RequestParam(required = false) String projectCode,
                             @RequestParam(required = false) Long departementId,
                             @RequestParam(required = false) Long agentId,
                             @RequestParam(required = false) String statut,
                             @RequestParam(required = false) String priorite,
                             @RequestParam(required = false) String systeme,
                             @RequestParam(required = false) String categorie,
                             @RequestParam(required = false) String type,
                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ticketService.search(projectCode, departementId, agentId, statut, priorite, from, to, systeme, categorie, type);
    }

    @RequireRoles({"SUPPORT", "DIRECTION", "ADMIN_SYSTEME", "DEV", "COMMERCIAL", "FINANCE", "RH", "JURIDIQUE"})
    @GetMapping("/departements")
    public List<Map<String, Object>> departements() {
        return ticketService.listDepartements();
    }

    @RequireRoles({"SUPPORT", "DIRECTION", "ADMIN_SYSTEME", "DEV", "COMMERCIAL", "FINANCE", "RH", "JURIDIQUE"})
    @GetMapping("/agents")
    public List<Map<String, Object>> agents() {
        return ticketService.listAssignableAgents();
    }

    @RequireRoles({"SUPPORT", "DIRECTION", "ADMIN_SYSTEME", "DEV", "COMMERCIAL", "FINANCE", "RH", "JURIDIQUE"})
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) String projectCode,
                                         @RequestParam(required = false) String statut,
                                         @RequestParam(required = false) String systeme) {
        List<Ticket> tickets = ticketService.search(projectCode, null, null, statut, null, null, null, systeme, null, null);
        byte[] bytes = ticketService.exportExcel(tickets);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=tickets.xlsx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(bytes);
    }

    @RequireRoles({"SUPPORT", "DIRECTION", "ADMIN_SYSTEME", "DEV", "COMMERCIAL", "FINANCE", "RH", "JURIDIQUE"})
    @GetMapping("/{id}")
    public Ticket get(@PathVariable Long id) {
        return ticketService.get(id);
    }

    @RequireRoles({"SUPPORT", "DIRECTION", "ADMIN_SYSTEME", "DEV", "COMMERCIAL", "FINANCE", "RH", "JURIDIQUE"})
    @PutMapping("/{id}")
    public Ticket update(@PathVariable Long id, @RequestBody TicketService.TicketCreateRequest request) {
        return ticketService.update(id, request, JwtRoles.agentId());
    }

    @RequireRoles({"DIRECTION", "ADMIN_SYSTEME"})
    @PostMapping("/{id}/archive")
    public Ticket archive(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        String motif = body == null ? null : body.get("motif");
        return ticketService.archiver(id, motif, JwtRoles.agentId());
    }

    @RequireRoles({"SUPPORT", "DIRECTION", "ADMIN_SYSTEME", "DEV", "COMMERCIAL", "FINANCE", "RH", "JURIDIQUE"})
    @GetMapping("/{id}/historique")
    public List<TicketStatutHistorique> historique(@PathVariable Long id) {
        return ticketService.historique(id);
    }

    @RequireRoles({"SUPPORT", "DIRECTION", "ADMIN_SYSTEME", "DEV", "COMMERCIAL", "FINANCE", "RH", "JURIDIQUE"})
    @PostMapping("/{id}/statut")
    public Ticket statut(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return ticketService.changeStatut(id, body.get("statut"), body.get("motif"), JwtRoles.agentId());
    }

    @RequireRoles({"SUPPORT", "DIRECTION", "ADMIN_SYSTEME", "DEV", "COMMERCIAL", "FINANCE", "RH", "JURIDIQUE"})
    @PostMapping("/{id}/attribuer")
    public Ticket attribuer(@PathVariable Long id, @RequestBody Map<String, Long> body) {
        return ticketService.attribuer(id, body.get("attributaireId"), JwtRoles.agentId());
    }

    @RequireRoles({"SUPPORT", "DIRECTION", "ADMIN_SYSTEME", "DEV", "COMMERCIAL", "FINANCE", "RH", "JURIDIQUE"})
    @GetMapping("/{id}/reattributions")
    public List<TicketReattribution> reattributions(@PathVariable Long id) {
        return ticketService.reattributions(id);
    }

    @RequireRoles({"SUPPORT", "DIRECTION", "ADMIN_SYSTEME", "DEV", "COMMERCIAL", "FINANCE", "RH", "JURIDIQUE"})
    @PostMapping("/{id}/commentaires")
    public TicketCommentaire commenter(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return ticketService.commenter(id, JwtRoles.agentId(), body.get("contenu"));
    }

    @RequireRoles({"SUPPORT", "DIRECTION", "ADMIN_SYSTEME", "DEV", "COMMERCIAL", "FINANCE", "RH", "JURIDIQUE"})
    @PostMapping(value = "/{id}/pieces-jointes", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public TicketPieceJointe piece(@PathVariable Long id, @RequestParam("fichier") MultipartFile fichier) throws Exception {
        return ticketService.attacher(id, fichier);
    }

    @RequireRoles({"SUPPORT", "DIRECTION", "ADMIN_SYSTEME", "DEV", "COMMERCIAL", "FINANCE", "RH", "JURIDIQUE"})
    @GetMapping("/{id}/pieces-jointes/{pieceId}")
    public ResponseEntity<byte[]> downloadPiece(@PathVariable Long id, @PathVariable Long pieceId) {
        TicketPieceJointe piece = ticketService.getPiece(id, pieceId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + (piece.getNomFichier() == null ? "piece" : piece.getNomFichier()) + "\"")
                .contentType(MediaType.parseMediaType(piece.getTypeMime() == null ? "application/octet-stream" : piece.getTypeMime()))
                .body(piece.getContenu());
    }
}
