package com.platform.disbursement;

import com.platform.audit.AuditLogService;
import com.platform.application.ApplicationRemark;
import com.platform.application.ApplicationRemarkRepository;
import com.platform.application.ApplicationStatus;
import com.platform.application.SubsidyApplication;
import com.platform.application.SubsidyApplicationRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
public class DisbursementPlanService {
    @org.springframework.beans.factory.annotation.Autowired private com.platform.config.CloudinaryStorageService cloudinaryStorage;

    @org.springframework.beans.factory.annotation.Autowired
    private ObjectProvider<JavaMailSender> mailSenderProvider;
    @Value("${sagar.mail.enabled:false}") private boolean citizenMailEnabled;
    @Value("${spring.mail.username:}") private String mailFrom;
    @Value("${sagar.portal.url:http://localhost:8081}") private String portalUrl;

    private final DisbursementPlanRepository planRepository;
    private final ComplianceReminderRepository reminderRepository;
    private final SubsidyApplicationRepository applicationRepository;
    private final DisbursementMilestoneRepository milestoneRepository;
    private final ApplicationRemarkRepository remarkRepository;
    private final AuditLogService auditLogService;

    public DisbursementPlanService(DisbursementPlanRepository planRepository,
                                   ComplianceReminderRepository reminderRepository,
                                   SubsidyApplicationRepository applicationRepository,
                                   DisbursementMilestoneRepository milestoneRepository,
                                   ApplicationRemarkRepository remarkRepository,
                                   AuditLogService auditLogService) {
        this.planRepository = planRepository;
        this.reminderRepository = reminderRepository;
        this.applicationRepository = applicationRepository;
        this.milestoneRepository = milestoneRepository;
        this.remarkRepository = remarkRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public DisbursementPlan getOrCreatePlan(SubsidyApplication application) {
        return planRepository.findByApplicationId(application.getId())
                .orElseGet(() -> {
                    if (application.getApplicationStatus() != ApplicationStatus.FUND_DISBURSEMENT) {
                        throw new IllegalStateException(
                                "A staged disbursement plan can only be created after Finance approval and sanctioning.");
                    }
                    return createDefaultPlan(application);
                });
    }

    @Transactional
    public DisbursementPlan createDefaultPlan(SubsidyApplication application) {
        if (application.getApplicationStatus() != ApplicationStatus.FUND_DISBURSEMENT) {
            throw new IllegalStateException("Finance approval must be completed before a disbursement plan is created.");
        }
        if (application.getSanctionAmount() <= 0) {
            throw new IllegalStateException("A valid sanctioned amount is required before creating a disbursement plan.");
        }

        DisbursementPlan plan = new DisbursementPlan();
        plan.setApplication(application);
        plan.setTotalPlannedAmount(round(application.getSanctionAmount()));
        plan.setStatus(DisbursementPlanStatus.PLANNED);

        LocalDate base = LocalDate.now();
        double total = round(application.getSanctionAmount());

        addMilestone(plan, 1, "Documentation Compliance",
                "Upload the Finance-requested documentation / bank-account confirmation for the first release.",
                base.plusDays(15), round(total * 0.20));
        addMilestone(plan, 2, "Ground Verification Compliance",
                "Upload the requested field / implementation progress evidence for the second release.",
                base.plusDays(30), round(total * 0.40));
        addMilestone(plan, 3, "Utilization Proof Compliance",
                "Upload utilization proof and supporting evidence for the final release.",
                base.plusDays(45), round(total * 0.40));

        // Correct rounding drift so scheduled amounts always equal sanction.
        double scheduled = plan.getMilestones().stream()
                .mapToDouble(DisbursementMilestone::getScheduledAmount)
                .sum();
        double drift = round(total - scheduled);
        if (Math.abs(drift) > 0.001) {
            DisbursementMilestone last = plan.getMilestones().get(plan.getMilestones().size() - 1);
            last.setScheduledAmount(round(last.getScheduledAmount() + drift));
        }

        DisbursementPlan saved = planRepository.save(plan);
        auditLogService.record("DISBURSEMENT_PLAN", saved.getId(), "PLAN_CREATED",
                null, "PLANNED", "Default staged disbursement plan created for the Finance workflow.");
        return saved;
    }

    private void addMilestone(DisbursementPlan plan, int sequence, String name,
                              String requirement, LocalDate dueDate, double amount) {
        DisbursementMilestone milestone = new DisbursementMilestone();
        milestone.setPlan(plan);
        milestone.setSequenceNo(sequence);
        milestone.setMilestoneName(name);
        milestone.setComplianceRequirement(requirement);
        milestone.setDueDate(dueDate);
        milestone.setDueTime(LocalTime.of(10, 0));
        milestone.setScheduledAmount(round(amount));
        milestone.setStatus(MilestoneStatus.PENDING);
        plan.getMilestones().add(milestone);
    }

    @Transactional
    public DisbursementPlan updatePlan(Long applicationId, List<MilestoneInput> inputs) {
        SubsidyApplication app = getApplication(applicationId);
        if (app.getApplicationStatus() != ApplicationStatus.FUND_DISBURSEMENT) {
            throw new IllegalStateException(
                    "The application must remain in Fund Disbursement stage while its schedule is configured.");
        }

        DisbursementPlan plan = getOrCreatePlan(app);
        if (inputs == null || inputs.isEmpty()) {
            throw new IllegalArgumentException("At least one disbursement milestone is required.");
        }

        double total = inputs.stream().mapToDouble(MilestoneInput::scheduledAmount).sum();
        if (Math.abs(total - app.getSanctionAmount()) > 0.01) {
            throw new IllegalArgumentException(
                    "Milestone amounts must total the sanctioned amount of ₹" + format(app.getSanctionAmount()) + ".");
        }

        for (MilestoneInput input : inputs) {
            if (input.name() == null || input.name().isBlank()) {
                throw new IllegalArgumentException("Every milestone must have a name.");
            }
            if (input.dueDate() == null) {
                throw new IllegalArgumentException("Every milestone must have a due date.");
            }
            if (input.scheduledAmount() <= 0) {
                throw new IllegalArgumentException("Every milestone must have an amount greater than ₹0.");
            }
        }

        List<DisbursementMilestone> existing = new ArrayList<>(plan.getMilestones());
        existing.sort(Comparator.comparingInt(DisbursementMilestone::getSequenceNo));

        boolean anyReleased = existing.stream().anyMatch(m -> m.getStatus() == MilestoneStatus.RELEASED);
        if (inputs.size() != existing.size()) {
            if (anyReleased) {
                throw new IllegalStateException(
                        "Milestone count cannot be changed after a fund release has been recorded.");
            }
            if (existing.stream().anyMatch(m -> m.getId() != null && reminderRepository.existsByMilestoneId(m.getId()))) {
                throw new IllegalStateException(
                        "Milestone count cannot be changed after compliance reminders have been recorded.");
            }
            plan.getMilestones().clear();
            for (int i = 0; i < inputs.size(); i++) {
                MilestoneInput in = inputs.get(i);
                validateNewDueDate(in.dueDate());
                addMilestone(plan, i + 1, in.name().trim(), safe(in.requirement()),
                        in.dueDate(), in.scheduledAmount());
            }
        } else {
            for (int i = 0; i < existing.size(); i++) {
                DisbursementMilestone milestone = existing.get(i);
                MilestoneInput input = inputs.get(i);

                if (milestone.getStatus() == MilestoneStatus.RELEASED) {
                    if (!sameMoney(milestone.getScheduledAmount(), input.scheduledAmount())
                            || !safe(milestone.getMilestoneName()).equals(input.name().trim())
                            || !safe(milestone.getComplianceRequirement()).equals(safe(input.requirement()))
                            || !sameDate(milestone.getDueDate(), input.dueDate())) {
                        throw new IllegalStateException(
                                "Released milestone " + (i + 1) + " is locked and cannot be edited.");
                    }
                    continue;
                }

                if (milestone.getReleasedAmount() > input.scheduledAmount() + 0.01) {
                    throw new IllegalArgumentException(
                            "Scheduled amount cannot be reduced below the amount already released for milestone " + (i + 1) + ".");
                }

                if (milestone.getDueDate() != null && input.dueDate().isBefore(LocalDate.now())
                        && !input.dueDate().equals(milestone.getDueDate())) {
                    throw new IllegalArgumentException(
                            "A new milestone due date cannot be set in the past.");
                }

                milestone.setMilestoneName(input.name().trim());
                milestone.setComplianceRequirement(safe(input.requirement()));
                milestone.setDueDate(input.dueDate());
                milestone.setDueTime(LocalTime.of(10, 0));
                milestone.setScheduledAmount(round(input.scheduledAmount()));
            }
        }

        plan.setTotalPlannedAmount(round(total));
        plan.setUpdatedAt(LocalDateTime.now());
        refreshPlanStatus(plan);
        DisbursementPlan saved = planRepository.save(plan);
        auditLogService.record("DISBURSEMENT_PLAN", saved.getId(), "PLAN_UPDATED",
                null, saved.getStatus().name(), "Finance Officer updated the staged disbursement schedule.");
        return saved;
    }

    @Transactional
    public void raiseFundReviewFlag(Long applicationId, String reason, String remarks, String officerEmail) {
        SubsidyApplication app = getApplication(applicationId);
        DisbursementPlan plan = getOrCreatePlan(app);
        if (plan.isFundReviewFlagged()) throw new IllegalStateException("A fund review flag is already active.");
        if (plan.getReleasedAmount() <= 0) throw new IllegalStateException("Fund review requires a previously released installment.");
        java.util.Set<String> reasons = java.util.Set.of("MISSING_PROOF", "INVALID_PROOF", "SUSPECTED_MISUSE", "OTHER");
        if (reason == null || !reasons.contains(reason)) throw new IllegalArgumentException("Select a valid flag reason.");
        if (remarks == null || remarks.isBlank()) throw new IllegalArgumentException("Officer remarks are required.");
        plan.setFundReviewFlagged(true);
        plan.setFundReviewReason(reason);
        plan.setFundReviewRemarks(remarks.trim().substring(0, Math.min(2000, remarks.trim().length())));
        plan.setFundReviewRaisedBy(officerEmail);
        plan.setFundReviewRaisedAt(LocalDateTime.now());
        plan.setFundReviewClearedBy(null);
        plan.setFundReviewClearedAt(null);
        plan.setFundReviewResolution(null);
        plan.setStatus(DisbursementPlanStatus.ON_HOLD);
        planRepository.save(plan);
        auditLogService.record("FUND_REVIEW", plan.getId(), "FLAG_RAISED", "NONE", reason,
                "Application SUB-" + applicationId + "; " + remarks.trim());
    }

    @Transactional
    public void clearFundReviewFlag(Long planId, String resolution, String adminEmail) {
        DisbursementPlan plan = planRepository.findById(planId)
                .orElseThrow(() -> new IllegalArgumentException("Disbursement plan not found."));
        if (!plan.isFundReviewFlagged()) throw new IllegalStateException("This case has no active flag.");
        if (resolution == null || resolution.isBlank()) throw new IllegalArgumentException("A resolution reason is required.");
        plan.setFundReviewFlagged(false);
        plan.setFundReviewClearedBy(adminEmail);
        plan.setFundReviewClearedAt(LocalDateTime.now());
        plan.setFundReviewResolution(resolution.trim().substring(0, Math.min(2000, resolution.trim().length())));
        // Keep ON_HOLD: clearing a flag must never authorize automatic payment.
        plan.setStatus(DisbursementPlanStatus.ON_HOLD);
        planRepository.save(plan);
        auditLogService.record("FUND_REVIEW", plan.getId(), "FLAG_CLEARED", "ACTIVE", "CLEARED",
                "Application SUB-" + plan.getApplication().getId() + "; " + resolution.trim());
    }

    @Transactional(readOnly = true)
    public List<DisbursementPlan> getFlaggedPlans() {
        return planRepository.findByFundReviewFlaggedTrueOrderByFundReviewRaisedAtDesc();
    }

    @Transactional
    public void holdPlan(Long applicationId, String remarks) {
        SubsidyApplication app = getApplication(applicationId);
        if (app.getApplicationStatus() != ApplicationStatus.FUND_DISBURSEMENT) {
            throw new IllegalStateException("Only applications awaiting fund disbursement can be placed on hold.");
        }
        DisbursementPlan plan = getOrCreatePlan(app);
        if (plan.getStatus() == DisbursementPlanStatus.COMPLETED) {
            throw new IllegalStateException("A completed disbursement plan cannot be placed on hold.");
        }
        if (plan.getStatus() == DisbursementPlanStatus.ON_HOLD) {
            throw new IllegalStateException("This disbursement plan is already on hold.");
        }
        plan.setStatus(DisbursementPlanStatus.ON_HOLD);
        plan.setUpdatedAt(LocalDateTime.now());
        planRepository.save(plan);
        String note = nonBlank(remarks, "Fund release placed on hold by Finance Officer.");
        addAuditRemark(app, "FUND_DISBURSEMENT", "FINANCE_APPROVER", "DISBURSEMENT_HOLD", note);
        auditLogService.record("DISBURSEMENT_PLAN", plan.getId(), "DISBURSEMENT_HOLD",
                "ACTIVE", "ON_HOLD", note);
    }

    @Transactional
    public void resumePlan(Long applicationId) {
        SubsidyApplication app = getApplication(applicationId);
        if (app.getApplicationStatus() != ApplicationStatus.FUND_DISBURSEMENT) {
            throw new IllegalStateException("The application must be in Fund Disbursement stage to resume the plan.");
        }
        DisbursementPlan plan = getOrCreatePlan(app);
        if (plan.getStatus() != DisbursementPlanStatus.ON_HOLD) {
            throw new IllegalStateException("This disbursement plan is not currently on hold.");
        }
        if (plan.isFundReviewFlagged()) throw new IllegalStateException("Admin must clear the active fund review flag before resuming.");
        refreshPlanStatus(plan);
        plan.setUpdatedAt(LocalDateTime.now());
        planRepository.save(plan);
        String note = "Fund release workflow resumed by Finance Officer.";
        addAuditRemark(app, "FUND_DISBURSEMENT", "FINANCE_APPROVER", "DISBURSEMENT_RESUMED", note);
        auditLogService.record("DISBURSEMENT_PLAN", plan.getId(), "DISBURSEMENT_RESUMED",
                "ON_HOLD", plan.getStatus().name(), note);
    }

    @Transactional
    public void requestCompliance(Long milestoneId, String remarks) {
        DisbursementMilestone milestone = getMilestone(milestoneId);
        ensurePlanActive(milestone.getPlan());
        if (milestone.getStatus() == MilestoneStatus.RELEASED) {
            throw new IllegalStateException("This milestone has already been released.");
        }
        milestone.setComplianceRequested(true);
        milestone.setComplianceRequestRemarks(safe(remarks));
        milestone.setComplianceRequestedAt(LocalDateTime.now());
        milestone.getPlan().setUpdatedAt(LocalDateTime.now());
        planRepository.save(milestone.getPlan());
        if (!reminderRepository.existsByMilestoneIdAndReminderType(milestone.getId(), ReminderType.REQUESTED)) {
            ComplianceReminder r = new ComplianceReminder();
            r.setMilestone(milestone); r.setReminderType(ReminderType.REQUESTED); r.setSentAt(LocalDateTime.now());
            r.setMessage("Finance requested a compliance document for " + milestone.getMilestoneName()
                    + ". Due " + milestone.getDueDate() + " at " + (milestone.getDueTime()==null?LocalTime.of(10,0):milestone.getDueTime()) + ".");
            reminderRepository.save(r);
            milestone.setLastReminderAt(r.getSentAt()); milestone.setLastReminderType(ReminderType.REQUESTED.name());
            planRepository.save(milestone.getPlan());
        }
        String note = nonBlank(remarks, "Finance Officer requested the applicant's compliance document.");
        addAuditRemark(milestone.getPlan().getApplication(), "FUND_DISBURSEMENT", "FINANCE_APPROVER", "COMPLIANCE_REQUESTED", note);
        auditLogService.record("DISBURSEMENT_MILESTONE", milestone.getId(), "COMPLIANCE_REQUESTED",
                milestone.getStatus().name(), milestone.getStatus().name(), note);
    }

    @Transactional
    public void submitApplicantCompliance(Long milestoneId, MultipartFile file, String remarks, Long applicantUserId) {
        DisbursementMilestone milestone = getMilestone(milestoneId);
        SubsidyApplication app = milestone.getPlan().getApplication();
        if (app.getUser() == null || !app.getUser().getId().equals(applicantUserId)) {
            throw new IllegalArgumentException("You are not authorized to update this application.");
        }
        ensurePlanActive(milestone.getPlan());
        ensurePreviousMilestoneReleased(milestone);
        if (!milestone.isComplianceRequested()) {
            throw new IllegalStateException("The Finance Officer has not requested this compliance document yet.");
        }
        if (milestone.getStatus() == MilestoneStatus.RELEASED) {
            throw new IllegalStateException("This milestone has already been released.");
        }
        if (milestone.getStatus() == MilestoneStatus.COMPLIANCE_SUBMITTED) {
            throw new IllegalStateException("Compliance has already been submitted and is awaiting verification.");
        }
        if (milestone.getStatus() == MilestoneStatus.COMPLIANCE_VERIFIED) {
            throw new IllegalStateException("Compliance has already been verified for this milestone.");
        }
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Please select a compliance document to upload.");
        }

        String original = file.getOriginalFilename() == null ? "compliance-document" : file.getOriginalFilename();
        String safeOriginal = org.springframework.util.StringUtils.cleanPath(original).replaceAll("[^a-zA-Z0-9._-]", "_");
        try {
            String stored = "milestone_" + milestone.getSequenceNo() + "_" + System.currentTimeMillis() + "_" + safeOriginal;
            cloudinaryStorage.upload(app.getId(), stored, file, true);
            milestone.setComplianceOriginalFilename(original);
            milestone.setComplianceStoredFilename(stored);
            milestone.setComplianceUploadedAt(LocalDateTime.now());
            milestone.setComplianceRemarks(safe(remarks));
            milestone.setStatus(MilestoneStatus.COMPLIANCE_SUBMITTED);
            milestone.setComplianceSubmittedDate(LocalDate.now());
            milestone.getPlan().setUpdatedAt(LocalDateTime.now());
            planRepository.save(milestone.getPlan());
            String note = nonBlank(remarks, "Applicant submitted the requested compliance document.");
            addAuditRemark(app, "FUND_DISBURSEMENT", "BENEFICIARY", "COMPLIANCE_SUBMITTED", note);
            auditLogService.recordAs("BENEFICIARY", "BENEFICIARY", "DISBURSEMENT_MILESTONE", milestone.getId(),
                    "COMPLIANCE_SUBMITTED", "REQUESTED", "COMPLIANCE_SUBMITTED", note);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not save the compliance document: " + ex.getMessage(), ex);
        }
    }

