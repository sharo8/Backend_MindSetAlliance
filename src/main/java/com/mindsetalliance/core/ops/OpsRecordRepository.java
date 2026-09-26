package com.mindsetalliance.core.ops;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OpsRecordRepository extends JpaRepository<OpsRecord, Long> {
    List<OpsRecord> findByModuleOrderByCreatedAtDesc(String module);
}
