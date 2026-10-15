package com.platform.application;

import com.platform.scheme.Scheme;
import com.platform.audit.AuditLogService;
import com.platform.scheme.SchemeRepository;
import com.platform.user.Beneficiary;
import com.platform.util.DocumentKeyUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

@Service
public class EligibilityService {

    public static final int COOLING_PERIOD_DAYS = 30;
    public static final int MAX_REJECTIONS_BEFORE_COOLING = 3;

    @Autowired private ObjectProvider<JavaMailSender> mailSenderProvider;
    @Value("${sagar.mail.enabled:false}") private boolean citizenMailEnabled;
    @Value("${spring.mail.username:}") private String mailFrom;
    @Value("${sagar.portal.url:http://localhost:8081}") private String portalUrl;
    @Autowired private SchemeRepository schemeRepository;
    @Autowired private SubsidyApplicationRepository applicationRepository;
    @Autowired private ApplicationRemarkRepository remarkRepository;
    @Autowired private ApplicationDocumentRepository documentRepository;
    @Autowired private AuditLogService auditLogService;

    /**
     * Calculates exactly the same score used by the live browser preview.
     *
     * 50 profile points:
     * age 10, income 15, identity 5, occupation 5, gender 5,
     * disability 5, residence 5.
     *
     * 40 points are distributed equally over the scheme's mandatory documents.
     * 10 points are distributed equally over the scheme's optional documents.
     */
    public SchemeEligibilityScore calculateLiveScore(Beneficiary beneficiary,
                                                     Scheme scheme,
                                                     Set<String> uploadedDocumentKeys) {
        Set<String> uploaded = uploadedDocumentKeys == null
                ? new HashSet<>()
                : new HashSet<>(uploadedDocumentKeys);

        SchemeEligibilityScore result = new SchemeEligibilityScore();
        result.setSchemeName(scheme.getSchemeName());

        boolean ageEligible = beneficiary != null
                && beneficiary.getAge() >= scheme.getEligibleAgeMin()
                && beneficiary.getAge() <= scheme.getEligibleAgeMax();
        boolean incomeEligible = beneficiary != null
                && beneficiary.getIncome() >= 0
                && beneficiary.getIncome() <= scheme.getMaxIncomeThreshold();
        boolean identityOk = beneficiary != null
                && beneficiary.getGovernmentId() != null
                && !beneficiary.getGovernmentId().isBlank();

        boolean occupationOk = true;
        boolean genderOk = true;
        boolean disabilityOk = true;
        boolean residenceOk = true;

        if (scheme.isOccupationRestricted()) {
            String occupation = beneficiary != null && beneficiary.getOccupation() != null
                    ? beneficiary.getOccupation().trim() : "";
            occupationOk = java.util.Arrays.stream(scheme.getRequiredOccupation().split(","))
                    .map(String::trim)
                    .anyMatch(value -> value.equalsIgnoreCase(occupation));
        }

        if (scheme.isGenderRestricted()) {
            genderOk = beneficiary != null
                    && beneficiary.getGender() != null
                    && beneficiary.getGender().trim().equalsIgnoreCase(scheme.getRequiredGender());
        }

        if (scheme.isRequiresDisability()) {
            disabilityOk = beneficiary != null
                    && "Yes".equalsIgnoreCase(beneficiary.getDisabilityStatus());
        }

        if (scheme.isResidenceRestricted()) {
            residenceOk = beneficiary != null
                    && beneficiary.getResidenceType() != null
                    && beneficiary.getResidenceType().trim().equalsIgnoreCase(scheme.getRequiredResidenceType());
        }

        result.setAgePass(ageEligible);
        result.setIncomePass(incomeEligible);
        result.setIdentityPass(identityOk);
        result.setOccupationPass(occupationOk);
        result.setGenderPass(genderOk);
        result.setDisabilityPass(disabilityOk);
        result.setResidencePass(residenceOk);

        LinkedHashMap<String, String> required = DocumentKeyUtil.parse(scheme.getRequiredDocuments());
        LinkedHashMap<String, String> optional = DocumentKeyUtil.parse(scheme.getOptionalDocuments());

        List<String> missing = new ArrayList<>();
        List<String> uploadedNames = new ArrayList<>();
        Map<String, Integer> documentPoints = new LinkedHashMap<>();

        int requiredUploaded = 0;
        for (Map.Entry<String, String> entry : required.entrySet()) {
            if (uploaded.contains(entry.getKey())) {
                requiredUploaded++;
                uploadedNames.add(entry.getValue());
            } else {
                missing.add(entry.getValue());
            }
        }

        int optionalUploaded = 0;
        for (Map.Entry<String, String> entry : optional.entrySet()) {
            if (uploaded.contains(entry.getKey())) {
                optionalUploaded++;
                uploadedNames.add(entry.getValue());
            }
        }

        int profilePts = 0;
        if (ageEligible) profilePts += 10;
        if (incomeEligible) profilePts += 15;
        if (identityOk) profilePts += 5;
        if (occupationOk) profilePts += 5;
        if (genderOk) profilePts += 5;
        if (disabilityOk) profilePts += 5;
        if (residenceOk) profilePts += 5;

        int mandatoryPts = required.isEmpty()
                ? 0
                : (int) Math.round(requiredUploaded * 40.0 / required.size());
        int optionalPts = optional.isEmpty()
                ? 0
                : (int) Math.round(optionalUploaded * 10.0 / optional.size());

        for (Map.Entry<String, String> entry : required.entrySet()) {
            if (uploaded.contains(entry.getKey())) {
                documentPoints.put(entry.getValue(), required.isEmpty() ? 0 : Math.max(1, (int) Math.round(40.0 / required.size())));
            }
        }
        for (Map.Entry<String, String> entry : optional.entrySet()) {
            if (uploaded.contains(entry.getKey())) {
                documentPoints.put(entry.getValue(), optional.isEmpty() ? 0 : Math.max(1, (int) Math.round(10.0 / optional.size())));
            }
        }

        boolean mandatoryDocumentsComplete = missing.isEmpty();
        boolean profileEligible = ageEligible && incomeEligible && identityOk
                && occupationOk && genderOk && disabilityOk && residenceOk;
        boolean fullyEligible = profileEligible && mandatoryDocumentsComplete;

        result.setMandatoryDocumentsComplete(mandatoryDocumentsComplete);
        result.setUploadedMandatoryDocuments(requiredUploaded);
        result.setTotalMandatoryDocuments(required.size());
        result.setUploadedOptionalDocuments(optionalUploaded);
        result.setTotalOptionalDocuments(optional.size());
        result.setProfilePts(profilePts);
        result.setMandatoryDocumentPts(mandatoryPts);
        result.setOptionalDocumentPts(optionalPts);
        result.setTotalScore(Math.min(100, profilePts + mandatoryPts + optionalPts));
        result.setEligible(fullyEligible);
        result.setMissingRequiredDocuments(missing);
        result.setUploadedDocuments(uploadedNames);
        result.setDocumentPoints(documentPoints);

        // Compatibility fields for existing templates.
        result.setOtherCriteriaPts(profilePts);
        result.setDocumentPts(mandatoryPts + optionalPts);
        result.setIdentityDocPts(identityOk ? 5 : 0);
        result.setIncomePts(incomeEligible ? 15 : 0);
        result.setResidenceOccupationPts(
                (occupationOk ? 5 : 0) + (residenceOk ? 5 : 0) +
                        (genderOk ? 5 : 0) + (disabilityOk ? 5 : 0));
        result.setOptionalDocPts(optionalPts);

        List<String> failures = new ArrayList<>();
        if (!ageEligible) failures.add("Age is outside the scheme's permitted range.");
        if (!incomeEligible) failures.add("Annual income is above the scheme threshold.");
        if (!identityOk) failures.add("Government ID is missing.");
        if (!occupationOk) failures.add("Occupation does not match the scheme requirement.");
        if (!genderOk) failures.add("Gender does not match the scheme requirement.");
        if (!disabilityOk) failures.add("Required disability status/certificate condition is not met.");
        if (!residenceOk) failures.add("Residence type does not match the scheme requirement.");
        if (!missing.isEmpty()) failures.add("Missing mandatory documents: " + String.join(", ", missing));

        result.setFailureReasons(failures.isEmpty() ? "All eligibility and document requirements are satisfied." : String.join(" ", failures));
        result.setSuggestedActions(missing.isEmpty()
                ? "All mandatory documents are present. Optional documents can add up to 10 points."
                : "Upload the missing mandatory documents before submitting the application.");

        return result;
    }