    @Transactional
    public void submitCompliance(Long milestoneId, String remarks) {
        DisbursementMilestone milestone = getMilestone(milestoneId);
        if (milestone.getStatus() == MilestoneStatus.RELEASED) {
            throw new IllegalStateException("This milestone has already been released.");
        }
        if (milestone.getStatus() == MilestoneStatus.COMPLIANCE_SUBMITTED) {
            throw new IllegalStateException("Compliance has already been submitted and is awaiting verification.");
        }
        if (milestone.getStatus() == MilestoneStatus.COMPLIANCE_VERIFIED) {
            throw new IllegalStateException("Verified compliance cannot be submitted again.");
        }

        ensurePlanActive(milestone.getPlan());
        ensurePreviousMilestoneReleased(milestone);

        milestone.setStatus(MilestoneStatus.COMPLIANCE_SUBMITTED);
        milestone.setComplianceSubmittedDate(LocalDate.now());
        milestone.setComplianceRemarks(safe(remarks));
        milestone.getPlan().setUpdatedAt(LocalDateTime.now());
        planRepository.save(milestone.getPlan());

        String note = nonBlank(remarks, "Compliance evidence marked as submitted for verification.");
        addAuditRemark(milestone.getPlan().getApplication(), "FUND_DISBURSEMENT",
                "FINANCE_APPROVER", "COMPLIANCE_SUBMITTED", note);
        auditLogService.record("DISBURSEMENT_MILESTONE", milestone.getId(), "COMPLIANCE_SUBMITTED",
                "PENDING", "COMPLIANCE_SUBMITTED", note);
    }

