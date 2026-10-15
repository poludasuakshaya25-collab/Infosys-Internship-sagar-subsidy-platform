package com.platform.disbursement;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface DisbursementPlanRepository extends JpaRepository<DisbursementPlan, Long> {

    @Query("select distinct p from DisbursementPlan p left join fetch p.milestones where p.fundReviewFlagged = true order by p.fundReviewRaisedAt desc")
    java.util.List<DisbursementPlan> findByFundReviewFlaggedTrueOrderByFundReviewRaisedAtDesc();

    @Query("select distinct p from DisbursementPlan p " +
            "left join fetch p.milestones " +
            "where p.application.id = :applicationId")
    Optional<DisbursementPlan> findByApplicationId(@Param("applicationId") Long applicationId);
}
