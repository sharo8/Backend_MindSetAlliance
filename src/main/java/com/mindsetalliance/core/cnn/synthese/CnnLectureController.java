package com.mindsetalliance.core.cnn.synthese;

import com.mindsetalliance.core.common.security.JwtRoles;
import com.mindsetalliance.core.iam.EffectivePermissionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Consultation des listes Colis na Nga (lecture seule, en direct dans sa base).
 * Permission : action VIEW du domaine concerné.
 */
@RestController
@RequestMapping("/api/cnn/lecture")
public class CnnLectureController {

    private static final Logger log = LoggerFactory.getLogger(CnnLectureController.class);
    private static final String PROJECT = "CNN";

    private final CnnLectureService service;
    private final CnnStatistiquesService statistiques;
    private final EffectivePermissionService permissions;

    public CnnLectureController(CnnLectureService service, CnnStatistiquesService statistiques, EffectivePermissionService permissions) {
        this.service = service;
        this.statistiques = statistiques;
        this.permissions = permissions;
    }

    /** Recherche d'une commande (en cours ou passée) pour retracer son itinéraire sur la carte. */
    @GetMapping("/carte/commandes")
    public ResponseEntity<?> carteCommandes(@RequestParam(required = false) String search, @RequestParam(required = false) String status) {
        return run("VIEW_CNN_DASHBOARD", () -> service.rechercheCourses(search, status));
    }

    /** Itinéraire d'une commande : départ, arrêts, chronologie des statuts, tracé GPS enregistré. */
    @GetMapping("/orders/{id}/itineraire")
    public ResponseEntity<?> itineraire(@PathVariable String id) {
        return run("VIEW_CNN_DASHBOARD", () -> {
            Map<String, Object> body = service.itineraire(id);
            if (body == null) throw new IllegalArgumentException("introuvable");
            return body;
        });
    }

    /** Tous les clients : ponctuels et abonnés, avec leur activité. */
    @GetMapping("/clients")
    public ResponseEntity<?> clients(@RequestParam(required = false) String type, @RequestParam(required = false) String search) {
        return run("MANAGE_CNN_CLIENTS", () -> service.clients(type, search));
    }

    /** Commandes d'un client. */
    @GetMapping("/clients/{id}/orders")
    public ResponseEntity<?> clientOrders(@PathVariable String id) {
        return run("MANAGE_CNN_CLIENTS", () -> service.commandes(null, null, id));
    }

    /** Historique des crédits achetés ou offerts aux motards. */
    @GetMapping("/credits")
    public ResponseEntity<?> credits() {
        return run("MANAGE_CNN_MOTARDS", service::credits);
    }

    /** Statistiques sur 7, 30, 90 ou 365 jours. */
    @GetMapping("/statistiques")
    public ResponseEntity<?> statistiques(@RequestParam(defaultValue = "30") int jours) {
        return run("VIEW_CNN_DASHBOARD", () -> statistiques.statistiques(jours));
    }

    /** Carte en direct : livreurs, motards, demandes, courses, associations, urgences. */
    @GetMapping("/carte")
    public ResponseEntity<?> carte() {
        return run("VIEW_CNN_DASHBOARD", service::carte);
    }

    @GetMapping("/orders")
    public ResponseEntity<?> orders(@RequestParam(required = false) String status, @RequestParam(required = false) String search) {
        return run("MANAGE_CNN_ORDERS", () -> service.commandes(status, search));
    }

    @GetMapping("/agents")
    public ResponseEntity<?> agents(@RequestParam(required = false) String status, @RequestParam(required = false) String search) {
        return run("MANAGE_CNN_AGENTS", () -> service.livreurs(status, search));
    }

    @GetMapping("/motards")
    public ResponseEntity<?> motards(@RequestParam(required = false) String validationStatus, @RequestParam(required = false) String search) {
        return run("MANAGE_CNN_MOTARDS", () -> service.motards(validationStatus, search));
    }

    @GetMapping("/associations")
    public ResponseEntity<?> associations(@RequestParam(required = false) String search) {
        return run("MANAGE_CNN_ASSOCIATIONS", () -> service.associations(search));
    }

    @GetMapping("/associations/{id}")
    public ResponseEntity<?> association(@PathVariable String id) {
        return run("MANAGE_CNN_ASSOCIATIONS", () -> {
            Map<String, Object> a = service.association(id);
            if (a == null) throw new IllegalArgumentException("introuvable");
            return a;
        });
    }

    @GetMapping("/associations/{id}/motards")
    public ResponseEntity<?> associationMotards(@PathVariable String id) {
        return run("MANAGE_CNN_ASSOCIATIONS", () -> service.motardsDeLAssociation(id));
    }

    private ResponseEntity<?> run(String permission, Supplier<Object> body) {
        check(permission);
        if (!service.disponible()) {
            return error(HttpStatus.SERVICE_UNAVAILABLE, "Source Colis na Nga non configurée (MA_CNN_DB_URL).");
        }
        try {
            return ResponseEntity.ok(body.get());
        } catch (IllegalArgumentException e) {
            return error(HttpStatus.NOT_FOUND, "Élément introuvable dans Colis na Nga.");
        } catch (org.springframework.jdbc.BadSqlGrammarException e) {
            // Colonne ou table différente dans la base Colis na Nga : message précis pour corriger vite.
            String cause = e.getMostSpecificCause().getMessage();
            log.warn("Lecture Colis na Nga : requête refusée par la base : {}", cause);
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "Lecture Colis na Nga impossible : " + cause);
        } catch (Exception e) {
            log.warn("Lecture Colis na Nga impossible : {}", e.getMessage());
            return error(HttpStatus.BAD_GATEWAY, "Base Colis na Nga injoignable. Vérifiez la connexion (Cloud SQL Auth Proxy).");
        }
    }

    private void check(String permission) {
        Jwt jwt = JwtRoles.currentJwt();
        Long agentId = JwtRoles.agentId();
        boolean allowed = agentId != null
                && JwtRoles.canSeeProject(PROJECT)
                && !permissions.isDenied(agentId, permission, PROJECT)
                && (JwtRoles.hasFullAccess(jwt) || permissions.hasAction(agentId, permission, "VIEW", PROJECT));
        if (!allowed) {
            throw new AccessDeniedException("Permission insuffisante");
        }
    }

    private static ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("status", status.value(), "message", message));
    }
}
