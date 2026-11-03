package com.praveen.wallet.repo;

import com.praveen.wallet.domain.TransferAudit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TransferAuditRepository extends JpaRepository<TransferAudit, UUID> {
}
