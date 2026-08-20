package com.tradex.compliance.repository;

import com.tradex.compliance.entity.ComplianceAuditRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ComplianceAuditRepository extends JpaRepository<ComplianceAuditRecord, UUID> {

    Page<ComplianceAuditRecord> findByUserId(UUID userId, Pageable pageable);
}
