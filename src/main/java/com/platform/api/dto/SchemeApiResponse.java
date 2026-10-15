package com.platform.api.dto;

public record SchemeApiResponse(
        Long id,
        String schemeName,
        String description,
        String benefitSummary,
        String targetBeneficiary,
        int eligibleAgeMin,
        int eligibleAgeMax,
        double maxIncomeThreshold,
        double maxSanctionAmount,
        String requiredDocuments,
        String optionalDocuments
) {}
