package com.platform.disbursement;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DisbursementMilestoneRepository extends JpaRepository<DisbursementMilestone, Long> {
    boolean existsByReleaseReferenceIgnoreCase(String releaseReference);
}