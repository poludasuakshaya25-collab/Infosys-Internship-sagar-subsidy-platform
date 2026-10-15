package com.platform.api.dto;

import java.time.LocalDateTime;

public record ApplicationTrackingResponse(
        Long applicationId,
        Long schemeId,
        String schemeName,
        String status,
        LocalDateTime applicationDate,
        boolean eligible,
        int eligibilityScore,
        int resubmissionCount,
        int rejectionCount,
        boolean awaitingResubmission,
        LocalDateTime coolingPeriodEndDate,
        double sanctionAmount,
        String bankVerificationStatus,
        boolean bankDetailsVerified,
        LocalDateTime disbursedAt
) {}
