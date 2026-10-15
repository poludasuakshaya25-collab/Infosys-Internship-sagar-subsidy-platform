package com.platform.disbursement;

import com.platform.application.SubsidyApplication;
import com.platform.scheme.Scheme;
import com.platform.application.SubsidyApplicationRepository;
import com.platform.util.SimplePdfUtil;
import com.platform.security.JurisdictionService;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalTime;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import java.util.ArrayList;
import java.util.List;

@Controller
@RequestMapping("/milestone3")
public class Milestone3Controller {
    @org.springframework.beans.factory.annotation.Autowired private com.platform.config.CloudinaryStorageService cloudinaryStorage;

    private final SubsidyApplicationRepository applicationRepository;
    private final DisbursementPlanService planService;
    private final DisbursementAnalyticsService analyticsService;
    private final com.platform.scheme.SchemeRepository schemeRepository;
    private final JurisdictionService jurisdictionService;

    public Milestone3Controller(SubsidyApplicationRepository applicationRepository,
                                DisbursementPlanService planService,
                                DisbursementAnalyticsService analyticsService,
                                com.platform.scheme.SchemeRepository schemeRepository,
                                JurisdictionService jurisdictionService) {
        this.applicationRepository = applicationRepository;
        this.planService = planService;
        this.analyticsService = analyticsService;
        this.schemeRepository = schemeRepository;
        this.jurisdictionService = jurisdictionService;
    }

    @GetMapping("/disbursement/{applicationId}")
    public String disbursementPlan(@PathVariable Long applicationId,
                                   @AuthenticationPrincipal UserDetails userDetails,
                                   Model model,
                                   RedirectAttributes redirectAttributes) {
        if (!isDisbursementOfficer(userDetails)) return "redirect:/dashboard/officer";

        try {
            SubsidyApplication app = requireJurisdiction(applicationId, userDetails);
            DisbursementPlan plan = planService.getOrCreatePlan(app);
            model.addAttribute("application", app);
            model.addAttribute("appId", applicationId);
            model.addAttribute("sanctionAmount", app.getSanctionAmount());
            com.platform.user.Beneficiary beneficiary = app.getUser() == null ? null : app.getUser().getBeneficiary();
            model.addAttribute("beneficiary", beneficiary);
            String beneficiaryName = beneficiary == null ? null : beneficiary.getFullName();
            if (beneficiaryName == null || beneficiaryName.isBlank()) {
                beneficiaryName = app.getUser() == null ? null : app.getUser().getFullName();
            }
            if (beneficiaryName == null || beneficiaryName.isBlank()) {
                beneficiaryName = app.getUser() == null ? "Not available" : app.getUser().getEmail();
            }
            model.addAttribute("beneficiaryName", beneficiaryName);
            model.addAttribute("schemeName", app.getScheme() == null ? "—" : app.getScheme().getSchemeName());
            String account = app.getPaymentAccountNumber();
            model.addAttribute("paymentAccountHolderName", app.getPaymentAccountHolderName());
            model.addAttribute("paymentAccountNumber", account);
            model.addAttribute("paymentIfscCode", app.getPaymentIfscCode());
            model.addAttribute("paymentBankName", app.getPaymentBankName());
            model.addAttribute("paymentBankVerified", app.isPaymentBankDetailsVerified());
            model.addAttribute("maskedAccount", account == null || account.length() < 4 ? "Not verified" : "XXXXXX" + account.substring(account.length()-4));
            model.addAttribute("plan", plan);
            model.addAttribute("reminderHistory", planService.getReminderHistoryForPlan(plan.getId()));
            model.addAttribute("planForm", toForm(plan));
            model.addAttribute("today", LocalDate.now());
            return "milestone3-disbursement";
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/dashboard/officer";
        }
    }

