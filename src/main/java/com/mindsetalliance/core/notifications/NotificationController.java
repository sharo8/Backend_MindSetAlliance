package com.mindsetalliance.core.notifications;

import com.mindsetalliance.core.common.security.JwtRoles;
import com.mindsetalliance.core.common.security.RequireRoles;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
class NotificationController {
    private final NotificationService notificationService;

    NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public List<Notification> list() {
        return notificationService.mine(JwtRoles.agentId());
    }

    @PostMapping("/{id}/lu")
    public Notification markRead(@PathVariable Long id) {
        return notificationService.markRead(JwtRoles.agentId(), id);
    }

    @RequireRoles({"ADMIN_SYSTEME", "DIRECTION"})
    @PostMapping
    public Notification send(@RequestBody NotificationRequest request) {
        return notificationService.send(request.agentId(), request.canal(), request.titre(), request.message());
    }

    record NotificationRequest(Long agentId, String canal, String titre, String message) {}
}
