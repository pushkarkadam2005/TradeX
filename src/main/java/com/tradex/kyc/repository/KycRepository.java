package com.tradex.kyc.repository;

import com.tradex.kyc.entity.KycVerification;
import com.tradex.kyc.enums.KycStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface KycRepository extends JpaRepository<KycVerification, UUID> {

    Optional<KycVerification> findByUserId(UUID userId);

    boolean existsByUserId(UUID userId);

    Page<KycVerification> findByStatus(KycStatus status, Pageable pageable);
}
