package com.platform.application;

import com.platform.scheme.SchemeRepository;
import com.platform.user.Beneficiary;
import com.platform.user.BeneficiaryRepository;
import com.platform.security.JurisdictionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

/**
 * Legacy eligibility controller — kept for backward compatibility with any
 * existing routes that reference /eligibility/officer/field etc.
 * New role-specific workflow endpoints are in WebController.
 */
@Controller
@RequestMapping("/eligibility")
public class EligibilityController {

    @Autowired private EligibilityService eligibilityService;
    @Autowired private BeneficiaryRepository beneficiaryRepository;
    @Autowired private SubsidyApplicationRepository applicationRepository;
    @Autowired private SchemeRepository schemeRepository;
    @Autowired private JurisdictionService jurisdictionService;

    // Legacy field officer route — redirects to new dashboard
    @GetMapping("/officer/field")
    public String showFieldOfficerDesk() {
        return "redirect:/dashboard/officer";
    }

    // Legacy district officer route
    @GetMapping("/officer/district")
    public String showDistrictOfficerDesk() {
        return "redirect:/dashboard/officer";
    }

    // Legacy finance approver route
    @GetMapping("/officer/finance")
    public String showFinanceOfficerDesk() {
        return "redirect:/dashboard/officer";
    }

    // Admin monitor — moved to WebController#showAdminMonitor
    // (GET /eligibility/admin/monitor) to avoid ambiguous mapping.

    // Legacy unified action — now split into role-specific endpoints in WebController
    // Kept here as fallback
    @PostMapping("/officer/action")
    public String legacyProcessGovernanceAction(@RequestParam Long appId,
                                                @RequestParam String workflowAction,
                                                @RequestParam(required = false) String remarks,
                                                @RequestParam(required = false) String originQueue,
                                                @AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) return "redirect:/login";

        SubsidyApplication application = applicationRepository.findDetailedById(appId).orElse(null);
        if (application == null) return "redirect:/dashboard/officer";

        // This legacy compatibility endpoint must enforce the same server-side
        // jurisdiction rules as the newer role-specific officer endpoints.
        if (!jurisdictionService.canAccess(userDetails, application)) {
            return "redirect:/dashboard/officer?error=UnauthorizedJurisdiction";
        }

        // Do not trust originQueue supplied by the browser to decide authority.
        // The authenticated role must match the workflow action being requested.
        if ("DISTRICT_REVIEW".equals(originQueue)) {
            if (!hasRole(userDetails, "ROLE_DISTRICT_OFFICER")) {
                return "redirect:/dashboard/officer?error=UnauthorizedRole";
            }
            eligibilityService.districtOfficerAction(appId, workflowAction, remarks);

        } else if ("FINANCE_PENDING".equals(originQueue)) {
            if (!hasRole(userDetails, "ROLE_FINANCE_APPROVER")) {
                return "redirect:/dashboard/officer?error=UnauthorizedRole";
            }
            eligibilityService.financeApproverAction(appId, workflowAction, remarks, 0);

        } else if ("FUND_DISBURSEMENT".equals(originQueue)) {
            if (!hasAnyRole(userDetails, "ROLE_FINANCE_APPROVER", "ROLE_DISBURSEMENT_OFFICER")) {
                return "redirect:/dashboard/officer?error=UnauthorizedRole";
            }
            // Milestone 3 is Finance-owned. Legacy pages enter the staged desk
            // rather than bypassing the milestone workflow.
            return "redirect:/milestone3/disbursement/" + appId;

        } else {
            if (!hasRole(userDetails, "ROLE_FIELD_OFFICER")) {
                return "redirect:/dashboard/officer?error=UnauthorizedRole";
            }
            eligibilityService.fieldOfficerAction(appId, workflowAction, remarks);
        }

        return "redirect:/dashboard/officer";
    }

    private boolean hasRole(UserDetails userDetails, String role) {
        return userDetails != null && userDetails.getAuthorities().stream()
                .anyMatch(a -> role.equals(a.getAuthority()));
    }

    private boolean hasAnyRole(UserDetails userDetails, String... roles) {
        if (userDetails == null) return false;
        java.util.Set<String> allowed = java.util.Set.of(roles);
        return userDetails.getAuthorities().stream()
                .anyMatch(a -> allowed.contains(a.getAuthority()));
    }
}