    @PostMapping("/disbursement/{applicationId}/save")
    public String savePlan(@PathVariable Long applicationId,
                           @ModelAttribute DisbursementPlanForm form,
                           @AuthenticationPrincipal UserDetails userDetails,
                           RedirectAttributes redirectAttributes) {
        if (!isDisbursementOfficer(userDetails)) return "redirect:/dashboard/officer";

        try {
            requireJurisdiction(applicationId, userDetails);
            List<DisbursementPlanService.MilestoneInput> inputs = new ArrayList<>();
            for (DisbursementPlanForm.MilestoneForm m : form.getMilestones()) {
                if (m.getName() == null || m.getName().isBlank()
                        || m.getDueDate() == null || m.getDueDate().isBlank()
                        || m.getDueTime() == null || m.getDueTime().isBlank()
                        || m.getScheduledAmount() < 0) {
                    throw new IllegalArgumentException("Every milestone needs a name, due date and valid amount.");
                }
                inputs.add(new DisbursementPlanService.MilestoneInput(
                        m.getName().trim(),
                        m.getRequirement() == null ? "" : m.getRequirement().trim(),
                        LocalDate.parse(m.getDueDate()),
                        (m.getDueTime() == null || m.getDueTime().isBlank()) ? LocalTime.of(10,0) : LocalTime.parse(m.getDueTime()),
                        m.getScheduledAmount()
                ));
            }
            planService.updatePlan(applicationId, inputs);
            redirectAttributes.addFlashAttribute("success", "Staged disbursement schedule saved successfully.");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/milestone3/disbursement/" + applicationId;
    }

    @PostMapping("/citizen/milestone/{milestoneId}/upload")
    public String citizenUploadCompliance(@PathVariable Long milestoneId,
                                          @RequestParam("document") MultipartFile document,
                                          @RequestParam(required = false) String remarks,
                                          @AuthenticationPrincipal UserDetails userDetails,
                                          RedirectAttributes redirectAttributes) {
        if (userDetails == null) return "redirect:/login";
        try {
            Long applicationId = planService.getApplicationIdForMilestone(milestoneId);
            SubsidyApplication application = applicationRepository.findDetailedById(applicationId)
                    .orElseThrow(() -> new IllegalArgumentException("Application not found."));
            com.platform.user.User user = application.getUser();
            if (user == null || !user.getEmail().equals(userDetails.getUsername())) {
                throw new IllegalArgumentException("You are not authorized to update this milestone.");
            }
            planService.submitApplicantCompliance(milestoneId, document, remarks, user.getId());
            redirectAttributes.addFlashAttribute("success", "Compliance document submitted to Finance/Disbursement Officer.");
            return "redirect:/dashboard/citizen";
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/dashboard/citizen";
        }
    }

    @PostMapping("/disbursement/milestone/{milestoneId}/request")
    public String requestCompliance(@PathVariable Long milestoneId,
                                    @RequestParam(required = false) String remarks,
                                    @AuthenticationPrincipal UserDetails userDetails,
                                    RedirectAttributes redirectAttributes) {
        if (!isDisbursementOfficer(userDetails)) return "redirect:/dashboard/officer";
        try {
            Long applicationId = planService.getApplicationIdForMilestone(milestoneId);
            requireJurisdiction(applicationId, userDetails);
            planService.requestCompliance(milestoneId, remarks);
            redirectAttributes.addFlashAttribute("success", "Applicant has been requested to submit the milestone compliance document.");
            return "redirect:/milestone3/disbursement/" + applicationId;
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/dashboard/officer";
        }
    }

    @GetMapping("/milestone/{milestoneId}/document")
    public ResponseEntity<byte[]> viewComplianceDocument(@PathVariable Long milestoneId,
                                                         @AuthenticationPrincipal UserDetails userDetails) {
        if (!isDisbursementOfficer(userDetails)) return ResponseEntity.status(403).build();
        try {
            DisbursementMilestone m = planService.getMilestone(milestoneId);
            if (m.getComplianceStoredFilename() == null) return ResponseEntity.notFound().build();
            Long appId = m.getPlan().getApplication().getId();
            requireJurisdiction(appId, userDetails);
            byte[] content = cloudinaryStorage.read(appId, m.getComplianceStoredFilename(), true);
            String f = m.getComplianceStoredFilename().toLowerCase(java.util.Locale.ROOT);
            MediaType media = f.endsWith(".pdf") ? MediaType.APPLICATION_PDF :
                    f.endsWith(".png") ? MediaType.IMAGE_PNG :
                    (f.endsWith(".jpg") || f.endsWith(".jpeg")) ? MediaType.IMAGE_JPEG : MediaType.APPLICATION_OCTET_STREAM;
            String safeName = m.getComplianceOriginalFilename() == null ? "document" :
                    m.getComplianceOriginalFilename().replace("\"", "").replace("\r", "").replace("\n", "");
            return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,
                    "inline; filename=\"" + safeName + "\"").contentType(media).body(content);
        } catch (Exception ex) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/milestone/{milestoneId}/compliance")
    public String submitCompliance(@PathVariable Long milestoneId,
                                   @RequestParam(required = false) String remarks,
                                   @AuthenticationPrincipal UserDetails userDetails,
                                   RedirectAttributes redirectAttributes) {
        if (!isDisbursementOfficer(userDetails)) return "redirect:/dashboard/officer";
        try {
            Long applicationId = planService.getApplicationIdForMilestone(milestoneId);
            requireJurisdiction(applicationId, userDetails);
            planService.submitCompliance(milestoneId, remarks);
            redirectAttributes.addFlashAttribute("success", "Compliance evidence marked as submitted.");
            return "redirect:/milestone3/disbursement/" + applicationId;
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/dashboard/officer";
        }
    }

    @PostMapping("/milestone/{milestoneId}/verify")
    public String verifyCompliance(@PathVariable Long milestoneId,
                                   @RequestParam boolean approve,
                                   @RequestParam(required = false) String remarks,
                                   @AuthenticationPrincipal UserDetails userDetails,
                                   RedirectAttributes redirectAttributes) {
        if (!isDisbursementOfficer(userDetails)) return "redirect:/dashboard/officer";
        try {
            Long applicationId = planService.getApplicationIdForMilestone(milestoneId);
            requireJurisdiction(applicationId, userDetails);
            planService.verifyCompliance(milestoneId, approve, remarks);
            redirectAttributes.addFlashAttribute("success",
                    approve ? "Compliance verified. Milestone is ready for fund release."
                            : "Compliance returned for correction.");
            return "redirect:/milestone3/disbursement/" + applicationId;
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/dashboard/officer";
        }
    }

    @PostMapping("/milestone/{milestoneId}/release")
    public String releaseMilestone(@PathVariable Long milestoneId,
                                   @RequestParam(required = false) String remarks,
                                   @AuthenticationPrincipal UserDetails userDetails,
                                   RedirectAttributes redirectAttributes) {
        if (!isDisbursementOfficer(userDetails)) return "redirect:/dashboard/officer";
        try {
            Long applicationId = planService.getApplicationIdForMilestone(milestoneId);
            requireJurisdiction(applicationId, userDetails);
            planService.releaseMilestone(milestoneId, null, remarks);
//            planService.releaseMilestone(milestoneId, reference, remarks);
            redirectAttributes.addFlashAttribute("success",
                    "Milestone fund release recorded successfully.");
            return "redirect:/milestone3/disbursement/" + applicationId;
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/dashboard/officer";
        }
    }

    @GetMapping("/citizen/milestone/{milestoneId}/receipt")
    public ResponseEntity<byte[]> transactionReceipt(@PathVariable Long milestoneId, @AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) return ResponseEntity.status(401).build();
        try {
            DisbursementMilestone ms=planService.getMilestone(milestoneId);
            SubsidyApplication app=ms.getPlan().getApplication();
            if(app.getUser()==null || !app.getUser().getEmail().equals(userDetails.getUsername())) return ResponseEntity.status(403).build();
            if(ms.getStatus()!=MilestoneStatus.RELEASED) return ResponseEntity.notFound().build();
            com.platform.user.Beneficiary b=app.getUser().getBeneficiary();
            String a=b==null?null:b.getBankAccountNumber();
            String masked=a==null||a.length()<4?"N/A":"XXXXXX"+a.substring(a.length()-4);
            List<String> lines=new ArrayList<>();
            lines.add("Application: SUB-"+app.getId()); lines.add("Beneficiary: "+(b==null?app.getUser().getEmail():b.getFullName()));
            lines.add("Scheme: "+(app.getScheme()==null?"N/A":app.getScheme().getSchemeName()));
            lines.add("Milestone: "+ms.getSequenceNo()+" - "+ms.getMilestoneName());
            lines.add("Amount Released: INR "+String.format(java.util.Locale.US,"%.2f",ms.getReleasedAmount()));
            lines.add("Account: "+masked); lines.add("Account Holder: "+(b==null?"N/A":b.getBankAccountHolderName()));
            lines.add("Bank: "+(b==null?"N/A":b.getBankName())); lines.add("IFSC: "+(b==null?"N/A":b.getBankIfscCode()));
            lines.add("Released At: "+(ms.getReleasedAt()==null?ms.getReleaseDate():ms.getReleasedAt()));
            lines.add("Demo UTR / Transaction Reference: "+ms.getReleaseReference()); lines.add("Status: RELEASED"); lines.add("");
            lines.add("System-generated transaction receipt - simulated disbursement for prototype/demo purposes.");
            return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=transaction-receipt-SUB-"+app.getId()+"-M"+ms.getSequenceNo()+".pdf").contentType(MediaType.APPLICATION_PDF).body(SimplePdfUtil.onePage("SAGAR - Transaction Receipt",lines));
        } catch(RuntimeException ex){ return ResponseEntity.notFound().build(); }
    }