    @Transactional
    public void verifyCompliance(Long milestoneId, boolean approve, String remarks) {
        DisbursementMilestone milestone = getMilestone(milestoneId);
        ensurePlanActive(milestone.getPlan());
        if (milestone.getStatus() != MilestoneStatus.COMPLIANCE_SUBMITTED) {
            throw new IllegalStateException("Compliance must be submitted before it can be verified.");
        }

        milestone.setComplianceRemarks(safe(remarks));
        if (approve) {
            milestone.setStatus(MilestoneStatus.COMPLIANCE_VERIFIED);
            milestone.setComplianceVerifiedDate(LocalDate.now());
        } else {
            milestone.setStatus(milestone.isOverdue() ? MilestoneStatus.OVERDUE : MilestoneStatus.PENDING);
            milestone.setComplianceVerifiedDate(null);
        }
        milestone.getPlan().setUpdatedAt(LocalDateTime.now());
        planRepository.save(milestone.getPlan());

        String note = nonBlank(remarks, approve
                ? "Compliance verified by Finance Officer."
                : "Compliance returned for correction or additional evidence.");
        addAuditRemark(milestone.getPlan().getApplication(), "FUND_DISBURSEMENT",
                "FINANCE_APPROVER", approve ? "COMPLIANCE_VERIFIED" : "COMPLIANCE_RETURNED", note);
        auditLogService.record("DISBURSEMENT_MILESTONE", milestone.getId(),
                approve ? "COMPLIANCE_VERIFIED" : "COMPLIANCE_RETURNED",
                "COMPLIANCE_SUBMITTED", milestone.getStatus().name(), note);
    }

