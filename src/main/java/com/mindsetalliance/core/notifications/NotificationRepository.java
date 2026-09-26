package com.mindsetalliance.core.notifications;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByAgentIdOrderByCreatedAtDesc(Long agentId);
}
