package com.platform.admin;

import com.platform.application.ApplicationStatus;
import com.platform.application.SubsidyApplication;
import com.platform.application.SubsidyApplicationRepository;
import com.platform.disbursement.DisbursementAnalyticsService;
import com.platform.scheme.Scheme;
import com.platform.scheme.SchemeService;
import com.platform.user.User;
import com.platform.user.UserRepository;
import com.platform.audit.AuditLogService;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/admin")
public class AdminController {

    @Autowired private SchemeService schemeService;
    @Autowired private SubsidyApplicationRepository applicationRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private DisbursementAnalyticsService analyticsService;
    @Autowired private AuditLogService auditLogService;
    @Autowired private com.platform.disbursement.DisbursementPlanService planService;

    @GetMapping({"", "/dashboard"})
    public String dashboard(@AuthenticationPrincipal UserDetails userDetails,
                            @RequestParam(value = "edit", required = false) Long editId,
                            @RequestParam(value = "fundReviewOnly", defaultValue = "false") boolean fundReviewOnly,
                            Model model) {
        if (userDetails == null) return "redirect:/login";

        List<Scheme> schemes = schemeService.getAllSchemes();
        List<SubsidyApplication> applications = applicationRepository.findAllByOrderByApplicationDateDesc();
        List<User> users = userRepository.findAll();

        Map<ApplicationStatus, Long> statusCounts = new EnumMap<>(ApplicationStatus.class);
        for (ApplicationStatus status : ApplicationStatus.values()) {
            statusCounts.put(status, applications.stream()
                    .filter(a -> a.getApplicationStatus() == status)
                    .count());
        }

        SchemeAdminForm form = new SchemeAdminForm();
        if (editId != null) {
            form = SchemeAdminForm.from(schemeService.getSchemeById(editId));
        }

        List<SubsidyApplication> recentApplications = new ArrayList<>(applications);
        recentApplications.sort(Comparator.comparing(SubsidyApplication::getApplicationDate,
                Comparator.nullsLast(Comparator.reverseOrder())));
        if (recentApplications.size() > 12) {
            recentApplications = new ArrayList<>(recentApplications.subList(0, 12));
        }

        model.addAttribute("fundReviewFlags", planService.getFlaggedPlans());
        model.addAttribute("fundReviewOnly", fundReviewOnly);
        model.addAttribute("schemes", schemes);
        model.addAttribute("schemeForm", form);
        model.addAttribute("applications", applications);
        model.addAttribute("recentApplications", recentApplications);
        model.addAttribute("users", users);
        model.addAttribute("statusCounts", statusCounts);
        addNamedStatusCounts(model, statusCounts);
        addRoleCounts(model, users);
        model.addAttribute("analytics", analyticsService.buildSnapshot());
        List<com.platform.audit.AuditLog> auditLogs = auditLogService.getRecentLogs().stream()
                .filter(AdminController::visibleInAdminAudit)
                .toList();
        model.addAttribute("auditLogs", auditLogs);
        model.addAttribute("recentAuditLogs", auditLogs.size() > 8 ? auditLogs.subList(0, 8) : auditLogs);
        model.addAttribute("applicationStatuses", ApplicationStatus.values());
        User adminUser = userRepository.findByEmail(userDetails.getUsername()).orElse(null);
        List<Map<String,String>> adminEvents = new ArrayList<>();
        if (adminUser != null) {
            for (com.platform.audit.AuditLog log : auditLogs) {
                if (!importantAdminNotification(log)) continue;
                if (adminEvents.size() >= 12) break;
                Map<String,String> event = new java.util.LinkedHashMap<>();
                event.put("key", "ADMIN:" + log.getId());
                event.put("title", log.getAction());
                event.put("detail", log.getEntityType() + " #" + log.getEntityId());
                event.put("read", String.valueOf(adminUser.getReadNotificationKeys().contains(event.get("key"))));
                adminEvents.add(event);
            }
        }
        model.addAttribute("adminEvents", adminEvents);
        model.addAttribute("adminUnreadCount", adminEvents.stream().filter(e -> !"true".equals(e.get("read"))).count());

        return "admin-dashboard";
    }

