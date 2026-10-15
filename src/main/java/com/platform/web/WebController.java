package com.platform.web;

import com.platform.security.OtpService;
import com.platform.security.OtpAuthenticationSuccessHandler;
import com.platform.security.RegistrationOtpService;
import java.util.ArrayList;
import com.platform.application.*;
import com.platform.audit.AuditLogService;
import com.platform.disbursement.DisbursementPlan;
import com.platform.disbursement.DisbursementMilestone;
import com.platform.disbursement.DisbursementPlanRepository;
import com.platform.scheme.Scheme;
import com.platform.scheme.SchemeService;
import com.platform.user.*;
import com.platform.util.DocumentKeyUtil;
import com.platform.util.SimplePdfUtil;
import com.platform.security.JurisdictionService;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.util.MultiValueMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.stream.Collectors;

@Controller
public class WebController {
    @Autowired private com.platform.config.CloudinaryStorageService cloudinaryStorage;
    // District Officer's staged optional-document selections.
    // Selecting documents does NOT move the application; Send to Applicant does.
    private final Map<Long, List<String>> districtOptionalRequests =
            new java.util.concurrent.ConcurrentHashMap<>();



    @Autowired private UserService userService;
    @Autowired private UserRepository userRepository;
    @Autowired private BeneficiaryRepository beneficiaryRepository;
    @Autowired private SchemeService schemeService;
    @Autowired private SubsidyApplicationRepository subsidyApplicationRepository;
    @Autowired private EligibilityService eligibilityService;
    @Autowired private AuditLogService auditLogService;
    @Autowired private ApplicationDocumentRepository applicationDocumentRepository;
    @Autowired private ApplicationRemarkRepository applicationRemarkRepository;
    @Autowired private DisbursementPlanRepository disbursementPlanRepository;
    @Autowired private JurisdictionService jurisdictionService;
    @Autowired private RegistrationOtpService registrationOtpService;
    @Autowired private OtpService otpService;

    // Notification receipt writes belong to the authenticated user only.
    @Autowired private com.platform.disbursement.ComplianceReminderRepository complianceReminderRepository;

    // =========================================================
    // PUBLIC HOME
    // =========================================================
    @GetMapping("/")
    public String showPublicPortal(Model model) {
        model.addAttribute("schemes", schemeService.getActiveSchemes());
        return "index";
    }

    // =========================================================
    // REGISTRATION
    // =========================================================
    @GetMapping("/register")
    public String showRegistrationPage(@RequestParam(value = "selectedScheme", required = false) String selectedScheme, Model model) {
        UserRegistrationDto dto = new UserRegistrationDto();
        if (selectedScheme != null && !selectedScheme.isBlank()) dto.setSchemeName(selectedScheme);
        model.addAttribute("user", dto);
        model.addAttribute("schemes", schemeService.getActiveSchemes());
        jakarta.servlet.http.HttpSession session =
                ((org.springframework.web.context.request.ServletRequestAttributes)
                        org.springframework.web.context.request.RequestContextHolder.currentRequestAttributes())
                        .getRequest().getSession(false);
        if (session != null) {
            String pendingEmail = (String) session.getAttribute(RegistrationOtpService.PENDING_EMAIL);
            if (pendingEmail != null) {
                model.addAttribute("otpStage", true);
                model.addAttribute("maskedEmail", maskEmail(pendingEmail));
                if (registrationOtpService.isDevDisplay()) {
                    model.addAttribute("devOtp", session.getAttribute(RegistrationOtpService.DEV_OTP));
                }
            }
        }
        return "register";
    }

    @PostMapping("/register")
    public String registerUserForm(@Valid @ModelAttribute("user") UserRegistrationDto dto,
                                   BindingResult result, Model model) {
        if (result.hasErrors()) {
            model.addAttribute("schemes", schemeService.getActiveSchemes());
            return "register";
        }
        try {
            User entityUser = new User();
            entityUser.setEmail(dto.getEmail());
            entityUser.setPassword(dto.getPassword());

            Beneficiary beneficiary = new Beneficiary();
            beneficiary.setFullName(dto.getFullName());
            beneficiary.setGovernmentId(dto.getGovernmentId().trim().toUpperCase());
            beneficiary.setContactNumber(dto.getContactNumber());
            beneficiary.setAge(dto.getAge());
            beneficiary.setState(dto.getState());
            beneficiary.setDistrict(dto.getDistrict());
            beneficiary.setGender(dto.getGender());
            beneficiary.setDisabilityStatus(dto.getDisabilityStatus());
            beneficiary.setResidenceType(dto.getResidenceType());
            beneficiary.setAddress(dto.getAddress());
            beneficiary.setSchemeName(dto.getSchemeName());
            beneficiary.setIncome(dto.getIncome());
            beneficiary.setOccupation(dto.getOccupation());
            beneficiary.setUser(entityUser);
            entityUser.setBeneficiary(beneficiary);

            // Citizen account exists only as a disabled pending record until email OTP succeeds.
            entityUser.setAccountStatus("PENDING_EMAIL_VERIFICATION");
            User savedUser = userService.registerUser(entityUser);

            String developmentOtp = registrationOtpService.issueOtp(savedUser.getEmail(), false);
            jakarta.servlet.http.HttpSession session =
                    ((org.springframework.web.context.request.ServletRequestAttributes)
                            org.springframework.web.context.request.RequestContextHolder.currentRequestAttributes())
                            .getRequest().getSession(true);

            session.setAttribute(RegistrationOtpService.PENDING_EMAIL, savedUser.getEmail());
            if (developmentOtp != null) {
                session.setAttribute(RegistrationOtpService.DEV_OTP, developmentOtp);
            } else {
                session.removeAttribute(RegistrationOtpService.DEV_OTP);
            }

            return "redirect:/register?otp";
        } catch (UserRegistrationException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("schemes", schemeService.getActiveSchemes());
            return "register";
        }
    }

    // =========================================================
    // LOGIN
    // =========================================================
    @GetMapping("/login")
    public String showLoginPage(jakarta.servlet.http.HttpSession session, Model model) {
        String pendingEmail = (String) session.getAttribute(OtpAuthenticationSuccessHandler.PENDING_EMAIL);
        if (pendingEmail != null) {
            model.addAttribute("otpStage", true);
            model.addAttribute("maskedEmail", maskEmail(pendingEmail));
            if (otpService.isDevDisplay()) {
                model.addAttribute("devOtp", session.getAttribute(OtpAuthenticationSuccessHandler.DEV_OTP));
            }
        }
        return "login";
    }

    // =========================================================
    // DASHBOARD ROUTER
    // =========================================================
    @GetMapping("/dashboard")
    public String routeUserDashboard(@AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) return "redirect:/login";

        boolean isAdmin = userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ADMIN"));
        if (isAdmin) return "redirect:/admin/dashboard";