    /** Compatibility overload used by older officer code. */
    public SchemeEligibilityScore calculateLiveScore(Beneficiary beneficiary,
                                                     Scheme scheme,
                                                     boolean hasIncomeCertificate,
                                                     boolean hasResidenceProof,
                                                     boolean hasSchemeSpecificDoc) {
        Set<String> docs = new HashSet<>();
        if (hasIncomeCertificate) docs.add(DocumentKeyUtil.key("Income Certificate"));
        if (hasResidenceProof) docs.add(DocumentKeyUtil.key("Residence Proof"));
        if (hasSchemeSpecificDoc) {
            // Legacy callers do not know the scheme-specific key. Count the first optional document.
            LinkedHashMap<String, String> optional = DocumentKeyUtil.parse(scheme.getOptionalDocuments());
            if (!optional.isEmpty()) docs.add(optional.keySet().iterator().next());
        }
        return calculateLiveScore(beneficiary, scheme, docs);
    }

    // Scan actionable applications periodically: works even when an officer action
    // happened before SMTP became available. Only the registered citizen is emailed.
    @Scheduled(cron = "0 */5 * * * *")
    @org.springframework.transaction.annotation.Transactional
    public void deliverCitizenActionEmails() {
        if (!citizenMailEnabled || mailFrom.isBlank()) return;
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null) return;
        for (SubsidyApplication app : applicationRepository.findAll()) {
            if (!app.isAwaitingResubmission()) continue;
            ApplicationStatus status = app.getApplicationStatus();
            if (status != ApplicationStatus.APPLICANT_REVERIFICATION && status != ApplicationStatus.REJECTED) continue;
            // Each new officer decision appends a remark; its ID changes for each request.
            ApplicationRemark latest = app.getRemarks().isEmpty() ? null : app.getRemarks().get(app.getRemarks().size()-1);
            if (latest == null || latest.getId() == null) continue;
            String key = "ACTION:" + app.getId() + ":" + latest.getId();
            if (key.equals(app.getLastCitizenActionEmailKey())) continue;
            String recipient = app.getUser() == null ? null : app.getUser().getEmail();
            if (recipient == null || recipient.isBlank()) continue;
            try {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setFrom(mailFrom);
                message.setTo(recipient);
                message.setSubject("SAGAR - Documents/action required for application #" + app.getId());
                message.setText("Dear Citizen,\n\nYour SAGAR application SUB-" + app.getId() +
                        " requires your attention. Current status: " + status.name().replace('_',' ') +
                        ".\nPlease sign in and review the officer's instructions: " + portalUrl +
                        "/dashboard/citizen\n\nDo not reply with sensitive documents.\nSAGAR Team");
                sender.send(message);
                app.setLastCitizenActionEmailKey(key);
                applicationRepository.save(app);
            } catch (MailException ex) {
                System.err.println("SAGAR action email failed for application " + app.getId() + ": " + ex.getClass().getSimpleName());
            }
        }
    }

    // ---------------- Workflow operations ----------------

    public void fieldOfficerAction(Long appId, String action, String remarks) {
        SubsidyApplication app = getApp(appId);
        if (app.getApplicationStatus() != ApplicationStatus.FIELD_VERIFICATION
                && app.getApplicationStatus() != ApplicationStatus.RESUBMITTED) {
            throw new IllegalStateException("Application is not in Field Verification stage.");
        }
        String previousStatus = app.getApplicationStatus().name();
        String note = normalizeRemark(remarks, action, "Field Officer");
        app.setFieldInspectionRemarks(note);
        switch (normalizeAction(action)) {
            case "APPROVE" -> app.setApplicationStatus(ApplicationStatus.DISTRICT_REVIEW);
            case "REJECT" -> rejectApplication(app);
            case "REVERIFY", "SEND_BACK_APPLICANT", "SEND_TO_APPLICANT", "REQUEST_OPTIONAL_DOCUMENTS" -> sendToApplicant(app);
            default -> throw new IllegalArgumentException("Invalid field officer action: " + action);
        }
        applicationRepository.save(app);
        addRemark(app, "FIELD_VERIFICATION", "FIELD_OFFICER", action, note);
        auditLogService.record("SUBSIDY_APPLICATION", app.getId(),
                "FIELD_" + normalizeAction(action), previousStatus,
                app.getApplicationStatus().name(), note);
    }

    public void districtOfficerAction(Long appId, String action, String remarks) {
        SubsidyApplication app = getApp(appId);
        if (app.getApplicationStatus() != ApplicationStatus.DISTRICT_REVIEW) {
            throw new IllegalStateException("Application is not in District Review stage.");
        }
        String previousStatus = app.getApplicationStatus().name();
        String note = normalizeRemark(remarks, action, "District Officer");
        app.setDistrictReviewRemarks(note);
        switch (normalizeAction(action)) {
            case "APPROVE" -> app.setApplicationStatus(ApplicationStatus.FINANCE_PENDING);
            case "REJECT" -> rejectApplication(app);
            case "SENDBACK" -> app.setApplicationStatus(ApplicationStatus.FIELD_VERIFICATION);
            case "SEND_BACK_APPLICANT", "SEND_TO_APPLICANT", "REQUEST_OPTIONAL_DOCUMENTS" -> sendToApplicant(app);
            case "REMARK" -> app.setAwaitingResubmission(true);
            default -> throw new IllegalArgumentException("Invalid district officer action: " + action);
        }
        applicationRepository.save(app);
        addRemark(app, "DISTRICT_REVIEW", "DISTRICT_OFFICER", action, note);
        auditLogService.record("SUBSIDY_APPLICATION", app.getId(),
                "DISTRICT_" + normalizeAction(action), previousStatus,
                app.getApplicationStatus().name(), note);
    }

    public void financeApproverAction(Long appId, String action, String remarks, double sanctionAmount) {
        SubsidyApplication app = getApp(appId);
        if (app.getApplicationStatus() != ApplicationStatus.FINANCE_PENDING) {
            throw new IllegalStateException("Application is not in Finance Pending stage.");
        }
        String previousStatus = app.getApplicationStatus().name();
        String note = normalizeRemark(remarks, action, "Finance Approver");
        app.setFinanceApprovalRemarks(note);
        switch (normalizeAction(action)) {
            case "APPROVE" -> {
                if (app.getScheme() == null) {
                    throw new IllegalStateException("Application has no scheme configuration.");
                }
                double schemeCeiling = app.getScheme().getMaxSanctionAmount();
                double availableBudget = app.getScheme().getRemainingBudget();
                if (schemeCeiling <= 0) {
                    throw new IllegalStateException("Scheme sanction ceiling is not configured. Ask the administrator to configure it.");
                }
                if (sanctionAmount <= 0) {
                    throw new IllegalArgumentException("Enter a sanction amount greater than zero.");
                }
                if (sanctionAmount > schemeCeiling + 0.001) {
                    throw new IllegalArgumentException("Sanction amount cannot exceed the scheme limit of ₹"
                            + String.format("%,.2f", schemeCeiling) + ".");
                }
                if (sanctionAmount > availableBudget + 0.001) {
                    throw new IllegalArgumentException("Sanction amount cannot exceed the current scheme budget of ₹"
                            + String.format("%,.2f", availableBudget) + ".");
                }
                if (remarks == null || remarks.isBlank()) {
                    throw new IllegalArgumentException("Finance sanction remarks are required.");
                }
                app.setSanctionAmount(sanctionAmount);
                app.setApplicationStatus(ApplicationStatus.FUND_DISBURSEMENT);
            }
            case "REJECT" -> rejectApplication(app);
            case "SENDBACK" -> app.setApplicationStatus(ApplicationStatus.DISTRICT_REVIEW);
            case "SEND_BACK_APPLICANT", "SEND_TO_APPLICANT", "REQUEST_OPTIONAL_DOCUMENTS" -> sendToApplicant(app);
            default -> throw new IllegalArgumentException("Invalid finance action: " + action);
        }
        applicationRepository.save(app);
        addRemark(app, "FINANCE_PENDING", "FINANCE_APPROVER", action, note);
        String financeNewValue = app.getApplicationStatus().name();
        if ("APPROVE".equals(normalizeAction(action))) {
            financeNewValue += " | sanctionAmount=" + String.format("%.2f", app.getSanctionAmount());
        }
        auditLogService.record("SUBSIDY_APPLICATION", app.getId(),
                "FINANCE_" + normalizeAction(action), previousStatus,
                financeNewValue, note);
    }

    public void fundDisbursementAction(Long appId, String action, String utrNumber, String remarks) {
        SubsidyApplication app = getApp(appId);
        if (app.getApplicationStatus() != ApplicationStatus.FUND_DISBURSEMENT) {
            throw new IllegalStateException("Application is not in Fund Disbursement stage.");
        }
        String previousStatus = app.getApplicationStatus().name();
        String note = normalizeRemark(remarks, action, "Disbursement Officer");
        app.setDisbursementRemarks(note);
        switch (normalizeAction(action)) {
            case "DISBURSE" -> {
                if (utrNumber == null || utrNumber.isBlank()) {
                    throw new IllegalArgumentException("UTR number is required before disbursement.");
                }
                app.setApplicationStatus(ApplicationStatus.APPROVED);
                app.setUtrNumber(utrNumber.trim());
                app.setDisbursedAt(LocalDateTime.now());
            }
            case "HOLD" -> { }
            case "REJECT" -> rejectApplication(app);
            case "SEND_FOR_APPROVAL", "SEND_TO_APPROVAL" -> {
                // Finance exception during disbursement: send the application back
                // to the Finance approval queue for an explicit decision.
                app.setApplicationStatus(ApplicationStatus.FINANCE_PENDING);
            }
            case "SEND_BACK_APPLICANT", "SEND_TO_APPLICANT", "REQUEST_OPTIONAL_DOCUMENTS" -> sendToApplicant(app);
            default -> throw new IllegalArgumentException("Invalid disbursement action: " + action);
        }
        applicationRepository.save(app);
        addRemark(app, "FUND_DISBURSEMENT", "DISBURSEMENT_OFFICER", action, note);
        auditLogService.record("SUBSIDY_APPLICATION", app.getId(),
                "DISBURSEMENT_" + normalizeAction(action), previousStatus,
                app.getApplicationStatus().name(), note);
    }

    /** Citizen has supplied the requested documents and sends the application back into Field Verification. */
    public void markApplicantResubmitted(SubsidyApplication app) {
        String previousStatus = app.getApplicationStatus() == null
                ? null : app.getApplicationStatus().name();
        // After the third rejection the same application waits through cooling.
        // Once cooling has expired, resubmitting starts a new 0/3 rejection cycle
        // without creating a second application record for the same scheme.
        if (app.getApplicationStatus() == ApplicationStatus.COOLING_PERIOD
                && !app.isCoolingPeriodActive()) {
            app.setRejectionCount(0);
        }
        app.setApplicationStatus(ApplicationStatus.FIELD_VERIFICATION);
        app.setAwaitingResubmission(false);
        app.setResubmissionCount(app.getResubmissionCount() + 1);
        app.setCoolingPeriodEndDate(null);
        applicationRepository.save(app);
        String note = "Applicant continued/resubmitted the same application for verification.";
        addRemark(app, "APPLICANT", "BENEFICIARY", "RESUBMITTED", note);
        auditLogService.record("SUBSIDY_APPLICATION", app.getId(),
                "APPLICATION_RESUBMITTED", previousStatus,
                app.getApplicationStatus().name(), note);
    }

    /**
     * Records a formal rejection for the current application attempt. The applicant
     * gets three rejection attempts in total. Cooling is applied only after the
     * third rejection; the first two rejections return the application to the
     * applicant for another submission without imposing a cooling period.
     */
    private void rejectApplication(SubsidyApplication app) {
        int count = app.getRejectionCount() + 1;
        app.setRejectionCount(count);

        if (count >= MAX_REJECTIONS_BEFORE_COOLING) {
            app.setApplicationStatus(ApplicationStatus.COOLING_PERIOD);
            app.setCoolingPeriodEndDate(LocalDateTime.now().plusDays(COOLING_PERIOD_DAYS));
            app.setAwaitingResubmission(false);
        } else {
            app.setApplicationStatus(ApplicationStatus.REJECTED);
            app.setCoolingPeriodEndDate(null);
            app.setAwaitingResubmission(true);
        }
    }

    private void sendToApplicant(SubsidyApplication app) {
        app.setApplicationStatus(ApplicationStatus.APPLICANT_REVERIFICATION);
        app.setAwaitingResubmission(true);
        app.setCoolingPeriodEndDate(null);
    }

    private String normalizeAction(String action) {
        if (action == null || action.isBlank()) {
            throw new IllegalArgumentException("Workflow action is required.");
        }
        return action.trim().toUpperCase().replace('-', '_').replace(' ', '_');
    }

    private String normalizeRemark(String remarks, String action, String actor) {
        if (remarks != null && !remarks.isBlank()) return remarks.trim();
        return actor + " action: " + action.replace('_', ' ').toLowerCase() + ".";
    }

    private void addRemark(SubsidyApplication app, String stage, String actorRole, String action, String remarks) {
        ApplicationRemark entry = new ApplicationRemark();
        entry.setApplication(app);
        entry.setStage(stage);
        entry.setActorRole(actorRole);
        entry.setAction(action);
        entry.setRemarks(remarks == null || remarks.isBlank() ? "No remarks provided." : remarks.trim());
        entry.setVisibleToApplicant(true);
        entry.setCreatedAt(LocalDateTime.now());
        remarkRepository.save(entry);
    }

    public void documentAction(Long documentId, String action, String remarks, String actorRole) {
        ApplicationDocument document = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found: " + documentId));
        SubsidyApplication app = document.getApplication();
        String previousDocumentStatus = document.getVerificationStatus();
        String normalized = normalizeAction(action);
        String note = normalizeRemark(remarks, action, actorRole);
        switch (normalized) {
            case "VERIFY", "APPROVE" -> {
                document.setVerificationStatus("VERIFIED");
                document.setVerificationRemarks(note);
                document.setVerifiedAt(LocalDateTime.now());
                document.setVerifiedBy(actorRole);
            }

            case "RESUBMIT" -> {
                document.setVerificationStatus("RESUBMIT_REQUIRED");
                document.setVerificationRemarks(note);
                document.setVerifiedAt(LocalDateTime.now());
                document.setVerifiedBy(actorRole);
            }

            case "REJECT" -> {
                document.setVerificationStatus("REJECTED");
                document.setVerificationRemarks(note);
                document.setVerifiedAt(LocalDateTime.now());
                document.setVerifiedBy(actorRole);
                sendToApplicant(app);
            }
            default -> throw new IllegalArgumentException("Invalid document action: " + action);
        }
        documentRepository.save(document);
        applicationRepository.save(app);
        addRemark(app, actorRole.contains("DISTRICT") ? "DISTRICT_REVIEW" : "FIELD_VERIFICATION",
                actorRole, "DOCUMENT_" + normalized, note);
        auditLogService.record("APPLICATION_DOCUMENT", document.getId(),
                "DOCUMENT_" + normalized,
                previousDocumentStatus, document.getVerificationStatus(),
                "Application #" + app.getId() + " | " + note);
    }

    public List<SubsidyApplication> getApplicationsForStatus(ApplicationStatus status) {
        return applicationRepository.findByApplicationStatus(status);
    }

    public List<SubsidyApplication> getAllApplicationsForAdmin() {
        return applicationRepository.findAll();
    }

    public List<SubsidyApplication> getApplicationsByUser(com.platform.user.User user) {
        return applicationRepository.findByUser(user);
    }

    private SubsidyApplication getApp(Long appId) {
        return applicationRepository.findById(appId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found: " + appId));
    }
}