    @PostMapping("/disbursement/{applicationId}/fund-review/flag")
    public String flagFunds(@PathVariable Long applicationId,
                            @RequestParam String reason, @RequestParam String remarks,
                            @AuthenticationPrincipal UserDetails userDetails,
                            RedirectAttributes redirectAttributes) {
        if (!isDisbursementOfficer(userDetails)) return "redirect:/dashboard/officer";
        try {
            requireJurisdiction(applicationId, userDetails);
            planService.raiseFundReviewFlag(applicationId, reason, remarks, userDetails.getUsername());
            redirectAttributes.addFlashAttribute("success", "Fund review flagged and future installments blocked. Sent to Admin review.");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/milestone3/disbursement/" + applicationId;
    }

    @PostMapping("/disbursement/{applicationId}/hold")
    public String holdPlan(@PathVariable Long applicationId,
                           @RequestParam(required = false) String remarks,
                           @AuthenticationPrincipal UserDetails userDetails,
                           RedirectAttributes redirectAttributes) {
        if (!isDisbursementOfficer(userDetails)) return "redirect:/dashboard/officer";
        try {
            requireJurisdiction(applicationId, userDetails);
            planService.holdPlan(applicationId, remarks);
            redirectAttributes.addFlashAttribute("success", "Finance disbursement workflow placed on hold.");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/milestone3/disbursement/" + applicationId;
    }

    @PostMapping("/disbursement/{applicationId}/resume")
    public String resumePlan(@PathVariable Long applicationId,
                             @AuthenticationPrincipal UserDetails userDetails,
                             RedirectAttributes redirectAttributes) {
        if (!isDisbursementOfficer(userDetails)) return "redirect:/dashboard/officer";
        try {
            requireJurisdiction(applicationId, userDetails);
            planService.resumePlan(applicationId);
            redirectAttributes.addFlashAttribute("success", "Finance disbursement workflow resumed.");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/milestone3/disbursement/" + applicationId;
    }

    @PostMapping("/reminders/run")
    public String runReminderCheck(@AuthenticationPrincipal UserDetails userDetails,
                                   RedirectAttributes redirectAttributes) {
        if (!isDisbursementOfficer(userDetails)) return "redirect:/dashboard/officer";
        try {
            int created = planService.runComplianceReminderCheck();
            redirectAttributes.addFlashAttribute("success",
                    created + " compliance reminder(s) processed.");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/milestone3/analytics";
    }

    @GetMapping("/analytics")
    public String analytics(@RequestParam(required = false) Long schemeId,
                            @RequestParam(required = false) Long applicationId,
                            @AuthenticationPrincipal UserDetails userDetails,
                            Model model) {
        if (!isOfficer(userDetails)) return "redirect:/dashboard";

        Long selectedSchemeId = schemeId;
        String selectedSchemeName = null;

        if (applicationId != null) {
            SubsidyApplication selectedApplication = requireJurisdiction(applicationId, userDetails);
            if (selectedApplication.getScheme() != null) {
                selectedSchemeId = selectedApplication.getScheme().getId();
                selectedSchemeName = selectedApplication.getScheme().getSchemeName();
            }
        }

        if (selectedSchemeId != null && selectedSchemeName == null) {
            selectedSchemeName = schemeRepository.findById(selectedSchemeId)
                    .map(Scheme::getSchemeName)
                    .orElse("Selected Scheme");
        }

        model.addAttribute("analytics", analyticsService.buildSnapshot(selectedSchemeId, userDetails));
        model.addAttribute("schemeFiltered", selectedSchemeId != null);
        model.addAttribute("selectedSchemeId", selectedSchemeId);
        model.addAttribute("selectedSchemeName", selectedSchemeName);

        boolean admin = hasAuthority(userDetails, "ROLE_ADMIN");
        model.addAttribute("analyticsBackUrl", admin ? "/admin/dashboard" : "/dashboard/officer");
        model.addAttribute("analyticsBackLabel", admin ? "Admin Dashboard" : "Officer Dashboard");
        model.addAttribute("financeViewer",
                hasAuthority(userDetails, "ROLE_FINANCE_APPROVER")
                        || hasAuthority(userDetails, "ROLE_DISBURSEMENT_OFFICER"));

        return "milestone3-analytics";
    }

    private SubsidyApplication requireJurisdiction(Long applicationId, UserDetails userDetails) {
        SubsidyApplication app = applicationRepository.findDetailedById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found: " + applicationId));
        jurisdictionService.requireAccess(userDetails, app);
        return app;
    }

    private boolean hasAuthority(UserDetails userDetails, String authority) {
        return userDetails != null && userDetails.getAuthorities().stream()
                .anyMatch(a -> authority.equals(a.getAuthority()));
    }

    private boolean isOfficer(UserDetails userDetails) {
        return userDetails != null && userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().matches("ROLE_(FIELD_OFFICER|DISTRICT_OFFICER|FINANCE_APPROVER|DISBURSEMENT_OFFICER|ADMIN)"));
    }

    private boolean isDisbursementOfficer(UserDetails userDetails) {
        // Finance Officer is the primary owner of staged fund release.
        // The old Disbursement Officer role remains accepted for backward compatibility.
        return userDetails != null && userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_FINANCE_APPROVER")
                        || a.getAuthority().equals("ROLE_DISBURSEMENT_OFFICER"));
    }

    private DisbursementPlanForm toForm(DisbursementPlan plan) {
        DisbursementPlanForm form = new DisbursementPlanForm();
        for (DisbursementMilestone m : plan.getMilestones()) {
            DisbursementPlanForm.MilestoneForm row = new DisbursementPlanForm.MilestoneForm();
            row.setName(m.getMilestoneName());
            row.setRequirement(m.getComplianceRequirement());
            row.setDueDate(m.getDueDate() == null ? "" : m.getDueDate().toString());
            row.setDueTime(m.getDueTime() == null ? "10:00" : m.getDueTime().toString());
            row.setScheduledAmount(m.getScheduledAmount());
            form.getMilestones().add(row);
        }
        return form;
    }
}
