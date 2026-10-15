package com.platform.security;

import com.platform.application.ApplicationDocument;
import com.platform.application.ApplicationDocumentRepository;
import com.platform.application.SubsidyApplication;
import com.platform.application.SubsidyApplicationRepository;
import com.platform.disbursement.DisbursementMilestone;
import com.platform.disbursement.DisbursementMilestoneRepository;
import com.platform.disbursement.DisbursementPlanService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class OfficerJurisdictionInterceptor implements HandlerInterceptor {
    private static final Pattern DOCUMENT = Pattern.compile("/officer/(?:field|district)/document/(\\d+)(?:/.*)?");
    private static final Pattern OFFICER_APP = Pattern.compile("/officer/(?:field|district|finance|disbursal|application)(?:/eligibility)?/(\\d+)(?:/.*)?");
    private static final Pattern M3_APP = Pattern.compile("/milestone3/disbursement/(\\d+)(?:/.*)?");
    private static final Pattern M3_MILESTONE = Pattern.compile("/milestone3/(?:citizen/)?milestone/(\\d+)(?:/.*)?");

    private final SubsidyApplicationRepository applicationRepository;
    private final ApplicationDocumentRepository documentRepository;
    private final DisbursementMilestoneRepository milestoneRepository;
    private final JurisdictionService jurisdictionService;
    private final DisbursementPlanService disbursementPlanService;

    public OfficerJurisdictionInterceptor(SubsidyApplicationRepository applicationRepository,
                                          ApplicationDocumentRepository documentRepository,
                                          DisbursementMilestoneRepository milestoneRepository,
                                          JurisdictionService jurisdictionService,
                                          DisbursementPlanService disbursementPlanService) {
        this.applicationRepository = applicationRepository;
        this.documentRepository = documentRepository;
        this.milestoneRepository = milestoneRepository;
        this.jurisdictionService = jurisdictionService;
        this.disbursementPlanService = disbursementPlanService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        UserDetails principal = principal();
        if (principal == null || !isOfficer(principal)) return true;
        if (has(principal, "ROLE_ADMIN")) return true;

        SubsidyApplication app = resolveApplication(request.getRequestURI());
        if (app == null) return true;

        if (!jurisdictionService.canAccess(principal, app)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Application is outside your assigned jurisdiction.");
            return false;
        }
        return true;
    }

    private SubsidyApplication resolveApplication(String uri) {
        Matcher m = DOCUMENT.matcher(uri);
        if (m.matches()) {
            ApplicationDocument d = documentRepository.findById(Long.valueOf(m.group(1))).orElse(null);
            return d == null ? null : d.getApplication();
        }
        m = OFFICER_APP.matcher(uri);
        if (m.matches()) return applicationRepository.findDetailedById(Long.valueOf(m.group(1))).orElse(null);

        m = M3_APP.matcher(uri);
        if (m.matches()) return applicationRepository.findDetailedById(Long.valueOf(m.group(1))).orElse(null);

        m = M3_MILESTONE.matcher(uri);
        if (m.matches()) {
            Long applicationId = disbursementPlanService.getApplicationIdForMilestone(Long.valueOf(m.group(1)));
            return applicationRepository.findDetailedById(applicationId).orElse(null);
        }
        return null;
    }

    private UserDetails principal() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a != null && a.getPrincipal() instanceof UserDetails u ? u : null;
    }

    private boolean isOfficer(UserDetails u) {
        return u.getAuthorities().stream().anyMatch(a -> a.getAuthority().matches(
                "ROLE_(FIELD_OFFICER|DISTRICT_OFFICER|FINANCE_APPROVER|DISBURSEMENT_OFFICER|ADMIN)"));
    }
    private boolean has(UserDetails u, String role) {
        return u.getAuthorities().stream().anyMatch(a -> role.equals(a.getAuthority()));
    }
}