    @Transactional
    public void releaseMilestone(Long milestoneId, String reference, String remarks) {
        DisbursementMilestone milestone = getMilestone(milestoneId);
        DisbursementPlan plan = milestone.getPlan();
        ensurePlanActive(plan);

        if (milestone.getStatus() != MilestoneStatus.COMPLIANCE_VERIFIED) {
            throw new IllegalStateException(
                    "Compliance must be verified before funds can be released for this milestone.");
        }
        ensurePreviousMilestoneReleased(milestone);

        SubsidyApplication paymentApp = plan.getApplication();
        if (!paymentApp.isPaymentBankDetailsVerified()
                || paymentApp.getPaymentAccountNumber() == null
                || paymentApp.getPaymentIfscCode() == null)
            throw new IllegalStateException("Finance-verified bank details for this application are required before fund release.");
        String cleanReference = generateDemoUtr(plan.getApplication().getId(), milestone.getSequenceNo());
        if (milestoneRepository.existsByReleaseReferenceIgnoreCase(cleanReference)) {
            throw new IllegalArgumentException("This release transaction reference has already been recorded.");
        }

        milestone.setReleasedAmount(round(milestone.getScheduledAmount()));
        milestone.setReleaseDate(LocalDate.now());
        milestone.setReleasedAt(LocalDateTime.now());
        milestone.setReleaseReference(cleanReference);
        milestone.setReleaseRemarks(safe(remarks));
        milestone.setStatus(MilestoneStatus.RELEASED);

        refreshPlanStatus(plan);

        SubsidyApplication app = plan.getApplication();
        if (plan.getStatus() == DisbursementPlanStatus.COMPLETED) {
            app.setApplicationStatus(ApplicationStatus.APPROVED);
            app.setUtrNumber(cleanReference);
            app.setDisbursedAt(LocalDateTime.now());
            app.setDisbursementRemarks(
                    "Staged disbursement completed across all compliance milestones.");
            applicationRepository.save(app);
        }

        plan.setUpdatedAt(LocalDateTime.now());
        planRepository.save(plan);

        String note = nonBlank(remarks,
                "Milestone " + milestone.getSequenceNo() + " released. Transaction reference: " + cleanReference + ".");
        addAuditRemark(app, "FUND_DISBURSEMENT", "FINANCE_APPROVER", "MILESTONE_RELEASED", note);
        auditLogService.record("DISBURSEMENT_MILESTONE", milestone.getId(), "MILESTONE_RELEASED",
                "COMPLIANCE_VERIFIED", "RELEASED", note + " Reference: " + cleanReference);
    }

