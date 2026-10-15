package com.platform.api;

import com.platform.application.SubsidyApplication;
import com.platform.application.SubsidyApplicationRepository;
import com.platform.disbursement.DisbursementMilestone;
import com.platform.disbursement.DisbursementPlan;
import com.platform.disbursement.DisbursementPlanRepository;
import com.platform.security.JurisdictionService;
import com.platform.user.Beneficiary;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * Milestone 4C.2 read-only integration API.
 *
 * Kept deliberately in one controller with nested response records so the
 * existing Milestone 1-3 source stays locked and 4C.2 adds only one Java file.
 */
@RestController
@RequestMapping("/api/v1/officer")
public class OfficerApiController {

    private final SubsidyApplicationRepository applicationRepository;
    private final DisbursementPlanRepository planRepository;
    private final JurisdictionService jurisdictionService;

    public OfficerApiController(SubsidyApplicationRepository applicationRepository,
                                DisbursementPlanRepository planRepository,
                                JurisdictionService jurisdictionService) {
        this.applicationRepository = applicationRepository;
        this.planRepository = planRepository;
        this.jurisdictionService = jurisdictionService;
    }

    @GetMapping("/applications")
    @Transactional(readOnly = true)
    public List<OfficerApplicationResponse> applications(
            @AuthenticationPrincipal UserDetails principal) {

        return jurisdictionService
                .filter(principal, applicationRepository.findAllByOrderByApplicationDateDesc())
                .stream()
                .map(this::applicationResponse)
                .toList();
    }

    @GetMapping("/applications/{applicationId}")
    @Transactional(readOnly = true)
    public OfficerApplicationResponse application(
            @PathVariable Long applicationId,
            @AuthenticationPrincipal UserDetails principal) {

        SubsidyApplication app = authorizedApplication(applicationId, principal);
        return applicationResponse(app);
    }

    @GetMapping("/disbursements/application/{applicationId}")
    @Transactional(readOnly = true)
    public DisbursementResponse disbursement(
            @PathVariable Long applicationId,
            @AuthenticationPrincipal UserDetails principal) {

        SubsidyApplication app = authorizedApplication(applicationId, principal);
        DisbursementPlan plan = planRepository.findByApplicationId(applicationId)
                .orElseThrow(() -> notFound("Disbursement plan not found."));

        return new DisbursementResponse(
                plan.getId(),
                app.getId(),
                app.getApplicationStatus().name(),
                app.getScheme().getSchemeName(),
                plan.getStatus().name(),
                plan.getTotalPlannedAmount(),
                plan.getReleasedAmount(),
                plan.getPendingAmount(),
                plan.getUtilizationPercentage(),
                plan.getMilestones().stream().map(this::milestoneResponse).toList()
        );
    }

    private SubsidyApplication authorizedApplication(Long applicationId,
                                                     UserDetails principal) {
        SubsidyApplication app = applicationRepository.findDetailedById(applicationId)
                .orElseThrow(() -> notFound("Application not found."));

        // Return 404 instead of revealing that an out-of-jurisdiction ID exists.
        if (!jurisdictionService.canAccess(principal, app)) {
            throw notFound("Application not found.");
        }
        return app;
    }

    private OfficerApplicationResponse applicationResponse(SubsidyApplication app) {
        Beneficiary beneficiary =
                app.getUser() == null ? null : app.getUser().getBeneficiary();

        return new OfficerApplicationResponse(
                app.getId(),
                app.getScheme().getId(),
                app.getScheme().getSchemeName(),
                beneficiary == null ? null : beneficiary.getFullName(),
                beneficiary == null ? null : beneficiary.getState(),
                beneficiary == null ? null : beneficiary.getDistrict(),
                app.getApplicationStatus().name(),
                app.getApplicationDate(),
                app.getEligibilityScore(),
                app.getResubmissionCount(),
                app.getRejectionCount(),
                app.getSanctionAmount(),
                app.getPaymentBankVerificationStatus(),
                app.isPaymentBankDetailsVerified(),
                app.getDisbursedAt()
        );
    }

    private MilestoneResponse milestoneResponse(DisbursementMilestone milestone) {
        return new MilestoneResponse(
                milestone.getId(),
                milestone.getSequenceNo(),
                milestone.getMilestoneName(),
                milestone.getComplianceRequirement(),
                milestone.getDueDate(),
                milestone.getDueTime(),
                milestone.getScheduledAmount(),
                milestone.getReleasedAmount(),
                milestone.getStatus().name(),
                milestone.isComplianceRequested(),
                milestone.getComplianceSubmittedDate(),
                milestone.getComplianceVerifiedDate(),
                milestone.getReleaseDate(),
                milestone.getReleasedAt(),
                milestone.getReleaseReference()
        );
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    public record OfficerApplicationResponse(
            Long applicationId,
            Long schemeId,
            String schemeName,
            String citizenName,
            String citizenState,
            String citizenDistrict,
            String status,
            LocalDateTime applicationDate,
            int eligibilityScore,
            int resubmissionCount,
            int rejectionCount,
            double sanctionAmount,
            String bankVerificationStatus,
            boolean bankDetailsVerified,
            LocalDateTime disbursedAt) {
    }

    public record DisbursementResponse(
            Long planId,
            Long applicationId,
            String applicationStatus,
            String schemeName,
            String planStatus,
            double totalPlannedAmount,
            double releasedAmount,
            double pendingAmount,
            double utilizationPercentage,
            List<MilestoneResponse> milestones) {
    }

    public record MilestoneResponse(
            Long milestoneId,
            int sequenceNo,
            String name,
            String complianceRequirement,
            LocalDate dueDate,
            LocalTime dueTime,
            double scheduledAmount,
            double releasedAmount,
            String status,
            boolean complianceRequested,
            LocalDate complianceSubmittedDate,
            LocalDate complianceVerifiedDate,
            LocalDate releaseDate,
            LocalDateTime releasedAt,
            String releaseReference) {
    }
}
