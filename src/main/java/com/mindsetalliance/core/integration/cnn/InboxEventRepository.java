package com.mindsetalliance.core.integration.cnn;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

public interface InboxEventRepository extends JpaRepository<InboxEvent, String> {

    @Modifying
    @Transactional
    @Query("delete from InboxEvent e where e.receivedAt < :before")
    int purgeBefore(Instant before);
}