        boolean isOfficer = userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().contains("FIELD_OFFICER")
                        || a.getAuthority().contains("DISTRICT_OFFICER")
                        || a.getAuthority().contains("FINANCE_APPROVER")
                        || a.getAuthority().contains("DISBURSEMENT_OFFICER"));
        return isOfficer ? "redirect:/dashboard/officer" : "redirect:/dashboard/citizen";
    }

    // =========================================================
    // OFFICER DASHBOARD
    // =========================================================
    @GetMapping("/dashboard/officer")
    public String showOfficerDashboard(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        if (userDetails == null) return "redirect:/login";
        User user = userRepository.findByEmail(userDetails.getUsername()).orElse(null);
        if (user == null) return "redirect:/login?error=SessionExpired";

        model.addAttribute("user", user);

        boolean isField      = userDetails.getAuthorities().stream().anyMatch(a -> a.getAuthority().contains("FIELD_OFFICER"));
        boolean isDistrict   = userDetails.getAuthorities().stream().anyMatch(a -> a.getAuthority().contains("DISTRICT_OFFICER"));
        boolean isFinance    = userDetails.getAuthorities().stream().anyMatch(a -> a.getAuthority().contains("FINANCE_APPROVER"));
        // Finance Officer owns staged disbursement. Keep the legacy role available for old accounts.
        boolean isDisburse   = isFinance || userDetails.getAuthorities().stream().anyMatch(a -> a.getAuthority().contains("DISBURSEMENT_OFFICER"));

        model.addAttribute("isFieldOfficer",      isField);
        model.addAttribute("isDistrictOfficer",   isDistrict);
        model.addAttribute("isFinanceApprover",   isFinance);
        model.addAttribute("isDisbursementOfficer", isDisburse);

        // FIELD OFFICER: Applications awaiting field verification
        List<SubsidyApplication> fieldQueue = isField
                ? subsidyApplicationRepository.findByApplicationStatus(ApplicationStatus.FIELD_VERIFICATION)
                : Collections.emptyList();
        List<SubsidyApplication> resubmittedQueue = isField
                ? subsidyApplicationRepository.findByApplicationStatus(ApplicationStatus.RESUBMITTED)
                : Collections.emptyList();

        // DISTRICT OFFICER: Applications awaiting district review
        List<SubsidyApplication> districtQueue = isDistrict
                ? subsidyApplicationRepository.findByApplicationStatus(ApplicationStatus.DISTRICT_REVIEW)
                : Collections.emptyList();

        // FINANCE APPROVER: Applications awaiting finance approval
        List<SubsidyApplication> financeQueue = isFinance
                ? subsidyApplicationRepository.findByApplicationStatus(ApplicationStatus.FINANCE_PENDING)
                : Collections.emptyList();

        // DISBURSEMENT OFFICER: Applications ready for fund disbursement
        List<SubsidyApplication> disbursalQueue = isDisburse
                ? subsidyApplicationRepository.findByApplicationStatus(ApplicationStatus.FUND_DISBURSEMENT)
                : Collections.emptyList();

        // Milestone 4A.2: server-backed jurisdiction filtering.
        // Legacy seeded officers without assignments retain the old all-India development view.
        fieldQueue = jurisdictionService.filter(userDetails, fieldQueue);
        resubmittedQueue = jurisdictionService.filter(userDetails, resubmittedQueue);
        districtQueue = jurisdictionService.filter(userDetails, districtQueue);
        financeQueue = jurisdictionService.filter(userDetails, financeQueue);
        disbursalQueue = jurisdictionService.filter(userDetails, disbursalQueue);

        fieldQueue.forEach(this::enrichApplicationDocumentState);
        resubmittedQueue.forEach(this::enrichApplicationDocumentState);
        districtQueue.forEach(this::enrichApplicationDocumentState);
        financeQueue.forEach(this::enrichApplicationDocumentState);
        disbursalQueue.forEach(this::enrichApplicationDocumentState);

        model.addAttribute("fieldQueue", fieldQueue);
        model.addAttribute("resubmittedQueue", resubmittedQueue);
        model.addAttribute("districtQueue", districtQueue);
        model.addAttribute("financeQueue", financeQueue);
        model.addAttribute("disbursalQueue", disbursalQueue);

        // Finance reminder view: expose the current staged plan for each active
        // disbursement application so due-soon / overdue reminders are visible
        // to the officer as well as the applicant.
        Map<Long, DisbursementPlan> officerDisbursementPlans = new LinkedHashMap<>();
        if (isDisburse) {
            for (SubsidyApplication application : disbursalQueue) {
                disbursementPlanRepository.findByApplicationId(application.getId())
                        .ifPresent(plan -> officerDisbursementPlans.put(application.getId(), plan));
            }
        }
        model.addAttribute("officerDisbursementPlans", officerDisbursementPlans);
        List<DisbursementMilestone> urgentComplianceMilestones = new ArrayList<>();
        if (isDisburse) for (DisbursementPlan p : officerDisbursementPlans.values()) for (DisbursementMilestone m : p.getMilestones())
            if (m.getStatus()==com.platform.disbursement.MilestoneStatus.COMPLIANCE_SUBMITTED && m.getComplianceOriginalFilename()!=null) urgentComplianceMilestones.add(m);
        model.addAttribute("urgentComplianceMilestones", urgentComplianceMilestones);

        // Role-specific history: once an officer has completed their stage,
        // the application leaves that officer's active queue and appears here.
        List<SubsidyApplication> completedHistory = subsidyApplicationRepository.findAllByOrderByApplicationDateDesc()
                .stream()
                .filter(app -> {
                    if (isField) {
                        return app.getFieldInspectionRemarks() != null
                                && app.getApplicationStatus() != ApplicationStatus.FIELD_VERIFICATION
                                && app.getApplicationStatus() != ApplicationStatus.RESUBMITTED;
                    }
                    if (isDistrict) {
                        return app.getDistrictReviewRemarks() != null
                                && app.getApplicationStatus() != ApplicationStatus.DISTRICT_REVIEW;
                    }
                    if (isDisburse) {
                        return app.getApplicationStatus() == ApplicationStatus.APPROVED
                                || app.getApplicationStatus() == ApplicationStatus.REJECTED;
                    }
                    return false;
                })
                .toList();
        completedHistory.forEach(this::enrichApplicationDocumentState);
        model.addAttribute("completedHistory", completedHistory);

        List<Map<String,String>> officerEvents = new ArrayList<>();
        if (isField) {
            for (SubsidyApplication a : fieldQueue) addPortalEvent(officerEvents, "FIELD:"+a.getId()+":FIELD_VERIFICATION", "Field verification assigned", a.getId(), "/dashboard/officer#pending");
            for (SubsidyApplication a : resubmittedQueue) addPortalEvent(officerEvents, "FIELD:"+a.getId()+":RESUBMITTED", "Documents resubmitted for review", a.getId(), "/dashboard/officer#pending");
        }
        if (isDistrict) for (SubsidyApplication a : districtQueue)
            addPortalEvent(officerEvents, "DISTRICT:"+a.getId()+":DISTRICT_REVIEW", "District decision pending", a.getId(), "/dashboard/officer#pending");
        if (isFinance) for (SubsidyApplication a : financeQueue)
            addPortalEvent(officerEvents, "FINANCE:"+a.getId()+":FINANCE_PENDING", "Finance approval pending", a.getId(), "/dashboard/officer#pending");
        if (isDisburse) {
            for (SubsidyApplication a : disbursalQueue)
                addPortalEvent(officerEvents, "DISBURSE:"+a.getId()+":FUND_DISBURSEMENT", "Disbursement action pending", a.getId(), "/dashboard/officer#pending");
            // Admin-cleared fund reviews: notify Finance without resuming payments.
            for (DisbursementPlan plan : officerDisbursementPlans.values()) {
                if (!plan.isFundReviewFlagged() && plan.getFundReviewClearedAt() != null) {
                    String key = "CLEARED:" + plan.getId() + ":" +
                            plan.getFundReviewClearedAt().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
                    addPortalEvent(officerEvents, key, "Fund review flag cleared by Admin — review and resume finance if appropriate",
                            plan.getApplication().getId(), "/milestone3/disbursement/" + plan.getApplication().getId());
                }
            }
            for (DisbursementMilestone m : urgentComplianceMilestones)
                addPortalEvent(officerEvents, "COMPLIANCE:"+m.getId()+":SUBMITTED", "Compliance document submitted", m.getPlan().getApplication().getId(), "/dashboard/officer#pending");
            java.time.LocalDateTime now = java.time.LocalDateTime.now();
            for (DisbursementPlan plan : officerDisbursementPlans.values()) {
                for (DisbursementMilestone m : plan.getMilestones()) {
                    if (!m.isComplianceRequested() || m.getComplianceOriginalFilename()!=null || m.getDueDate()==null) continue;
                    java.time.LocalDateTime due = java.time.LocalDateTime.of(m.getDueDate(), m.getDueTime()==null ? java.time.LocalTime.of(10,0) : m.getDueTime());
                    if (!due.isAfter(now.plusHours(24))) {
                        String urgency = due.isBefore(now) ? "Overdue compliance" : "Compliance due within 24 hours";
                        addPortalEvent(officerEvents, "DUE:"+m.getId()+":"+(due.isBefore(now)?"OVERDUE":"SOON"), urgency,
                                plan.getApplication().getId(), "/dashboard/officer#pending");
                    }
                }
            }
        }
        attachPortalEvents(model, user, officerEvents);
        return "dashboard-officer";
    }

    // =========================================================
    // CITIZEN DASHBOARD
    // =========================================================
    @GetMapping("/dashboard/citizen")
    public String showCitizenDashboard(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        if (userDetails == null) return "redirect:/login";
        User user = userRepository.findByEmail(userDetails.getUsername()).orElse(null);
        if (user == null) return "redirect:/login?error=SessionExpired";

        model.addAttribute("user", user);
        List<Scheme> activeSchemes = schemeService.getActiveSchemes();
        model.addAttribute("schemes", activeSchemes);
        List<SubsidyApplication> trackingLedger = subsidyApplicationRepository.findByUser(user);
        trackingLedger.forEach(this::enrichApplicationDocumentState);

        // One citizen may have only one application record per scheme.
        // REJECTED / officer re-verification continues on the SAME application.
        // APPROVED permanently blocks another application. A third rejection blocks
        // resubmission until the cooling period ends.
        Map<Long, String> schemeApplicationState = new LinkedHashMap<>();
        Map<Long, Long> schemeApplicationId = new LinkedHashMap<>();
        for (Scheme scheme : activeSchemes) {
            SubsidyApplication latest = subsidyApplicationRepository
                    .findTopByUserAndSchemeOrderByApplicationDateDesc(user, scheme)
                    .orElse(null);
            if (latest == null) {
                schemeApplicationState.put(scheme.getId(), "AVAILABLE");
                continue;
            }

            schemeApplicationId.put(scheme.getId(), latest.getId());
            if (latest.getApplicationStatus() == ApplicationStatus.APPROVED) {
                schemeApplicationState.put(scheme.getId(), "APPROVED");
            } else if (latest.isCoolingPeriodActive()) {
                schemeApplicationState.put(scheme.getId(), "COOLING");
            } else if (latest.getApplicationStatus() == ApplicationStatus.REJECTED
                    || latest.getApplicationStatus() == ApplicationStatus.APPLICANT_REVERIFICATION
                    || latest.getApplicationStatus() == ApplicationStatus.COOLING_PERIOD) {
                schemeApplicationState.put(scheme.getId(), "RESUBMIT");
            } else {
                schemeApplicationState.put(scheme.getId(), "ACTIVE");
            }
        }
        model.addAttribute("schemeApplicationState", schemeApplicationState);
        model.addAttribute("schemeApplicationId", schemeApplicationId);

        Map<Long, DisbursementPlan> disbursementPlans = new LinkedHashMap<>();
        for (SubsidyApplication application : trackingLedger) {
            disbursementPlanRepository.findByApplicationId(application.getId())
                    .ifPresent(plan -> disbursementPlans.put(application.getId(), plan));
        }

        List<DisbursementMilestone> citizenNotifications = new ArrayList<>();
        for (DisbursementPlan p : disbursementPlans.values()) for (DisbursementMilestone m : p.getMilestones())
            if (m.isComplianceRequested() && m.getStatus()!=com.platform.disbursement.MilestoneStatus.RELEASED && m.getComplianceOriginalFilename()==null) citizenNotifications.add(m);
        model.addAttribute("trackingLedger", trackingLedger);
        model.addAttribute("disbursementPlans", disbursementPlans);
        // Current application-state alerts are scoped to the authenticated citizen's own ledger.
        // These are live status indicators, not persisted/read-unread event notifications.
        List<SubsidyApplication> citizenStatusAlerts = trackingLedger.stream()
                .filter(app -> app.getApplicationStatus() == ApplicationStatus.APPROVED
                        || app.getApplicationStatus() == ApplicationStatus.REJECTED
                        || app.getApplicationStatus() == ApplicationStatus.APPLICANT_REVERIFICATION
                        || app.getApplicationStatus() == ApplicationStatus.COOLING_PERIOD)
                .toList();
        model.addAttribute("citizenStatusAlerts", citizenStatusAlerts);
        model.addAttribute("citizenNotifications", citizenNotifications);
        List<Map<String,String>> citizenEvents = new ArrayList<>();
        for (SubsidyApplication a : citizenStatusAlerts) {
            addPortalEvent(citizenEvents, "STATUS:"+a.getId()+":"+a.getApplicationStatus().name(),
                    "Application " + a.getApplicationStatus().name().replace('_',' ').toLowerCase(java.util.Locale.ROOT),
                    a.getId(), "/dashboard/citizen");
        }
        for (DisbursementMilestone m : citizenNotifications) {
            addPortalEvent(citizenEvents, "REQUEST:"+m.getId(), "Compliance document requested",
                    m.getPlan().getApplication().getId(), "/dashboard/citizen");
            if (m.getLastReminderType()!=null) {
                addPortalEvent(citizenEvents, "REMINDER:"+m.getId()+":"+m.getLastReminderType(),
                        "Compliance " + m.getLastReminderType().replace('_',' ').toLowerCase(java.util.Locale.ROOT),
                        m.getPlan().getApplication().getId(), "/dashboard/citizen");
            }
        }
        attachPortalEvents(model, user, citizenEvents);
        return "dashboard-citizen";
    }

    // Notification keys contain only event IDs, not message content. All keys are
    // generated server-side from this user's authorized applications/queues.
    private void addPortalEvent(List<Map<String,String>> events, String key, String title,
                                Long applicationId, String url) {
        Map<String,String> event = new LinkedHashMap<>();
        event.put("key", key);
        event.put("title", title);
        event.put("detail", "SUB-" + applicationId);
        event.put("url", url);
        events.add(event);
    }

    private void attachPortalEvents(Model model, User user, List<Map<String,String>> events) {
        Set<String> read = user.getReadNotificationKeys();
        for (Map<String,String> event : events) event.put("read", String.valueOf(read.contains(event.get("key"))));
        model.addAttribute("portalEvents", events);
        model.addAttribute("portalUnreadCount", events.stream().filter(e -> !"true".equals(e.get("read"))).count());
    }

    @PostMapping("/notifications/read")
    public String markPortalNotificationRead(@AuthenticationPrincipal UserDetails principal,
                                             @RequestParam("key") String key,
                                             @RequestParam(value="returnTo",defaultValue="/dashboard") String returnTo) {
        if (principal == null) return "redirect:/login";
        User user = userRepository.findByEmail(principal.getUsername()).orElse(null);
        if (user == null) return "redirect:/login";
        // A receipt can be written only for an event the account can currently see.
        // Build the same authorized queue on the appropriate dashboard and check it there.
        // Restrict key syntax to the known application/reminder event format.
        if (key.length() > 160 || !key.matches("[A-Z_]+:[0-9]+(?::[A-Z_0-9]+)?")) {
            return "redirect:/dashboard";
        }
        if (!isPortalEventVisible(principal, user, key)) return "redirect:/dashboard";
        user.getReadNotificationKeys().add(key);
        userRepository.save(user);
        return "redirect:" + ("/dashboard/officer".equals(returnTo) ? "/dashboard/officer" : "/dashboard/citizen");
    }

    private boolean isPortalEventVisible(UserDetails principal, User user, String key) {
        String[] parts = key.split(":");
        if (parts.length < 2) return false;
        long id;
        try { id = Long.parseLong(parts[1]); } catch (NumberFormatException e) { return false; }
        String type = parts[0];
        if ("CLEARED".equals(type)) {
            boolean finance = principal.getAuthorities().stream().anyMatch(x ->
                    x.getAuthority().contains("FINANCE_APPROVER") || x.getAuthority().contains("DISBURSEMENT_OFFICER"));
            if (!finance || parts.length != 3) return false;
            return disbursementPlanRepository.findById(id).map(p ->
                    !p.isFundReviewFlagged() && p.getFundReviewClearedAt() != null
                            && parts[2].equals(String.valueOf(p.getFundReviewClearedAt()
                            .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()))
                            && jurisdictionService.canAccess(principal, p.getApplication())).orElse(false);
        }
        if ("STATUS".equals(type)) {
            return subsidyApplicationRepository.findById(id).map(a -> java.util.Objects.equals(a.getUser().getId(), user.getId())
                    && parts.length == 3 && a.getApplicationStatus().name().equals(parts[2])).orElse(false);
        }
        if ("REQUEST".equals(type) || "REMINDER".equals(type) || "DUE".equals(type) || "COMPLIANCE".equals(type)) {
            return disbursementPlanRepository.findAll().stream()
                    .flatMap(p -> p.getMilestones().stream())
                    .filter(m -> java.util.Objects.equals(m.getId(), id))
                    .anyMatch(m -> {
                        SubsidyApplication a = m.getPlan().getApplication();
                        if ("REQUEST".equals(type)) return java.util.Objects.equals(a.getUser().getId(), user.getId())
                                && m.isComplianceRequested() && m.getComplianceOriginalFilename()==null;
                        if ("REMINDER".equals(type)) return java.util.Objects.equals(a.getUser().getId(), user.getId())
                                && parts.length == 3 && parts[2].equals(m.getLastReminderType())
                                && m.isComplianceRequested() && m.getComplianceOriginalFilename()==null;
                        boolean finance = principal.getAuthorities().stream().anyMatch(x ->
                                x.getAuthority().contains("FINANCE_APPROVER") || x.getAuthority().contains("DISBURSEMENT_OFFICER"));
                        if (!finance || !jurisdictionService.canAccess(principal,a)) return false;
                        if ("COMPLIANCE".equals(type)) return m.getComplianceOriginalFilename()!=null;
                        return m.isComplianceRequested() && m.getComplianceOriginalFilename()==null;
                    });
        }
        if (!Set.of("FIELD","DISTRICT","FINANCE","DISBURSE").contains(type)) return false;
        return subsidyApplicationRepository.findById(id).map(a -> {
            if (!jurisdictionService.canAccess(principal,a) || parts.length != 3
                    || !a.getApplicationStatus().name().equals(parts[2])) return false;
            String requiredRole = switch (type) {
                case "FIELD" -> "FIELD_OFFICER";
                case "DISTRICT" -> "DISTRICT_OFFICER";
                case "FINANCE" -> "FINANCE_APPROVER";
                default -> "DISBURSEMENT_OFFICER";
            };
            return principal.getAuthorities().stream().anyMatch(x -> x.getAuthority().contains(requiredRole)
                    || ("DISBURSE".equals(type) && x.getAuthority().contains("FINANCE_APPROVER")));
        }).orElse(false);
    }

    // =========================================================
    // EDIT PROFILE
    // =========================================================
    @PostMapping("/profile/update")
    public String updateCitizenProfile(@AuthenticationPrincipal UserDetails userDetails,
                                       @RequestParam String fullName, @RequestParam String contactNumber,
                                       @RequestParam String address,  @RequestParam String state,
                                       @RequestParam(required = false) String district,
                                       @RequestParam String occupation, @RequestParam double income,
                                       @RequestParam String gender,
                                       @RequestParam String disabilityStatus, @RequestParam String residenceType) {
        if (userDetails == null) return "redirect:/login";
        User user = userRepository.findByEmail(userDetails.getUsername()).orElse(null);
        if (user == null) return "redirect:/login";
        Beneficiary b = user.getBeneficiary();
        if (b != null) {
            b.setFullName(fullName); b.setContactNumber(contactNumber);
            b.setAddress(address);   b.setState(state);
            if (district != null && !district.isBlank()) b.setDistrict(district.trim());
            b.setOccupation(occupation); b.setIncome(income);
            b.setGender(gender);
            b.setDisabilityStatus(disabilityStatus); b.setResidenceType(residenceType);
            userRepository.save(user);
        }
        return "redirect:/dashboard/citizen?updated=true";
    }

    /**
     * Returns the latest application for this citizen and scheme.
     * A scheme is not allowed to create a second application record:
     * active/approved applications are blocked, while rejected/re-verification
     * applications continue through the existing resubmission flow.
     */
    private SubsidyApplication latestApplicationFor(User user, Scheme scheme) {
        return subsidyApplicationRepository
                .findTopByUserAndSchemeOrderByApplicationDateDesc(user, scheme)
                .orElse(null);
    }

    // =========================================================
    // ELIGIBILITY / SCHEME SELECTION
    // =========================================================
    @GetMapping("/eligibility")
    public String showEligibilityPage(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        if (userDetails == null) return "redirect:/login";
        model.addAttribute("schemes", schemeService.getActiveSchemes());
        return "eligibility";
    }

    @GetMapping("/eligibility/{schemeId}")
    public String showEligibilityCheckForm(@PathVariable Long schemeId,
                                           @AuthenticationPrincipal UserDetails userDetails,
                                           Model model) {
        if (userDetails == null) return "redirect:/login";

        Scheme scheme = schemeService.getSchemeById(schemeId);
        User user = userRepository.findByEmail(userDetails.getUsername()).orElse(null);
        if (user == null) return "redirect:/login?error=SessionExpired";

        SubsidyApplication latest = latestApplicationFor(user, scheme);
        if (latest != null) {
            if (latest.isCoolingPeriodActive()) {
                model.addAttribute("coolingEndDate", latest.getCoolingPeriodEndDate().toLocalDate());
                model.addAttribute("scheme", scheme);
                return "cooling-period";
            }
            if (latest.getApplicationStatus() == ApplicationStatus.REJECTED
                    || latest.getApplicationStatus() == ApplicationStatus.APPLICANT_REVERIFICATION
                    || latest.getApplicationStatus() == ApplicationStatus.COOLING_PERIOD) {
                // REJECTED uses the same application for attempts 1/3, 2/3 and,
                // after cooling expires, starts a fresh rejection cycle on that same ID.
                return "redirect:/application/" + latest.getId() + "/resubmit";
            }
            if (latest.getApplicationStatus() == ApplicationStatus.APPROVED) {
                return "redirect:/dashboard/citizen?schemeAlreadyApproved=true";
            }
            // Any other status is an application already moving through the workflow.
            return "redirect:/dashboard/citizen?schemeApplicationActive=true";
        }

        Beneficiary target = new Beneficiary();
        if (user.getBeneficiary() != null) {
            copyBeneficiary(user.getBeneficiary(), target, user, scheme.getSchemeName());
        }

        LinkedHashMap<String, String> requiredDocuments = DocumentKeyUtil.parse(scheme.getRequiredDocuments());
        LinkedHashMap<String, String> optionalDocuments = DocumentKeyUtil.parse(scheme.getOptionalDocuments());

        model.addAttribute("user", user);
        model.addAttribute("scheme", scheme);
        model.addAttribute("beneficiary", target);
        model.addAttribute("requiredDocuments", requiredDocuments);
        model.addAttribute("optionalDocuments", optionalDocuments);
        return "eligibility-form";
    }

    /**
     * Same server-side scoring engine as the final submit. The browser sends all
     * current profile fields and all currently selected files on every change.
     */
    @PostMapping(value = "/eligibility/{schemeId}/live-score", consumes = "multipart/form-data", produces = "application/json")
    @ResponseBody
    public Map<String, Object> calculateLiveScore(@PathVariable Long schemeId,
                                                  @ModelAttribute("beneficiary") Beneficiary beneficiary,
                                                  @RequestParam Map<String, MultipartFile> files) {
        Scheme scheme = schemeService.getSchemeById(schemeId);
        Set<String> uploadedKeys = uploadedDocumentKeys(files);
        SchemeEligibilityScore score = eligibilityService.calculateLiveScore(beneficiary, scheme, uploadedKeys);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("score", score.getTotalScore());
        response.put("eligible", score.isEligible());
        response.put("profilePts", score.getProfilePts());
        response.put("mandatoryDocumentPts", score.getMandatoryDocumentPts());
        response.put("optionalDocumentPts", score.getOptionalDocumentPts());
        response.put("uploadedMandatoryDocuments", score.getUploadedMandatoryDocuments());
        response.put("totalMandatoryDocuments", score.getTotalMandatoryDocuments());
        response.put("uploadedOptionalDocuments", score.getUploadedOptionalDocuments());
        response.put("totalOptionalDocuments", score.getTotalOptionalDocuments());
        response.put("mandatoryDocumentsComplete", score.isMandatoryDocumentsComplete());
        response.put("missingDocuments", score.getMissingRequiredDocuments());
        response.put("message", score.getFailureReasons());
        return response;
    }

    // =========================================================
    // CHECK ELIGIBILITY + SUBMIT APPLICATION
    // =========================================================
    @PostMapping(value = "/eligibility/{schemeId}/check", consumes = "multipart/form-data")
    public String evaluateFormEligibility(@PathVariable Long schemeId,
                                          @ModelAttribute("beneficiary") Beneficiary inputFormDetails,
                                          @RequestParam Map<String, MultipartFile> files,
                                          @AuthenticationPrincipal UserDetails userDetails,
                                          Model model) {
        if (userDetails == null) return "redirect:/login";

        User registeredUser = userRepository.findByEmail(userDetails.getUsername()).orElse(null);
        if (registeredUser == null) return "redirect:/login?error=SessionExpired";

        Scheme scheme = schemeService.getSchemeById(schemeId);

        // A third rejection starts the one-month cooling period for this scheme.
        // Do not create another application while that cooling period is active.
        SubsidyApplication latest = latestApplicationFor(registeredUser, scheme);
        if (latest != null) {
            if (latest.isCoolingPeriodActive()) {
                model.addAttribute("coolingEndDate", latest.getCoolingPeriodEndDate().toLocalDate());
                model.addAttribute("scheme", scheme);
                return "cooling-period";
            }
            if (latest.getApplicationStatus() == ApplicationStatus.REJECTED
                    || latest.getApplicationStatus() == ApplicationStatus.APPLICANT_REVERIFICATION
                    || latest.getApplicationStatus() == ApplicationStatus.COOLING_PERIOD) {
                return "redirect:/application/" + latest.getId() + "/resubmit";
            }
            if (latest.getApplicationStatus() == ApplicationStatus.APPROVED) {
                return "redirect:/dashboard/citizen?schemeAlreadyApproved=true";
            }
            return "redirect:/dashboard/citizen?schemeApplicationActive=true";
        }

        Set<String> uploadedKeys = uploadedDocumentKeys(files);
        SchemeEligibilityScore scoreResult = eligibilityService.calculateLiveScore(inputFormDetails, scheme, uploadedKeys);

        // Do not silently create a rejected application because a dynamic document was
        // omitted. Return to the same form with an exact list of missing documents.
        if (!scoreResult.isMandatoryDocumentsComplete() || !scoreResult.isEligible()) {
            model.addAttribute("user", registeredUser);
            model.addAttribute("scheme", scheme);
            model.addAttribute("beneficiary", inputFormDetails);
            model.addAttribute("requiredDocuments", DocumentKeyUtil.parse(scheme.getRequiredDocuments()));
            model.addAttribute("optionalDocuments", DocumentKeyUtil.parse(scheme.getOptionalDocuments()));
            model.addAttribute("scoreResult", scoreResult);
            model.addAttribute("error", scoreResult.getFailureReasons());
            return "eligibility-form";
        }

        SubsidyApplication application = new SubsidyApplication();
        application.setUser(registeredUser);
        application.setScheme(scheme);
        application.setEligibilityStatus(true);
        application.setApplicationDate(LocalDateTime.now());
        application.setEligibilityScore(scoreResult.getTotalScore());
        application.setSanctionAmount(0.0); // Not sanctioned until Finance Officer approves an amount.
        application.setApplicationStatus(ApplicationStatus.FIELD_VERIFICATION);

        // Save the application first so the generated ID can be used as the upload directory.
        SubsidyApplication savedApplication = subsidyApplicationRepository.save(application);
        saveUploadedDocuments(savedApplication, scheme, files);

        auditLogService.record("SUBSIDY_APPLICATION", savedApplication.getId(),
                "APPLICATION_SUBMITTED", null,
                savedApplication.getApplicationStatus().name(),
                "Citizen submitted application for scheme: " + scheme.getSchemeName()
                        + " | eligibilityScore=" + savedApplication.getEligibilityScore());

        model.addAttribute("application", savedApplication);
        model.addAttribute("applicationDocuments", applicationDocumentRepository.findByApplicationIdOrderByOptionalDocumentAscDocumentNameAsc(savedApplication.getId()));
        model.addAttribute("scheme", scheme);
        model.addAttribute("eligible", true);
        model.addAttribute("score", scoreResult.getTotalScore());
        model.addAttribute("scoreResult", scoreResult);
        model.addAttribute("coolingDays", EligibilityService.COOLING_PERIOD_DAYS);
        return "application-success";
    }

    // =========================================================
    // CITIZEN RESUBMISSION
    // =========================================================
    @GetMapping("/application/{appId}/resubmit")
    public String showResubmitForm(@PathVariable Long appId,
                                   @AuthenticationPrincipal UserDetails userDetails,
                                   Model model) {
        if (userDetails == null) return "redirect:/login";
        User currentUser = userRepository.findByEmail(userDetails.getUsername()).orElse(null);
        if (currentUser == null) return "redirect:/login?error=SessionExpired";
        SubsidyApplication app = subsidyApplicationRepository.findDetailedById(appId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found: " + appId));
        if (app.getUser() == null || !app.getUser().getId().equals(currentUser.getId())) {
            return "redirect:/dashboard/citizen?error=Unauthorized";
        }
        if (app.isCoolingPeriodActive()) {
            model.addAttribute("coolingEndDate", app.getCoolingPeriodEndDate().toLocalDate());
            return "cooling-period";
        }
        // If status is COOLING_PERIOD and isCoolingPeriodActive() is false,
        // cooling has expired. Keep this SAME application ID and allow resubmission;
        // markApplicantResubmitted() will reset the rejection cycle to 0/3.
        if (app.getApplicationStatus() != ApplicationStatus.APPLICANT_REVERIFICATION
                && app.getApplicationStatus() != ApplicationStatus.COOLING_PERIOD
                && app.getApplicationStatus() != ApplicationStatus.REJECTED) {
            model.addAttribute("info", "This application is not currently waiting for applicant re-verification.");
        }
        enrichApplicationDocumentState(app);
        model.addAttribute("application", app);
        model.addAttribute("scheme", app.getScheme());
        model.addAttribute("requiredDocuments", DocumentKeyUtil.parse(app.getScheme().getRequiredDocuments()));
        model.addAttribute("optionalDocuments", DocumentKeyUtil.parse(app.getScheme().getOptionalDocuments()));
        List<ApplicationDocument> uploadedDocuments = applicationDocumentRepository
                .findByApplicationIdOrderByOptionalDocumentAscDocumentNameAsc(appId);
        Set<String> uploadedDocumentKeys = new HashSet<>();
        Map<String, String> documentStatusByKey = new LinkedHashMap<>();
        Set<String> resubmitRequiredKeys = new HashSet<>();
        for (ApplicationDocument doc : uploadedDocuments) {
            uploadedDocumentKeys.add(doc.getDocumentKey());
            String status = doc.getVerificationStatus() == null ? "PENDING" : doc.getVerificationStatus();
            documentStatusByKey.put(doc.getDocumentKey(), status);
            if ("RESUBMIT_REQUIRED".equalsIgnoreCase(status)) {
                resubmitRequiredKeys.add(doc.getDocumentKey());
            }
        }
        List<ApplicationRemark> applicationRemarks =
                applicationRemarkRepository.findByApplicationIdOrderByCreatedAtAsc(appId);

        // Add missing optional documents explicitly requested by the District Officer.
        for (int i = applicationRemarks.size() - 1; i >= 0; i--) {
            ApplicationRemark remark = applicationRemarks.get(i);
            if (("REQUEST_OPTIONAL_DOCUMENTS".equalsIgnoreCase(remark.getAction())
                    || "SEND_BACK_APPLICANT".equalsIgnoreCase(remark.getAction())
                    || "SEND_TO_APPLICANT".equalsIgnoreCase(remark.getAction()))
                    && remark.getRemarks() != null) {
                String text = remark.getRemarks();
                int markerStart = text.indexOf("[OPTIONAL_KEYS:");
                if (markerStart >= 0) {
                    int markerEnd = text.indexOf(']', markerStart);
                    if (markerEnd > markerStart) {
                        String keys = text.substring(markerStart + "[OPTIONAL_KEYS:".length(), markerEnd);
                        for (String key : keys.split(",")) {
                            String cleanKey = key.trim();
                            if (!cleanKey.isEmpty() && !uploadedDocumentKeys.contains(cleanKey)) {
                                resubmitRequiredKeys.add(cleanKey);
                            }
                        }
                    }
                }
                break;
            }
        }

        model.addAttribute("uploadedDocuments", uploadedDocuments);
        model.addAttribute("uploadedDocumentKeys", uploadedDocumentKeys);
        model.addAttribute("documentStatusByKey", documentStatusByKey);
        model.addAttribute("resubmitRequiredKeys", resubmitRequiredKeys);
        model.addAttribute("applicationRemarks", applicationRemarks);
        return "resubmit-form";
    }

    /*
     * Compatibility handlers for older Thymeleaf pages that accidentally rendered
     * /application//resubmit. These recover the applicant's pending application
     * instead of allowing Spring to return HTTP 400 for the missing path variable.
     */
    @GetMapping("/application//resubmit")
    public String showMalformedResubmitForm(@AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) return "redirect:/login";

        User currentUser = userRepository.findByEmail(userDetails.getUsername()).orElse(null);
        if (currentUser == null) return "redirect:/login?error=SessionExpired";

        List<SubsidyApplication> applications = subsidyApplicationRepository.findByUser(currentUser);
        SubsidyApplication pending = applications.stream()
                .filter(a -> a.getApplicationStatus() == ApplicationStatus.APPLICANT_REVERIFICATION
                        || a.getApplicationStatus() == ApplicationStatus.REJECTED
                        || (a.getApplicationStatus() == ApplicationStatus.COOLING_PERIOD && !a.isCoolingPeriodActive()))
                .findFirst()
                .orElse(null);

        if (pending == null) {
            return "redirect:/dashboard/citizen?error=NoApplicationAvailableForResubmission";
        }

        return "redirect:/application/" + pending.getId() + "/resubmit";
    }

    @PostMapping(value = "/application//resubmit", consumes = "multipart/form-data")
    public String processMalformedResubmission(
            @RequestParam Map<String, MultipartFile> files,
            @AuthenticationPrincipal UserDetails userDetails,
            RedirectAttributes redirectAttributes) {

        if (userDetails == null) return "redirect:/login";

        User currentUser = userRepository.findByEmail(userDetails.getUsername()).orElse(null);
        if (currentUser == null) return "redirect:/login?error=SessionExpired";

        List<SubsidyApplication> applications = subsidyApplicationRepository.findByUser(currentUser);
        SubsidyApplication pending = applications.stream()
                .filter(a -> a.getApplicationStatus() == ApplicationStatus.APPLICANT_REVERIFICATION
                        || a.getApplicationStatus() == ApplicationStatus.REJECTED
                        || (a.getApplicationStatus() == ApplicationStatus.COOLING_PERIOD && !a.isCoolingPeriodActive()))
                .findFirst()
                .orElse(null);

        if (pending == null) {
            redirectAttributes.addFlashAttribute(
                    "error", "No application is currently available for resubmission.");
            return "redirect:/dashboard/citizen";
        }

        // Reuse the normal resubmission flow with the recovered application ID.
        Long appId = pending.getId();
        SubsidyApplication app = subsidyApplicationRepository.findDetailedById(appId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found: " + appId));

        if (app.getUser() == null || !app.getUser().getId().equals(currentUser.getId())) {
            redirectAttributes.addFlashAttribute("error", "Unauthorized application.");
            return "redirect:/dashboard/citizen";
        }

        if (app.isCoolingPeriodActive()) {
            redirectAttributes.addFlashAttribute("error", "Cooling period is still active.");
            return "redirect:/dashboard/citizen";
        }
        // Expired COOLING_PERIOD is allowed to continue on this same application.

        saveUploadedDocuments(app, app.getScheme(), files);

        List<ApplicationDocument> currentDocs =
                applicationDocumentRepository.findByApplicationIdOrderByOptionalDocumentAscDocumentNameAsc(appId);
        Set<String> uploadedKeys = new HashSet<>();
        for (ApplicationDocument doc : currentDocs) {
            uploadedKeys.add(doc.getDocumentKey());
        }

        Beneficiary beneficiary = app.getUser().getBeneficiary();
        SchemeEligibilityScore newScore =
                eligibilityService.calculateLiveScore(beneficiary, app.getScheme(), uploadedKeys);

        app.setEligibilityScore(newScore.getTotalScore());
        app.setEligibilityStatus(newScore.isEligible());

        if (newScore.isEligible()) {
            eligibilityService.markApplicantResubmitted(app);
        } else {
            app.setApplicationStatus(ApplicationStatus.APPLICANT_REVERIFICATION);
            app.setAwaitingResubmission(true);
            subsidyApplicationRepository.save(app);
        }

        redirectAttributes.addFlashAttribute(
                "success",
                newScore.isEligible()
                        ? "Application resubmitted successfully and sent to the Field Officer. Updated score: "
                          + newScore.getTotalScore() + "/100."
                        : "The application still needs the mandatory eligibility requirements: "
                          + String.join(", ", newScore.getMissingRequiredDocuments()));

        return "redirect:/dashboard/citizen";
    }

    @PostMapping(value = {"/application/{appId}/resubmit", "/application/resubmit"})
    public String processResubmission(@PathVariable(value = "appId", required = false) Long appId,
                                      @RequestParam(value = "submittedAppId", required = false) Long submittedAppId,
                                      MultipartHttpServletRequest request,
                                      @AuthenticationPrincipal UserDetails userDetails,
                                      RedirectAttributes redirectAttributes) {
        Map<String, MultipartFile> files = request.getFileMap();
        if (userDetails == null) return "redirect:/login";
        User currentUser = userRepository.findByEmail(userDetails.getUsername()).orElse(null);
        if (currentUser == null) return "redirect:/login?error=SessionExpired";

        Long actualAppId = appId != null ? appId : submittedAppId;

        if (actualAppId == null) {
            actualAppId = subsidyApplicationRepository.findByUser(currentUser).stream()
                    .filter(a -> a.getApplicationStatus() == ApplicationStatus.APPLICANT_REVERIFICATION
                            || a.getApplicationStatus() == ApplicationStatus.REJECTED)
                    .map(SubsidyApplication::getId)
                    .filter(java.util.Objects::nonNull)
                    .findFirst()
                    .orElse(null);
        }

        if (actualAppId == null) {
            redirectAttributes.addFlashAttribute("error",
                    "No application is currently available for resubmission.");
            return "redirect:/dashboard/citizen";
        }

        final Long resolvedAppId = actualAppId;
        SubsidyApplication app = subsidyApplicationRepository.findDetailedById(resolvedAppId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found: " + resolvedAppId));
        if (app.getUser() == null || !app.getUser().getId().equals(currentUser.getId())) {
            return "redirect:/dashboard/citizen?error=Unauthorized";
        }

        if (app.isCoolingPeriodActive()) {
            redirectAttributes.addFlashAttribute("error", "Cooling period is still active.");
            return "redirect:/dashboard/citizen";
        }
        // Expired COOLING_PERIOD is allowed to continue on this same application.
        if (app.getApplicationStatus() != ApplicationStatus.APPLICANT_REVERIFICATION
                && app.getApplicationStatus() != ApplicationStatus.COOLING_PERIOD
                && app.getApplicationStatus() != ApplicationStatus.REJECTED) {
            redirectAttributes.addFlashAttribute("error", "This application is not waiting for applicant re-verification.");
            return "redirect:/dashboard/citizen";
        }

        // Only the documents explicitly marked by an officer as RESUBMIT_REQUIRED
        // must be supplied by the citizen in this resubmission.
        List<ApplicationDocument> beforeUpload = applicationDocumentRepository
                .findByApplicationIdOrderByOptionalDocumentAscDocumentNameAsc(app.getId());
        Set<String> requestedReplacementKeys = new HashSet<>();
        for (ApplicationDocument doc : beforeUpload) {
            if ("RESUBMIT_REQUIRED".equalsIgnoreCase(doc.getVerificationStatus())) {
                requestedReplacementKeys.add(doc.getDocumentKey());
            }
        }

        Set<String> submittedReplacementKeys = uploadedDocumentKeys(files);
        Set<String> missingReplacementKeys = new HashSet<>(requestedReplacementKeys);
        missingReplacementKeys.removeAll(submittedReplacementKeys);
        if (!missingReplacementKeys.isEmpty()) {
            redirectAttributes.addFlashAttribute("error",
                    "Please upload every document requested by the officer before resubmitting.");
            return "redirect:/application/" + app.getId() + "/resubmit";
        }

        // Save replacements. saveUploadedDocuments resets each replaced document to
        // PENDING so the Field Officer must verify the new file again.
        saveUploadedDocuments(app, app.getScheme(), files);

        // Recalculate and store the latest score as required by the workflow. The
        // resubmission itself is not blocked by the score: the Field Officer reviews
        // the newly supplied documents next.
        List<ApplicationDocument> currentDocs = applicationDocumentRepository
                .findByApplicationIdOrderByOptionalDocumentAscDocumentNameAsc(app.getId());
        Set<String> uploadedKeys = new HashSet<>();
        for (ApplicationDocument doc : currentDocs) uploadedKeys.add(doc.getDocumentKey());

        Beneficiary b = app.getUser().getBeneficiary();
        SchemeEligibilityScore newScore = eligibilityService.calculateLiveScore(b, app.getScheme(), uploadedKeys);
        app.setEligibilityScore(newScore.getTotalScore());
        app.setEligibilityStatus(newScore.isEligible());
        subsidyApplicationRepository.save(app);

        // A citizen who supplied all officer-requested replacements has completed
        // their action. Move the application back to Field Verification regardless
        // of the recalculated score; the officer decides the next workflow action.
        eligibilityService.markApplicantResubmitted(app);

        redirectAttributes.addFlashAttribute("success",
                "Application resubmitted successfully and sent to the Field Officer. Updated score: "
                        + newScore.getTotalScore() + "/100.");
        return "redirect:/dashboard/citizen?resubmitted=true";
    }

    private Set<String> uploadedDocumentKeys(Map<String, MultipartFile> files) {
        Set<String> keys = new HashSet<>();
        if (files == null) return keys;
        for (Map.Entry<String, MultipartFile> entry : files.entrySet()) {
            MultipartFile file = entry.getValue();
            if (file != null && !file.isEmpty() && entry.getKey().startsWith("document_")) {
                keys.add(entry.getKey().substring("document_".length()));
            }
        }
        return keys;
    }

    private void saveUploadedDocuments(SubsidyApplication application,
                                       Scheme scheme,
                                       Map<String, MultipartFile> files) {
        if (files == null || files.isEmpty()) return;

        Map<String, String> required = DocumentKeyUtil.parse(scheme.getRequiredDocuments());
        Map<String, String> optional = DocumentKeyUtil.parse(scheme.getOptionalDocuments());

        for (Map.Entry<String, MultipartFile> entry : files.entrySet()) {
            if (!entry.getKey().startsWith("document_")) continue;
            MultipartFile file = entry.getValue();
            if (file == null || file.isEmpty()) continue;

            String key = entry.getKey().substring("document_".length());
            String documentName = required.containsKey(key) ? required.get(key) : optional.get(key);
            if (documentName == null) continue;
            boolean optionalDocument = optional.containsKey(key);

            try {
                String original = file.getOriginalFilename() == null ? "document" : file.getOriginalFilename();
                String safeOriginal = org.springframework.util.StringUtils.cleanPath(original)
                        .replaceAll("[^a-zA-Z0-9._-]", "_");
                String stored = key + "_" + System.currentTimeMillis() + "_" + safeOriginal;
                cloudinaryStorage.upload(application.getId(), stored, file, false);

                ApplicationDocument doc = applicationDocumentRepository
                        .findByApplicationIdAndDocumentKey(application.getId(), key)
                        .orElseGet(ApplicationDocument::new);
                doc.setApplication(application);
                doc.setDocumentKey(key);
                doc.setDocumentName(documentName);
                doc.setOriginalFilename(original);
                doc.setStoredFilename(stored);
                doc.setOptionalDocument(optionalDocument);
                doc.setUploadedAt(LocalDateTime.now());
                // A replacement document must be reviewed again.
                doc.setVerificationStatus("PENDING");
                doc.setVerificationRemarks(null);
                doc.setVerifiedAt(null);
                doc.setVerifiedBy(null);
                applicationDocumentRepository.save(doc);
                if (!application.getDocuments().contains(doc)) {
                    application.getDocuments().add(doc);
                }
            } catch (Exception e) {
                throw new IllegalStateException("Could not save " + documentName + ": " + e.getMessage(), e);
            }
        }
    }

    // =========================================================
    // OFFICER ACTION ENDPOINTS (role-specific)
    // =========================================================

    /**
     * All officer action endpoints intentionally accept a parameter map.
     * This avoids Spring returning HTTP 400 when a browser sends `action`
     * instead of `workflowAction`, or an empty optional numeric field.
     */
    @PostMapping("/officer/field/document/{documentId}/{action}")
    public String fieldDocumentAction(@PathVariable Long documentId,
                                      @PathVariable String action,
                                      @RequestParam(value = "remarks", required = false) String remarks,
                                      RedirectAttributes redirectAttributes) {
        try {
            eligibilityService.documentAction(documentId, action, remarks, "FIELD_OFFICER");
            redirectAttributes.addFlashAttribute("success",
                    "VERIFY".equalsIgnoreCase(action) ? "Document verified." : "Document marked for resubmission. Send the application to the applicant when review is complete.");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", readableError(ex));
        }
        return "redirect:/dashboard/officer";
    }

    @PostMapping("/officer/district/document/{documentId}/{action}")
    public String districtDocumentAction(@PathVariable Long documentId,
                                         @PathVariable String action,
                                         @RequestParam(value = "remarks", required = false) String remarks,
                                         RedirectAttributes redirectAttributes) {
        try {
            eligibilityService.documentAction(documentId, action, remarks, "DISTRICT_OFFICER");
            redirectAttributes.addFlashAttribute("success",
                    "VERIFY".equalsIgnoreCase(action) ? "Document verified." : "Document marked for resubmission. Send the application to the applicant when review is complete.");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", readableError(ex));
        }
        return "redirect:/dashboard/officer";
    }

    @PostMapping("/officer/field/action/{appId}/{action}")
    public String fieldOfficerAction(@PathVariable Long appId,
                                     @PathVariable String action,
                                     @RequestParam(value = "remarks", required = false) String remarks,
                                     RedirectAttributes redirectAttributes) {
        try {
            eligibilityService.fieldOfficerAction(appId, action, remarks);
            redirectAttributes.addFlashAttribute("success", successMessage(action, "Field Officer"));
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", readableError(ex));
        }
        return "redirect:/dashboard/officer";
    }

    @PostMapping("/officer/district/action/{appId}/{action}")
    public String districtOfficerAction(@PathVariable Long appId,
                                        @PathVariable String action,
                                        @RequestParam(value = "remarks", required = false) String remarks,
                                        RedirectAttributes redirectAttributes) {
        try {
            eligibilityService.districtOfficerAction(appId, action, remarks);
            redirectAttributes.addFlashAttribute("success", successMessage(action, "District Officer"));
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", readableError(ex));
        }
        return "redirect:/dashboard/officer";
    }

    // Dedicated district-review action route. Keeping this separate from the
    // dashboard compatibility route prevents ambiguous form/action bindings.
    @PostMapping("/officer/district/eligibility/{appId}/action/{action}")
    public String districtEligibilityAction(@PathVariable Long appId,
                                            @PathVariable String action,
                                            @RequestParam(value = "remarks", required = false) String remarks,
                                            @AuthenticationPrincipal UserDetails userDetails,
                                            RedirectAttributes redirectAttributes) {
        if (userDetails == null) return "redirect:/login";
        boolean districtOfficer = userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().contains("DISTRICT_OFFICER"));
        if (!districtOfficer) return "redirect:/dashboard/officer";

        try {
            eligibilityService.districtOfficerAction(appId, action, remarks);
            redirectAttributes.addFlashAttribute("success", successMessage(action, "District Officer"));
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", readableError(ex));
        }
        return "redirect:/officer/district/eligibility/" + appId;
    }

    @PostMapping("/officer/finance/action/{appId}/{action}")
    public String financeApproverAction(@PathVariable Long appId,
                                        @PathVariable String action,
                                        @RequestParam(value = "remarks", required = false) String remarks,
                                        @RequestParam(value = "sanctionAmount", required = false) String sanctionAmount,
                                        RedirectAttributes redirectAttributes) {
        try {
            if ("APPROVE".equalsIgnoreCase(action)) {
                SubsidyApplication financeApp = subsidyApplicationRepository.findDetailedById(appId)
                        .orElseThrow(() -> new IllegalArgumentException("Application not found: " + appId));
                if (!financeApp.isPaymentBankDetailsVerified()) {
                    throw new IllegalStateException("Verify the applicant's bank details before sanctioning the amount.");
                }
            }
            double amount = parseDouble(sanctionAmount, 0.0);
            eligibilityService.financeApproverAction(appId, action, remarks, amount);
            redirectAttributes.addFlashAttribute("success", successMessage(action, "Finance Approver"));
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", readableError(ex));
        }
        return "redirect:/dashboard/officer";
    }

    @PostMapping("/officer/finance/{appId}/request-bank-details")
    public String requestBankDetails(@PathVariable Long appId, RedirectAttributes redirectAttributes) {
        try {
            SubsidyApplication app = subsidyApplicationRepository.findDetailedById(appId)
                    .orElseThrow(() -> new IllegalArgumentException("Application not found: " + appId));
            app.setPaymentBankDetailsRequested(true);
            app.setPaymentBankDetailsVerified(false);
            app.setPaymentBankVerificationStatus("REQUESTED");
            subsidyApplicationRepository.saveAndFlush(app);
            redirectAttributes.addFlashAttribute("success",
                    "Bank-detail re-verification requested only for application SUB-" + appId + ".");
        } catch (RuntimeException ex) { redirectAttributes.addFlashAttribute("error", readableError(ex)); }
        return "redirect:/dashboard/officer";
    }

    @PostMapping("/application/{appId}/bank-details")
    public String submitBankDetails(@PathVariable Long appId,
                                    @RequestParam String accountHolderName,
                                    @RequestParam String accountNumber,
                                    @RequestParam String ifscCode,
                                    @AuthenticationPrincipal UserDetails userDetails,
                                    RedirectAttributes redirectAttributes) {
        if (userDetails == null) return "redirect:/login";
        try {
            User user = userRepository.findByEmail(userDetails.getUsername())
                    .orElseThrow(() -> new IllegalArgumentException("User not found."));
            SubsidyApplication app = subsidyApplicationRepository.findDetailedById(appId)
                    .orElseThrow(() -> new IllegalArgumentException("Application not found: " + appId));
            if (app.getUser() == null || !app.getUser().getId().equals(user.getId()))
                throw new IllegalArgumentException("Unauthorized application.");
            if (!app.isPaymentBankDetailsRequested())
                throw new IllegalStateException("Finance has not requested bank details for SUB-" + appId + ".");

            String holder = accountHolderName == null ? "" : accountHolderName.trim();
            String account = accountNumber == null ? "" : accountNumber.replaceAll("\\s+", "");
            String ifsc = ifscCode == null ? "" : ifscCode.trim().toUpperCase();
            if (holder.isBlank()) throw new IllegalArgumentException("Account holder name is required.");
            if (!account.matches("[0-9]{8,20}")) throw new IllegalArgumentException("Enter a valid 8-20 digit account number.");
            if (!ifsc.matches("^[A-Z]{4}0[A-Z0-9]{6}$")) throw new IllegalArgumentException("Enter a valid IFSC code.");

            app.setPaymentAccountHolderName(holder);
            app.setPaymentAccountNumber(account);
            app.setPaymentIfscCode(ifsc);
            app.setPaymentBankName(bankNameFromIfsc(ifsc));
            app.setPaymentBankDetailsVerified(false);
            app.setPaymentBankVerificationStatus("SUBMITTED");
            subsidyApplicationRepository.saveAndFlush(app);
            redirectAttributes.addFlashAttribute("success",
                    "Bank details for SUB-" + appId + " submitted to Finance.");
        } catch (RuntimeException ex) { redirectAttributes.addFlashAttribute("error", readableError(ex)); }
        return "redirect:/dashboard/citizen";
    }

    @PostMapping("/officer/finance/{appId}/verify-bank-details")
    public String verifyBankDetails(@PathVariable Long appId, @RequestParam boolean approve,
                                    RedirectAttributes redirectAttributes) {
        try {
            SubsidyApplication app = subsidyApplicationRepository.findDetailedById(appId)
                    .orElseThrow(() -> new IllegalArgumentException("Application not found: " + appId));
            if (!"SUBMITTED".equalsIgnoreCase(app.getPaymentBankVerificationStatus()))
                throw new IllegalStateException("Applicant must submit bank details for SUB-" + appId + " first.");
            app.setPaymentBankDetailsVerified(approve);
            app.setPaymentBankVerificationStatus(approve ? "VERIFIED" : "REQUESTED");
            app.setPaymentBankDetailsRequested(!approve);
            subsidyApplicationRepository.saveAndFlush(app);
            redirectAttributes.addFlashAttribute("success",
                    approve ? "Bank details verified for SUB-" + appId + "."
                            : "Bank details for SUB-" + appId + " returned for correction.");
        } catch (RuntimeException ex) { redirectAttributes.addFlashAttribute("error", readableError(ex)); }
        return "redirect:/dashboard/officer";
    }

    private String bankNameFromIfsc(String ifsc) {
        if (ifsc == null || ifsc.length() < 4) return "Bank to be verified by Finance";
        return switch (ifsc.substring(0,4).toUpperCase()) {
            case "SBIN" -> "State Bank of India"; case "HDFC" -> "HDFC Bank";
            case "ICIC" -> "ICICI Bank"; case "UTIB" -> "Axis Bank";
            case "PUNB" -> "Punjab National Bank"; case "BARB" -> "Bank of Baroda";
            case "CNRB" -> "Canara Bank"; case "UBIN" -> "Union Bank of India";
            case "BKID" -> "Bank of India"; case "MAHB" -> "Bank of Maharashtra";
            case "IDIB" -> "Indian Bank"; case "KKBK" -> "Kotak Mahindra Bank";
            default -> "Bank to be verified by Finance";
        };
    }

    @PostMapping("/officer/disbursal/action/{appId}/{action}")
    public String disbursalOfficerAction(@PathVariable Long appId,
                                         @PathVariable String action,
                                         @RequestParam(value = "utrNumber", required = false) String utr,
                                         @RequestParam(value = "remarks", required = false) String remarks,
                                         RedirectAttributes redirectAttributes) {
        try {
            eligibilityService.fundDisbursementAction(appId, action, utr, remarks);
            redirectAttributes.addFlashAttribute("success", successMessage(action, "Disbursement Officer"));
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", readableError(ex));
        }
        return "redirect:/dashboard/officer";
    }

    // =========================================================
    // APPLICATION DETAILS - OFFICER
    // =========================================================
    @GetMapping("/officer/application/{appId}")
    public String officerApplicationDetails(@PathVariable Long appId,
                                            @AuthenticationPrincipal UserDetails userDetails,
                                            Model model) {
        if (userDetails == null) return "redirect:/login";
        if (!hasOfficerRole(userDetails)) return "redirect:/dashboard/citizen";

        SubsidyApplication application = subsidyApplicationRepository.findDetailedById(appId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found: " + appId));
        enrichApplicationDocumentState(application);
        populateApplicationDetails(model, application);
        boolean adminViewer = userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ADMIN"));
        model.addAttribute("viewerType", adminViewer ? "ADMIN" : "OFFICER");
        return "application-details";
    }

    // =========================================================
    // APPLICATION DETAILS - APPLICANT
    // =========================================================
    @GetMapping({"/application", "/application/"})
    public String applicationRoot(@AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) return "redirect:/login";
        return "redirect:/dashboard/citizen";
    }

    @GetMapping("/application/{appId}")
    public String applicantApplicationDetails(@PathVariable Long appId,
                                              @AuthenticationPrincipal UserDetails userDetails,
                                              Model model) {
        if (userDetails == null) return "redirect:/login";
        User user = userRepository.findByEmail(userDetails.getUsername()).orElse(null);
        if (user == null) return "redirect:/login?error=SessionExpired";

        SubsidyApplication application = subsidyApplicationRepository.findDetailedById(appId).orElse(null);
        if (application == null) {
            return "redirect:/dashboard/citizen?error=ApplicationNotFound";
        }
        if (application.getUser() == null || application.getUser().getId() == null
                || !application.getUser().getId().equals(user.getId())) {
            return "redirect:/dashboard/citizen?error=UnauthorizedApplication";
        }

        enrichApplicationDocumentState(application);
        populateApplicationDetails(model, application);
        model.addAttribute("viewerType", "APPLICANT");
        return "application-details";
    }

    @GetMapping({"/application/{appId}/pdf", "/application/{appId}/download"})
    public ResponseEntity<byte[]> applicantApplicationPdf(@PathVariable("appId") Long appId,
                                                          @AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) return ResponseEntity.status(401).build();
        User user = userRepository.findByEmail(userDetails.getUsername()).orElse(null);
        SubsidyApplication app = subsidyApplicationRepository.findDetailedById(appId).orElse(null);
        if (user == null || app == null || app.getUser() == null || !app.getUser().getId().equals(user.getId()))
            return ResponseEntity.status(403).build();
        return applicationPdf(app);
    }

    @GetMapping("/application/{appId}/sanction-letter")
    public ResponseEntity<byte[]> sanctionLetter(@PathVariable Long appId,
                                                 @AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) return ResponseEntity.status(401).build();

        User user = userRepository.findByEmail(userDetails.getUsername()).orElse(null);
        SubsidyApplication app = subsidyApplicationRepository.findDetailedById(appId).orElse(null);
        if (user == null || app == null || app.getUser() == null
                || !app.getUser().getId().equals(user.getId())) {
            return ResponseEntity.status(403).build();
        }
        if (app.getSanctionAmount() <= 0) return ResponseEntity.notFound().build();

        Beneficiary b = app.getUser().getBeneficiary();
        List<String> lines = new java.util.ArrayList<>();
        lines.add("Sanction Reference: SAGAR/SUB/" + app.getId() + "/" + java.time.LocalDate.now().getYear());
        lines.add("Application ID: SUB-" + app.getId());
        lines.add("Applicant: " + (b == null ? app.getUser().getEmail() : b.getFullName()));
        lines.add("Contact: " + (b == null ? "N/A" : b.getContactNumber()));
        lines.add("Scheme: " + (app.getScheme() == null ? "N/A" : app.getScheme().getSchemeName()));
        lines.add("Eligibility Score: " + app.getEligibilityScore() + "/100");
        lines.add("Sanctioned Amount: INR " + String.format(java.util.Locale.US, "%.2f", app.getSanctionAmount()));
        lines.add("Sanction / Finance Approval Date: " + java.time.LocalDate.now());
        lines.add("");
        lines.add("STAGE-WISE FUND RELEASE SCHEDULE");

        DisbursementPlan plan = disbursementPlanRepository.findByApplicationId(appId).orElse(null);
        if (plan == null) {
            lines.add("Schedule is being configured by the Finance Officer.");
        } else {
            for (com.platform.disbursement.DisbursementMilestone m : plan.getMilestones()) {
                lines.add("Stage " + m.getSequenceNo() + ": " + m.getMilestoneName());
                lines.add("  Amount: INR " + String.format(java.util.Locale.US, "%.2f", m.getScheduledAmount())
                        + " | Due: " + m.getDueDate() + " | Status: " + m.getStatus());
                lines.add("  Required compliance/document: "
                        + (m.getComplianceRequirement() == null ? "As requested by Finance Officer" : m.getComplianceRequirement()));
            }
        }
        lines.add("");
        lines.add("Important: Submit requested milestone documents before the stated due date.");
        lines.add("Funds are released only after the required compliance is verified.");

        byte[] pdf = SimplePdfUtil.onePage("SAGAR - Subsidy Sanction Letter", lines);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=sanction-letter-SUB-" + app.getId() + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @GetMapping("/application/{appId}/receipt")
    public ResponseEntity<byte[]> applicationDisbursementReceipt(@PathVariable Long appId,
                                                                 @AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) return ResponseEntity.status(401).build();
        User user = userRepository.findByEmail(userDetails.getUsername()).orElse(null);
        SubsidyApplication app = subsidyApplicationRepository.findDetailedById(appId).orElse(null);
        if (user == null || app == null || app.getUser() == null
                || !app.getUser().getId().equals(user.getId())) {
            return ResponseEntity.status(403).build();
        }

        DisbursementPlan plan = disbursementPlanRepository.findByApplicationId(appId).orElse(null);
        if (plan == null || plan.getMilestones() == null
                || plan.getMilestones().stream().noneMatch(m -> m.getReleasedAmount() > 0)) {
            return ResponseEntity.notFound().build();
        }

        Beneficiary b = app.getUser().getBeneficiary();
        List<String> lines = new java.util.ArrayList<>();
        lines.add("Application: SUB-" + app.getId());
        lines.add("Beneficiary: " + (b == null ? app.getUser().getEmail() : b.getFullName()));
        lines.add("Scheme: " + (app.getScheme() == null ? "N/A" : app.getScheme().getSchemeName()));
        lines.add("Sanctioned Amount: INR " + String.format(java.util.Locale.US, "%.2f", app.getSanctionAmount()));
        lines.add("Payment Account: " + maskAccount(app.getPaymentAccountNumber()));
        lines.add("IFSC: " + safePdf(app.getPaymentIfscCode()));
        lines.add("Bank: " + safePdf(app.getPaymentBankName()));
        lines.add("");
        lines.add("STAGE-WISE TRANSACTIONS");

        double totalReleased = 0.0;
        for (com.platform.disbursement.DisbursementMilestone m : plan.getMilestones()) {
            if (m.getReleasedAmount() <= 0) continue;
            totalReleased += m.getReleasedAmount();
            lines.add("Stage " + m.getSequenceNo() + ": " + m.getMilestoneName());
            lines.add("  Released: INR " + String.format(java.util.Locale.US, "%.2f", m.getReleasedAmount()));
            lines.add("  Transaction / Demo UTR: " + safePdf(m.getReleaseReference()));
            lines.add("  Released At: " + (m.getReleasedAt() != null ? m.getReleasedAt() : m.getReleaseDate()));
        }
        lines.add("");
        lines.add("Total Released: INR " + String.format(java.util.Locale.US, "%.2f", totalReleased));
        lines.add("Plan Status: " + plan.getStatus());
        lines.add("System-generated receipt for the SAGAR prototype / simulated disbursement.");

        byte[] pdf = SimplePdfUtil.onePage("SAGAR - Disbursement Receipt", lines);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=disbursement-receipt-SUB-" + app.getId() + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    private String maskAccount(String account) {
        if (account == null || account.isBlank()) return "N/A";
        return account.length() <= 4 ? account : "XXXXXX" + account.substring(account.length() - 4);
    }

    private String safePdf(String value) {
        return value == null || value.isBlank() ? "N/A" : value;
    }

    @GetMapping("/officer/application/{appId}/pdf")
    public ResponseEntity<byte[]> officerApplicationPdf(@PathVariable Long appId,
                                                        @AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null || !hasOfficerRole(userDetails)) return ResponseEntity.status(403).build();
        SubsidyApplication app = subsidyApplicationRepository.findDetailedById(appId).orElse(null);
        if (app == null) return ResponseEntity.notFound().build();
        return applicationPdf(app);
    }

    private ResponseEntity<byte[]> applicationPdf(SubsidyApplication app) {
        Beneficiary b = app.getUser() == null ? null : app.getUser().getBeneficiary();
        List<String> lines = new java.util.ArrayList<>();

        lines.add("APPLICATION SUMMARY");
        lines.add("Application ID: SUB-" + app.getId());
        lines.add("Application Date: " + app.getApplicationDate());
        lines.add("Status: " + app.getApplicationStatus());
        lines.add("Eligibility: " + (app.isEligibilityStatus() ? "Eligible" : "Not Eligible / Pending"));
        lines.add("Eligibility Score: " + app.getEligibilityScore() + "/100");
        lines.add("Resubmissions: " + app.getResubmissionCount());
        lines.add("Rejection Attempts: " + app.getRejectionCount() + "/3");
        lines.add("Sanction Amount: INR " + String.format(java.util.Locale.US, "%.2f", app.getSanctionAmount()));
        lines.add("Final Transaction Reference: " + safePdf(app.getUtrNumber()));
        lines.add("Disbursed At: " + (app.getDisbursedAt() == null ? "N/A" : app.getDisbursedAt().toString()));
        lines.add("");

        lines.add("APPLICANT DETAILS");
        lines.add("Name: " + (b == null ? "N/A" : safePdf(b.getFullName())));
        lines.add("Email: " + (app.getUser() == null ? "N/A" : safePdf(app.getUser().getEmail())));
        if (b != null) {
            lines.add("Government ID: " + safePdf(b.getGovernmentId()));
            lines.add("Contact: " + safePdf(b.getContactNumber()));
            lines.add("Age: " + b.getAge());
            lines.add("Gender: " + safePdf(b.getGender()));
            lines.add("Occupation: " + safePdf(b.getOccupation()));
            lines.add("Annual Income: INR " + String.format(java.util.Locale.US, "%.2f", b.getIncome()));
            lines.add("State: " + safePdf(b.getState()));
            lines.add("Address: " + safePdf(b.getAddress()));
        }
        lines.add("");

        lines.add("SCHEME");
        lines.add("Scheme: " + (app.getScheme() == null ? "N/A" : safePdf(app.getScheme().getSchemeName())));
        lines.add("");

        lines.add("VERIFIED PAYMENT DETAILS");
        lines.add("Account Holder: " + safePdf(app.getPaymentAccountHolderName()));
        lines.add("Account: " + maskAccount(app.getPaymentAccountNumber()));
        lines.add("IFSC: " + safePdf(app.getPaymentIfscCode()));
        lines.add("Bank: " + safePdf(app.getPaymentBankName()));
        lines.add("Finance Verified: " + (app.isPaymentBankDetailsVerified() ? "YES" : "NO"));
        lines.add("");

        lines.add("DOCUMENTS");
        if (app.getDocuments() == null || app.getDocuments().isEmpty()) {
            lines.add("No documents recorded.");
        } else {
            for (ApplicationDocument d : app.getDocuments()) {
                lines.add("- " + d.getDocumentName() + " | " + d.getOriginalFilename()
                        + " | " + (d.getVerificationStatus() == null ? "PENDING" : d.getVerificationStatus()));
            }
        }
        lines.add("");

        lines.add("WORKFLOW REMARKS");
        lines.add("Field: " + safePdf(app.getFieldInspectionRemarks()));
        lines.add("District: " + safePdf(app.getDistrictReviewRemarks()));
        lines.add("Finance: " + safePdf(app.getFinanceApprovalRemarks()));
        lines.add("Disbursement: " + safePdf(app.getDisbursementRemarks()));

        byte[] pdf = SimplePdfUtil.onePage("SAGAR - Complete Application Details", lines);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=application-SUB-" + app.getId() + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    // =========================================================
    // ADMIN MONITOR
    // =========================================================
    @GetMapping("/eligibility/admin/monitor")
    public String showAdminMonitor(Model model) {
        model.addAttribute("apps", subsidyApplicationRepository.findAllByOrderByApplicationDateDesc());
        return "admin-monitor";
    }

    // =========================================================
    // SCHEME DETAIL PAGE
    // =========================================================
    @GetMapping("/scheme/{schemeId}/detail")
    public String showSchemeDetail(@PathVariable Long schemeId, Model model,
                                   @AuthenticationPrincipal UserDetails userDetails) {
        Scheme scheme = schemeService.getSchemeById(schemeId);
        model.addAttribute("scheme", scheme);
        if (userDetails != null) {
            User user = userRepository.findByEmail(userDetails.getUsername()).orElse(null);
            model.addAttribute("user", user);
        }
        return "scheme-detail";
    }
    // =========================================================
// DISTRICT OFFICER: VIEW ELIGIBILITY SCORE BREAKDOWN
// =========================================================

    /**
     * Allow District Officer to view detailed eligibility score breakdown
     * Shows which criteria passed/failed and which documents are available
     * Allows officer to add remarks suggesting improvements
     */
    @GetMapping("/officer/district/eligibility/{appId}")
    public String viewEligibilityScoreBreakdown(@PathVariable Long appId,
                                                @AuthenticationPrincipal UserDetails userDetails,
                                                Model model) {
        if (userDetails == null) return "redirect:/login";

        // Verify user is district officer
        boolean isDistrictOfficer = userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().contains("DISTRICT_OFFICER"));
        if (!isDistrictOfficer) {
            return "redirect:/dashboard?error=Unauthorized";
        }

        SubsidyApplication app = subsidyApplicationRepository.findDetailedById(appId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found: " + appId));

        // The detailed page is also used after a send-back/resubmission.
        // Do not fail merely because the application moved between queues.
        // The page remains a read/review view and the action buttons enforce
        // the valid workflow transition in EligibilityService.

        // Recalculate the current score breakdown
        Beneficiary beneficiary = app.getUser().getBeneficiary();
        Scheme scheme = app.getScheme();

        List<ApplicationDocument> applicationDocuments = applicationDocumentRepository
                .findByApplicationIdOrderByOptionalDocumentAscDocumentNameAsc(appId);
        Set<String> uploadedKeys = new HashSet<>();
        for (ApplicationDocument doc : applicationDocuments) uploadedKeys.add(doc.getDocumentKey());

        SchemeEligibilityScore scoreBreakdown = eligibilityService.calculateLiveScore(
                beneficiary, scheme, uploadedKeys);
        enrichApplicationDocumentState(app);
        model.addAttribute("applicationDocuments", applicationDocuments);
        model.addAttribute("applicationRemarks", app.getRemarks());
        model.addAttribute("missingOptionalDocuments", app.getMissingOptionalDocuments());
        model.addAttribute("missingMandatoryDocuments", app.getMissingMandatoryDocuments());
        model.addAttribute("optionalDocuments", DocumentKeyUtil.parse(scheme.getOptionalDocuments()));
        model.addAttribute("uploadedDocumentKeys", uploadedKeys);
        model.addAttribute("selectedOptionalKeys",
                districtOptionalRequests.getOrDefault(appId, java.util.Collections.emptyList()));

        model.addAttribute("application", app);
        model.addAttribute("appId", appId);
        model.addAttribute("scoreBreakdown", scoreBreakdown);
        model.addAttribute("beneficiary", beneficiary);
        model.addAttribute("scheme", scheme);
        model.addAttribute("isDistrictOfficer", true);

        return "district-officer-eligibility-review";
    }
    @PostMapping("/officer/district/eligibility/{appId}/request-optional")
    public String requestOptionalDocuments(@PathVariable Long appId,
                                           @RequestParam(value = "requestedOptionalKeys", required = false) List<String> requestedOptionalKeys,
                                           @AuthenticationPrincipal UserDetails userDetails,
                                           RedirectAttributes redirectAttributes) {
        if (userDetails == null) return "redirect:/login";
        boolean districtOfficer = userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().contains("DISTRICT_OFFICER"));
        if (!districtOfficer) return "redirect:/dashboard/officer";

        SubsidyApplication app = subsidyApplicationRepository.findDetailedById(appId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found: " + appId));

        if (app.getApplicationStatus() != ApplicationStatus.DISTRICT_REVIEW) {
            redirectAttributes.addFlashAttribute("error",
                    "Optional documents can be selected only while the application is in District Review.");
            return "redirect:/officer/district/eligibility/" + appId;
        }

        LinkedHashMap<String, String> optional =
                DocumentKeyUtil.parse(app.getScheme().getOptionalDocuments());
        Set<String> alreadyUploaded = applicationDocumentRepository
                .findByApplicationIdOrderByOptionalDocumentAscDocumentNameAsc(appId)
                .stream().map(ApplicationDocument::getDocumentKey)
                .collect(java.util.stream.Collectors.toSet());

        List<String> validKeys = new java.util.ArrayList<>();
        if (requestedOptionalKeys != null) {
            for (String key : requestedOptionalKeys) {
                if (key != null && optional.containsKey(key) && !alreadyUploaded.contains(key)) {
                    validKeys.add(key);
                }
            }
        }

        if (validKeys.isEmpty()) {
            districtOptionalRequests.remove(appId);
            redirectAttributes.addFlashAttribute("error",
                    "Select at least one missing optional document.");
            return "redirect:/officer/district/eligibility/" + appId;
        }

        // IMPORTANT: selection only. No status change and no applicant notification yet.
        districtOptionalRequests.put(appId, List.copyOf(validKeys));
        redirectAttributes.addFlashAttribute("success",
                "Optional documents selected. Add a remark and click Send to Applicant.");
        return "redirect:/officer/district/eligibility/" + appId;
    }

    // =========================================================
// DISTRICT OFFICER: SUBMIT REMARKS & RECOMMENDATIONS
// =========================================================

    /**
     * District Officer submits remarks suggesting improvements
     * This allows the officer to guide the citizen on what's needed to improve
     */
    @PostMapping("/officer/district/eligibility/{appId}/remark/{action}")
    public String submitEligibilityRemark(@PathVariable Long appId,
                                          @PathVariable String action,
                                          @RequestParam(value = "remarks", required = false) String remarks,
                                          @AuthenticationPrincipal UserDetails userDetails,
                                          RedirectAttributes redirectAttributes) {
        if (userDetails == null) return "redirect:/login";
        try {
            eligibilityService.districtOfficerAction(appId, action, remarks);
            redirectAttributes.addFlashAttribute("success", successMessage(action, "District Officer"));
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", readableError(ex));
        }
        return "redirect:/officer/district/eligibility/" + appId;
    }

    @PostMapping("/officer/district/action")
    public String districtOfficerActionCompatibility(
            @RequestParam(value = "appId", required = false) Long appId,
            @RequestParam(value = "id", required = false) Long id,
            @RequestParam(value = "workflowAction", required = false) String workflowAction,
            @RequestParam(value = "action", required = false) String action,
            @RequestParam(value = "remarks", required = false) String remarks,
            RedirectAttributes redirectAttributes) {
        Long actualId = appId != null ? appId : id;
        String actualAction = firstValue(Map.of("workflowAction", workflowAction == null ? "" : workflowAction,
                "action", action == null ? "" : action), "workflowAction", "action");
        if (actualId == null || actualAction == null) {
            redirectAttributes.addFlashAttribute("error", "Application ID and workflow action are required.");
            return "redirect:/dashboard/officer";
        }
        try {
            String finalRemarks = remarks;
            if ("SEND_BACK_APPLICANT".equalsIgnoreCase(actualAction)
                    || "SEND_TO_APPLICANT".equalsIgnoreCase(actualAction)) {
                List<String> selectedKeys = districtOptionalRequests.get(actualId);
                if (selectedKeys != null && !selectedKeys.isEmpty()) {
                    SubsidyApplication selectedApp = subsidyApplicationRepository.findDetailedById(actualId)
                            .orElseThrow(() -> new IllegalArgumentException("Application not found: " + actualId));
                    LinkedHashMap<String, String> optional =
                            DocumentKeyUtil.parse(selectedApp.getScheme().getOptionalDocuments());
                    List<String> names = new java.util.ArrayList<>();
                    for (String key : selectedKeys) {
                        if (optional.containsKey(key)) names.add(optional.get(key));
                    }
                    String baseRemark = (remarks == null || remarks.isBlank())
                            ? "Please upload the selected optional document(s) to improve your eligibility score."
                            : remarks.trim();
                    finalRemarks = baseRemark
                            + " Requested optional documents: " + String.join(", ", names)
                            + ". [OPTIONAL_KEYS:" + String.join(",", selectedKeys) + "]";
                }
            }

            eligibilityService.districtOfficerAction(actualId, actualAction, finalRemarks);
            if ("SEND_BACK_APPLICANT".equalsIgnoreCase(actualAction)
                    || "SEND_TO_APPLICANT".equalsIgnoreCase(actualAction)) {
                districtOptionalRequests.remove(actualId);
            }
            redirectAttributes.addFlashAttribute("success", successMessage(actualAction, "District Officer"));
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", readableError(ex));
        }
        return "redirect:/dashboard/officer";
    }

    @PostMapping("/officer/district/eligibility/{appId}/remark")
    public String submitEligibilityRemarkCompatibility(@PathVariable Long appId,
                                                       @RequestParam(value = "action", required = false) String action,
                                                       @RequestParam(value = "workflowAction", required = false) String workflowAction,
                                                       @RequestParam(value = "remarks", required = false) String remarks,
                                                       @AuthenticationPrincipal UserDetails userDetails,
                                                       RedirectAttributes redirectAttributes) {
        if (userDetails == null) return "redirect:/login";
        String actualAction = (action != null && !action.isBlank()) ? action : workflowAction;
        if (actualAction == null || actualAction.isBlank()) {
            redirectAttributes.addFlashAttribute("error", "Workflow action is required.");
            return "redirect:/officer/district/eligibility/" + appId;
        }
        try {
            eligibilityService.districtOfficerAction(appId, actualAction, remarks);
            redirectAttributes.addFlashAttribute("success", successMessage(actualAction, "District Officer"));
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", readableError(ex));
        }
        return "redirect:/officer/district/eligibility/" + appId;
    }

    private String firstValue(Map<String, String> params, String... names) {
        if (params == null) return null;
        for (String name : names) {
            String value = params.get(name);
            if (value != null && !value.isBlank()) return value.trim();
        }
        return null;
    }

    private Long requiredLong(Map<String, String> params, String... names) {
        String value = firstValue(params, names);
        return parseLong(value);
    }

    private Long parseLong(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Application ID is missing.");
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid application ID: " + value);
        }
    }

    private double parseDouble(String value, double defaultValue) {
        if (value == null || value.isBlank()) return defaultValue;
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid sanction amount: " + value);
        }
    }

    private String requiredString(Map<String, String> params, String... names) {
        String value = firstValue(params, names);
        if (value == null) throw new IllegalArgumentException("Workflow action is missing.");
        return value;
    }

    private String readableError(RuntimeException ex) {
        String message = ex.getMessage();
        return (message == null || message.isBlank()) ? "The requested workflow action could not be completed." : message;
    }

    private void enrichApplicationDocumentState(SubsidyApplication app) {
        List<ApplicationDocument> docs = applicationDocumentRepository
                .findByApplicationIdOrderByOptionalDocumentAscDocumentNameAsc(app.getId());
        app.setDocuments(docs);
        app.setRemarks(applicationRemarkRepository.findByApplicationIdOrderByCreatedAtAsc(app.getId()));
        Set<String> uploaded = new HashSet<>();
        for (ApplicationDocument doc : docs) uploaded.add(doc.getDocumentKey());

        LinkedHashMap<String, String> required = app.getScheme() == null
                ? new LinkedHashMap<>()
                : DocumentKeyUtil.parse(app.getScheme().getRequiredDocuments());
        LinkedHashMap<String, String> optional = app.getScheme() == null
                ? new LinkedHashMap<>()
                : DocumentKeyUtil.parse(app.getScheme().getOptionalDocuments());
        List<String> missingRequired = new java.util.ArrayList<>();
        List<String> missingOptional = new java.util.ArrayList<>();
        required.forEach((key, name) -> { if (!uploaded.contains(key)) missingRequired.add(name); });
        optional.forEach((key, name) -> { if (!uploaded.contains(key)) missingOptional.add(name); });
        app.setMissingMandatoryDocuments(missingRequired);
        app.setMissingOptionalDocuments(missingOptional);
    }

    private boolean hasOfficerRole(UserDetails userDetails) {
        return userDetails.getAuthorities().stream().anyMatch(a -> {
            String role = a.getAuthority();
            return role.equals("FIELD_OFFICER") || role.equals("ROLE_FIELD_OFFICER")
                    || role.equals("DISTRICT_OFFICER") || role.equals("ROLE_DISTRICT_OFFICER")
                    || role.equals("FINANCE_APPROVER") || role.equals("ROLE_FINANCE_APPROVER")
                    || role.equals("DISBURSEMENT_OFFICER") || role.equals("ROLE_DISBURSEMENT_OFFICER")
                    || role.equals("ADMIN") || role.equals("ROLE_ADMIN");
        });
    }

    private void populateApplicationDetails(Model model, SubsidyApplication application) {
        model.addAttribute("application", application);
        model.addAttribute("beneficiary", application.getUser() != null ? application.getUser().getBeneficiary() : null);
        model.addAttribute("scheme", application.getScheme());
        model.addAttribute("applicationDocuments", application.getDocuments());
        model.addAttribute("applicationRemarks", application.getRemarks());

        // Stable values used by application-details.html.
        // Keeping these explicit prevents the page from showing SUB-null / N/A
        // when a nested Thymeleaf expression is unavailable.
        model.addAttribute("applicationId", application.getId());
        model.addAttribute("applicantEmail",
                application.getUser() != null && application.getUser().getEmail() != null
                        ? application.getUser().getEmail() : "N/A");
        model.addAttribute("appliedSchemeName",
                application.getScheme() != null && application.getScheme().getSchemeName() != null
                        ? application.getScheme().getSchemeName() : "N/A");
        model.addAttribute("summaryApplicationDate", application.getApplicationDate());
        model.addAttribute("summaryStatus", application.getApplicationStatus());
        model.addAttribute("summaryEligibilityStatus", application.isEligibilityStatus());
        model.addAttribute("summaryEligibilityScore", application.getEligibilityScore());
        model.addAttribute("summaryResubmissionCount", application.getResubmissionCount());
        model.addAttribute("summaryRejectionCount", application.getRejectionCount());
        model.addAttribute("summarySanctionAmount", application.getSanctionAmount());
        model.addAttribute("summaryUtrNumber", application.getUtrNumber());
    }

    private String successMessage(String action, String actor) {
        if (action == null) return actor + " action completed.";
        return switch (action.toUpperCase()) {
            case "SEND_BACK_APPLICANT", "REVERIFY", "REQUEST_OPTIONAL_DOCUMENTS" ->
                    "Application sent to the applicant for re-verification.";
            case "SENDBACK" -> "Application sent back to the previous officer stage.";
            case "APPROVE" -> "Application moved to the next workflow stage.";
            case "DISBURSE" -> "Funds marked as disbursed successfully.";
            case "REJECT" -> "Application rejection recorded. The applicant can use the remaining permitted attempt(s) before the cooling period is applied.";
            default -> actor + " action completed successfully.";
        };
    }

    // ─── helpers ───────────────────────────────────────────
    private void copyBeneficiary(Beneficiary src, Beneficiary dst, User user, String schemeName) {
        dst.setUser(user);
        dst.setFullName(src.getFullName());
        dst.setGovernmentId(src.getGovernmentId());
        dst.setContactNumber(src.getContactNumber());
        dst.setAge(src.getAge());
        dst.setState(src.getState());
        dst.setIncome(src.getIncome());
        dst.setAddress(src.getAddress());
        dst.setOccupation(src.getOccupation());
        dst.setGender(src.getGender());
        dst.setDisabilityStatus(src.getDisabilityStatus());
        dst.setResidenceType(src.getResidenceType());
        dst.setSchemeName(schemeName);
    }
    private String maskEmail(String email) {
        int at = email == null ? -1 : email.indexOf('@');
        if (at <= 1) return email;
        return email.charAt(0) + "***" + email.substring(at - 1);
    }

}