    @Transactional(readOnly = true)
    public Long getApplicationIdForMilestone(Long id) {
        return getMilestone(id).getPlan().getApplication().getId();
    }

    @Transactional(readOnly = true)
    public DisbursementMilestone getMilestone(Long id) {
        return milestoneRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Milestone not found: " + id));
    }

    /** Automatic compliance reminder engine. */
    @Scheduled(cron = "0 * * * * *")
    @Transactional
    public void automateComplianceReminders() { processComplianceReminders(); }

    @Transactional
    public int runComplianceReminderCheck() { return processComplianceReminders(); }

    @Transactional
    protected int processComplianceReminders() {
        LocalDateTime now=LocalDateTime.now(); LocalDate today=now.toLocalDate(); int created=0;
        for (DisbursementPlan plan:planRepository.findAll()) {
            boolean changed=false;
            // Iterate over a defensive snapshot. releaseMilestone(...) can change the
            // Hibernate-managed milestone collection while this reminder scan is running.
            for (DisbursementMilestone m : new ArrayList<>(plan.getMilestones())) {
                if (m.getStatus()==MilestoneStatus.RELEASED || m.getDueDate()==null) continue;
                LocalDateTime due=LocalDateTime.of(m.getDueDate(),m.getDueTime()==null?LocalTime.of(10,0):m.getDueTime());

                if (m.getStatus()==MilestoneStatus.COMPLIANCE_VERIFIED && !now.isBefore(due)) {
                    try {
                        releaseMilestone(m.getId(),"AUTO-SUB-"+plan.getApplication().getId()+"-M"+m.getSequenceNo()+"-"+today.toString().replace("-",""),
                                "Automatically released on the scheduled due date after compliance verification.");
                        continue;
                    } catch(RuntimeException ignored) {}
                }

                boolean submitted=m.getComplianceOriginalFilename()!=null
                        || m.getStatus()==MilestoneStatus.COMPLIANCE_SUBMITTED
                        || m.getStatus()==MilestoneStatus.COMPLIANCE_VERIFIED;
                if (!m.isComplianceRequested() || submitted) continue;

                ReminderType type=null; String msg=null;
                if (now.getHour()==9 && !today.isAfter(m.getDueDate()) && !today.isBefore(m.getDueDate().minusDays(3))) {
                    LocalDateTime from=today.atStartOfDay(), to=from.plusDays(1).minusNanos(1);
                    if (!reminderRepository.existsByMilestoneIdAndReminderTypeAndSentAtBetween(m.getId(),ReminderType.DUE_SOON,from,to)) {
                        type=ReminderType.DUE_SOON;
                        msg="Reminder: requested compliance document for "+m.getMilestoneName()+" is due on "+m.getDueDate()+" at "+(m.getDueTime()==null?LocalTime.of(10,0):m.getDueTime())+".";
                    }
                }
                if (type==null && !now.isBefore(due.minusMinutes(30)) && now.isBefore(due)
                        && !reminderRepository.existsByMilestoneIdAndReminderType(m.getId(),ReminderType.DUE_30_MIN)) {
                    type=ReminderType.DUE_30_MIN; msg="Urgent reminder: requested compliance document for "+m.getMilestoneName()+" is due within 30 minutes.";
                }
                if (type==null && now.isAfter(due) && !reminderRepository.existsByMilestoneIdAndReminderType(m.getId(),ReminderType.OVERDUE)) {
                    type=ReminderType.OVERDUE; msg="Requested compliance document for "+m.getMilestoneName()+" is overdue and requires immediate attention.";
                    if (m.getStatus()!=MilestoneStatus.COMPLIANCE_SUBMITTED && m.getStatus()!=MilestoneStatus.COMPLIANCE_VERIFIED) { m.setStatus(MilestoneStatus.OVERDUE); changed=true; }
                }
                if (type!=null) {
                    ComplianceReminder r=new ComplianceReminder(); r.setMilestone(m); r.setReminderType(type); r.setSentAt(now); r.setMessage(msg); reminderRepository.save(r); created++;
                    m.setLastReminderAt(now); m.setLastReminderType(type.name()); changed=true;
                    addAuditRemark(plan.getApplication(),"FUND_DISBURSEMENT","SYSTEM",type==ReminderType.OVERDUE?"COMPLIANCE_OVERDUE":"COMPLIANCE_REMINDER",msg);
                    auditLogService.recordAs("SYSTEM","SYSTEM","DISBURSEMENT_MILESTONE",m.getId(),type==ReminderType.OVERDUE?"COMPLIANCE_OVERDUE":"COMPLIANCE_REMINDER",null,type.name(),msg);
                }
            }
            if (changed) { refreshPlanStatus(plan); plan.setUpdatedAt(LocalDateTime.now()); planRepository.save(plan); }
        }
        return created;
    }