    @PostMapping("/fund-review/{planId}/clear")
    public String clearFundReview(@PathVariable Long planId,
                                  @RequestParam String resolution,
                                  @AuthenticationPrincipal UserDetails principal,
                                  RedirectAttributes redirectAttributes) {
        if (principal == null || principal.getAuthorities().stream().noneMatch(a ->
                "ROLE_ADMIN".equals(a.getAuthority()))) return "redirect:/login";
        try {
            planService.clearFundReviewFlag(planId, resolution, principal.getUsername());
            redirectAttributes.addFlashAttribute("success", "Fund review flag cleared. Finance workflow remains on hold until separately resumed.");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/dashboard?fundReviewOnly=true#fund-review";
    }

    @PostMapping("/notifications/read")
    public String markAdminNotificationRead(@AuthenticationPrincipal UserDetails principal,
                                            @RequestParam("key") String key) {
        if (principal == null || principal.getAuthorities().stream().noneMatch(a ->
                a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ADMIN"))) {
            return "redirect:/login";
        }
        User user = userRepository.findByEmail(principal.getUsername()).orElse(null);
        if (user == null || !key.matches("ADMIN:[0-9]+")) return "redirect:/admin/dashboard";
        Long auditId = Long.valueOf(key.substring(6));
        boolean visible = auditLogService.getRecentLogs().stream()
                .anyMatch(log -> java.util.Objects.equals(log.getId(), auditId)
                        && importantAdminNotification(log) && visibleInAdminAudit(log));
        if (visible) {
            user.getReadNotificationKeys().add(key);
            userRepository.save(user);
        }
        return "redirect:/admin/dashboard";
    }

    @PostMapping("/schemes/save")
    public String saveScheme(@Valid @ModelAttribute("schemeForm") SchemeAdminForm form,
                             BindingResult result,
                             RedirectAttributes redirectAttributes,
                             Model model) {
        if (result.hasErrors()) {
            return rebuildDashboardAfterValidation(model, form);
        }

        try {
            boolean creating = form.getId() == null;
            Scheme saved = schemeService.createOrUpdateFromAdmin(form);
            auditLogService.record("SCHEME", saved.getId(), creating ? "SCHEME_CREATED" : "SCHEME_UPDATED",
                    null, saved.getSchemeName(), creating
                            ? "Administrator created a new scheme master record."
                            : "Administrator updated scheme master data.");
            redirectAttributes.addFlashAttribute("success", creating
                    ? "Scheme created successfully."
                    : "Scheme updated successfully.");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", readableError(ex));
        }
        return "redirect:/admin/dashboard";
    }

    @PostMapping("/schemes/delete/{id}")
    public String deleteScheme(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            Scheme scheme = schemeService.getSchemeById(id);
            String schemeName = scheme.getSchemeName();
            schemeService.deleteByAdmin(id);
            auditLogService.record("SCHEME", id, "SCHEME_DELETED", schemeName, null,
                    "Administrator deleted an unused scheme master record.");
            redirectAttributes.addFlashAttribute("success", "Scheme deleted successfully.");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", readableError(ex));
        }
        return "redirect:/admin/dashboard";
    }

    private String rebuildDashboardAfterValidation(Model model, SchemeAdminForm form) {
        List<Scheme> schemes = schemeService.getAllSchemes();
        List<SubsidyApplication> applications = applicationRepository.findAllByOrderByApplicationDateDesc();
        List<User> users = userRepository.findAll();
        Map<ApplicationStatus, Long> statusCounts = new EnumMap<>(ApplicationStatus.class);
        for (ApplicationStatus status : ApplicationStatus.values()) {
            statusCounts.put(status, applications.stream()
                    .filter(a -> a.getApplicationStatus() == status)
                    .count());
        }
        model.addAttribute("schemes", schemes);
        model.addAttribute("schemeForm", form);
        model.addAttribute("applications", applications);
        model.addAttribute("recentApplications", applications.stream().limit(12).toList());
        model.addAttribute("users", users);
        model.addAttribute("statusCounts", statusCounts);
        addNamedStatusCounts(model, statusCounts);
        addRoleCounts(model, users);
        model.addAttribute("analytics", analyticsService.buildSnapshot());
        List<com.platform.audit.AuditLog> auditLogs = auditLogService.getRecentLogs();
        model.addAttribute("auditLogs", auditLogs);
        model.addAttribute("recentAuditLogs", auditLogs.size() > 8 ? auditLogs.subList(0, 8) : auditLogs);
        model.addAttribute("applicationStatuses", ApplicationStatus.values());
        // Validation rerender has no authenticated principal argument; avoid using
        // dashboard-only local variables here.
        model.addAttribute("adminEvents", java.util.Collections.emptyList());
        model.addAttribute("adminUnreadCount", 0L);

        return "admin-dashboard";
    }

    private void addNamedStatusCounts(Model model, Map<ApplicationStatus, Long> counts) {
        model.addAttribute("fieldCount", counts.getOrDefault(ApplicationStatus.FIELD_VERIFICATION, 0L));
        model.addAttribute("districtCount", counts.getOrDefault(ApplicationStatus.DISTRICT_REVIEW, 0L));
        model.addAttribute("financeCount", counts.getOrDefault(ApplicationStatus.FINANCE_PENDING, 0L));
        model.addAttribute("disbursementCount", counts.getOrDefault(ApplicationStatus.FUND_DISBURSEMENT, 0L));
        model.addAttribute("approvedCount", counts.getOrDefault(ApplicationStatus.APPROVED, 0L));
        model.addAttribute("rejectedCount", counts.getOrDefault(ApplicationStatus.REJECTED, 0L));
        model.addAttribute("reverificationCount", counts.getOrDefault(ApplicationStatus.APPLICANT_REVERIFICATION, 0L));
    }

    private void addRoleCounts(Model model, List<User> users) {
        long admins = users.stream().filter(u -> hasRole(u, "ROLE_ADMIN")).count();
        long field = users.stream().filter(u -> hasRole(u, "ROLE_FIELD_OFFICER")).count();
        long district = users.stream().filter(u -> hasRole(u, "ROLE_DISTRICT_OFFICER")).count();
        long finance = users.stream().filter(u -> hasRole(u, "ROLE_FINANCE_APPROVER")).count();
        long disbursement = users.stream().filter(u -> hasRole(u, "ROLE_DISBURSEMENT_OFFICER")).count();
        long beneficiaries = users.stream().filter(u -> hasRole(u, "ROLE_BENEFICIARY")).count();
        model.addAttribute("adminUsers", admins);
        model.addAttribute("fieldUsers", field);
        model.addAttribute("districtUsers", district);
        model.addAttribute("financeUsers", finance);
        model.addAttribute("disbursementUsers", disbursement);
        model.addAttribute("beneficiaryUsers", beneficiaries);
    }

    private boolean hasRole(User user, String role) {
        return user.getRoles() != null && user.getRoles().contains(role);
    }

    private String readableError(RuntimeException ex) {
        return ex.getMessage() == null || ex.getMessage().isBlank()
                ? "The requested administrator operation could not be completed."
                : ex.getMessage();
    }
    // Keep security events in the database; only filter their presentation to admins.
    private static boolean visibleInAdminAudit(com.platform.audit.AuditLog log) {
        String role = log.getActorRole() == null ? "" : log.getActorRole().toUpperCase(java.util.Locale.ROOT);
        return !role.contains("BENEFICIARY") && !role.contains("CITIZEN");
    }

    private static boolean importantAdminNotification(com.platform.audit.AuditLog log) {
        String action = log.getAction() == null ? "" : log.getAction().toUpperCase(java.util.Locale.ROOT);
        String entity = log.getEntityType() == null ? "" : log.getEntityType().toUpperCase(java.util.Locale.ROOT);
        if (action.contains("OTP") || action.contains("LOGIN") || action.contains("LOGOUT")
                || action.contains("SIGN_IN") || action.contains("SIGN_OUT")) return false;
        return "FUND_REVIEW".equals(entity) || action.startsWith("OFFICER_")
                || action.startsWith("SCHEME_") || action.contains("SUSPENDED")
                || action.contains("REACTIVATED");
    }

}
