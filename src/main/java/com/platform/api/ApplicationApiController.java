package com.platform.api;

import com.platform.api.dto.ApplicationTrackingResponse;
import com.platform.application.SubsidyApplication;
import com.platform.application.SubsidyApplicationRepository;
import com.platform.user.User;
import com.platform.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/api/v1/applications")
public class ApplicationApiController {

    private final SubsidyApplicationRepository applicationRepository;
    private final UserRepository userRepository;

    public ApplicationApiController(SubsidyApplicationRepository applicationRepository,
                                    UserRepository userRepository) {
        this.applicationRepository = applicationRepository;
        this.userRepository = userRepository;
    }

    @GetMapping("/my")
    public List<ApplicationTrackingResponse> myApplications(
            @AuthenticationPrincipal UserDetails principal) {
        User user = currentUser(principal);
        return applicationRepository.findByUser(user).stream()
                .sorted(Comparator.comparing(SubsidyApplication::getApplicationDate).reversed())
                .map(this::toResponse)
                .toList();
    }

    @GetMapping("/{applicationId}")
    public ApplicationTrackingResponse myApplication(
            @PathVariable Long applicationId,
            @AuthenticationPrincipal UserDetails principal) {
        User user = currentUser(principal);
        SubsidyApplication app = applicationRepository.findDetailedById(applicationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Application not found."));

        if (app.getUser() == null || !app.getUser().getId().equals(user.getId())) {
            // Do not reveal whether another citizen's application exists.
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Application not found.");
        }
        return toResponse(app);
    }

    private User currentUser(UserDetails principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Authentication is required.");
        }
        return userRepository.findByEmail(principal.getUsername())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "Authenticated user no longer exists."));
    }

    private ApplicationTrackingResponse toResponse(SubsidyApplication app) {
        return new ApplicationTrackingResponse(
                app.getId(),
                app.getScheme().getId(),
                app.getScheme().getSchemeName(),
                app.getApplicationStatus().name(),
                app.getApplicationDate(),
                app.isEligibilityStatus(),
                app.getEligibilityScore(),
                app.getResubmissionCount(),
                app.getRejectionCount(),
                app.isAwaitingResubmission(),
                app.getCoolingPeriodEndDate(),
                app.getSanctionAmount(),
                app.getPaymentBankVerificationStatus(),
                app.isPaymentBankDetailsVerified(),
                app.getDisbursedAt()
        );
    }
}