    /** Retries pending citizen email notices without duplicating successfully sent mail. */
    @Scheduled(cron = "0 */5 * * * *")
    @Transactional
    public void deliverPendingCitizenEmails() {
        if (!citizenMailEnabled || mailFrom.isBlank()) return;
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null) return;
        LocalDateTime now = LocalDateTime.now();
        for (ComplianceReminder reminder : reminderRepository.findTop100ByEmailSentAtIsNullOrderBySentAtAsc()) {
            if (reminder.getEmailLastAttemptAt() != null
                    && reminder.getEmailLastAttemptAt().isAfter(now.minusMinutes(5))) continue;
            String recipient = reminder.getMilestone().getPlan().getApplication().getUser().getEmail();
            if (recipient == null || recipient.isBlank()) continue;
            reminder.setEmailLastAttemptAt(now);
            reminder.setEmailAttempts(reminder.getEmailAttempts() + 1);
            try {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setFrom(mailFrom);
                message.setTo(recipient);
                message.setSubject("SAGAR - Action required for application #" +
                        reminder.getMilestone().getPlan().getApplication().getId());
                message.setText("Dear Citizen,\n\n" + reminder.getMessage() +
                        "\n\nApplication: SUB-" + reminder.getMilestone().getPlan().getApplication().getId() +
                        "\nPlease sign in to SAGAR: " + portalUrl + "/dashboard/citizen" +
                        "\n\nFor security, do not email identity or banking documents in reply.\nSAGAR Team");
                sender.send(message);
                reminder.setEmailSentAt(LocalDateTime.now());
            } catch (MailException ex) {
                // Keep pending for retry; never interrupt disbursement or compliance workflows.
                System.err.println("SAGAR citizen email delivery failed for reminder " + reminder.getId() + ": " + ex.getClass().getSimpleName());
            }
            reminderRepository.save(reminder);
        }
    }

    @Transactional(readOnly = true)
    public List<ComplianceReminder> getReminderHistoryForPlan(Long planId) {
        return reminderRepository.findByMilestonePlanIdOrderBySentAtDesc(planId);
    }

    private void ensurePlanActive(DisbursementPlan plan) {
        if (plan.isFundReviewFlagged()) throw new IllegalStateException("Active fund review flag blocks further installment actions.");
        if (plan.getStatus() == DisbursementPlanStatus.ON_HOLD) {
            throw new IllegalStateException("The disbursement plan is on hold. Resume the Finance workflow before continuing.");
        }
    }

    private void ensurePreviousMilestoneReleased(DisbursementMilestone milestone) {
        List<DisbursementMilestone> milestones = new ArrayList<>(milestone.getPlan().getMilestones());
        milestones.sort(Comparator.comparingInt(DisbursementMilestone::getSequenceNo));
        for (DisbursementMilestone previous : milestones) {
            if (previous.getSequenceNo() < milestone.getSequenceNo()
                    && previous.getStatus() != MilestoneStatus.RELEASED) {
                throw new IllegalStateException(
                        "Milestone " + milestone.getSequenceNo()
                                + " cannot proceed until milestone " + previous.getSequenceNo() + " is released.");
            }
        }
    }

    private void refreshPlanStatus(DisbursementPlan plan) {
        if (plan.isFundReviewFlagged()) { plan.setStatus(DisbursementPlanStatus.ON_HOLD); return; }
        if (plan.getMilestones().isEmpty()) {
            plan.setStatus(DisbursementPlanStatus.PLANNED);
            return;
        }

        boolean allReleased = plan.getMilestones().stream()
                .allMatch(m -> m.getStatus() == MilestoneStatus.RELEASED);
        boolean anyReleased = plan.getMilestones().stream()
                .anyMatch(m -> m.getStatus() == MilestoneStatus.RELEASED);
        boolean anyHold = plan.getMilestones().stream()
                .anyMatch(m -> m.getStatus() == MilestoneStatus.ON_HOLD);

        if (allReleased) plan.setStatus(DisbursementPlanStatus.COMPLETED);
        else if (anyHold) plan.setStatus(DisbursementPlanStatus.ON_HOLD);
        else if (anyReleased) plan.setStatus(DisbursementPlanStatus.IN_PROGRESS);
        else plan.setStatus(DisbursementPlanStatus.PLANNED);
    }

    private SubsidyApplication getApplication(Long applicationId) {
        return applicationRepository.findDetailedById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found: " + applicationId));
    }

    private void validateNewDueDate(LocalDate dueDate) {
        if (dueDate.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("A new milestone due date cannot be in the past.");
        }
    }

    private boolean sameMoney(double a, double b) {
        return Math.abs(a - b) <= 0.01;
    }

    private boolean sameDate(LocalDate a, LocalDate b) {
        return a == null ? b == null : a.equals(b);
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String nonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private void addAuditRemark(SubsidyApplication app, String stage, String actorRole,
                                String action, String remarks) {
        ApplicationRemark entry = new ApplicationRemark();
        entry.setApplication(app);
        entry.setStage(stage);
        entry.setActorRole(actorRole);
        entry.setAction(action);
        entry.setRemarks(nonBlank(remarks, "No remarks provided."));
        entry.setVisibleToApplicant(true);
        entry.setCreatedAt(LocalDateTime.now());
        remarkRepository.save(entry);
    }

    private String generateDemoUtr(Long appId,int seq) {
        return "SAGAR-SUB"+appId+"-M"+seq+"-"+LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private String format(double value) {
        return String.format("%,.2f", value);
    }

    public record MilestoneInput(String name, String requirement, LocalDate dueDate, LocalTime dueTime, double scheduledAmount) {}
}
