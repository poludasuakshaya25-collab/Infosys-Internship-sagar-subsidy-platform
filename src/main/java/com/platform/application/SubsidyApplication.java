package com.platform.application;

import com.platform.user.User;
import com.platform.scheme.Scheme;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "subsidy_applications")
public class SubsidyApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne
    @JoinColumn(name = "scheme_id", nullable = false)
    private Scheme scheme;

    @Column(nullable = false)
    private boolean eligibilityStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(50)")
    private ApplicationStatus applicationStatus;

    @Column(nullable = false)
    private LocalDateTime applicationDate;

    private double sanctionAmount;
    private int eligibilityScore;

    // Legacy columns kept so existing data remains compatible.
    private String aadhaarFilename;
    private String bankPassbookFilename;
    private String incomeCertificateFilename;
    private String residenceProofFilename;
    private String businessDocFilename;
    private String propertyDocFilename;

    @OneToMany(mappedBy = "application", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("optionalDocument ASC, documentName ASC")
    private List<ApplicationDocument> documents = new ArrayList<>();

    @OneToMany(mappedBy = "application", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    private List<ApplicationRemark> remarks = new ArrayList<>();

    @Transient
    private List<String> missingOptionalDocuments = new ArrayList<>();

    @Transient
    private List<String> missingMandatoryDocuments = new ArrayList<>();

    // Last successfully delivered action-alert key (per application).
    @Column(length = 160)
    private String lastCitizenActionEmailKey;
    public String getLastCitizenActionEmailKey() { return lastCitizenActionEmailKey; }
    public void setLastCitizenActionEmailKey(String key) { lastCitizenActionEmailKey = key; }

    private String fieldInspectionRemarks;
    private String districtReviewRemarks;
    private String financeApprovalRemarks;
    private LocalDateTime coolingPeriodEndDate;
    private int resubmissionCount;
    private Integer rejectionCount = 0;
    private boolean awaitingResubmission;
    private String utrNumber;
    private LocalDateTime disbursedAt;

    // Application-specific Finance payment-account re-verification.
    private String paymentAccountHolderName;
    private String paymentAccountNumber;
    private String paymentIfscCode;
    private String paymentBankName;
    private boolean paymentBankDetailsRequested = false;
    private boolean paymentBankDetailsVerified = false;
    private String paymentBankVerificationStatus = "NOT_REQUESTED";
    private String disbursementRemarks;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public Scheme getScheme() { return scheme; }
    public void setScheme(Scheme scheme) { this.scheme = scheme; }
    public boolean isEligibilityStatus() { return eligibilityStatus; }
    public void setEligibilityStatus(boolean value) { this.eligibilityStatus = value; }
    public ApplicationStatus getApplicationStatus() { return applicationStatus; }
    public void setApplicationStatus(ApplicationStatus value) { this.applicationStatus = value; }
    public LocalDateTime getApplicationDate() { return applicationDate; }
    public void setApplicationDate(LocalDateTime value) { this.applicationDate = value; }
    public double getSanctionAmount() { return sanctionAmount; }
    public void setSanctionAmount(double value) { this.sanctionAmount = value; }
    public int getEligibilityScore() { return eligibilityScore; }
    public void setEligibilityScore(int value) { this.eligibilityScore = value; }
    public String getAadhaarFilename() { return aadhaarFilename; }
    public void setAadhaarFilename(String value) { this.aadhaarFilename = value; }
    public String getBankPassbookFilename() { return bankPassbookFilename; }
    public void setBankPassbookFilename(String value) { this.bankPassbookFilename = value; }
    public String getIncomeCertificateFilename() { return incomeCertificateFilename; }
    public void setIncomeCertificateFilename(String value) { this.incomeCertificateFilename = value; }
    public String getResidenceProofFilename() { return residenceProofFilename; }
    public void setResidenceProofFilename(String value) { this.residenceProofFilename = value; }
    public String getBusinessDocFilename() { return businessDocFilename; }
    public void setBusinessDocFilename(String value) { this.businessDocFilename = value; }
    public String getPropertyDocFilename() { return propertyDocFilename; }
    public void setPropertyDocFilename(String value) { this.propertyDocFilename = value; }
    public List<ApplicationDocument> getDocuments() { return documents; }
    public void setDocuments(List<ApplicationDocument> documents) { this.documents = documents; }
    public List<ApplicationRemark> getRemarks() { return remarks; }
    public void setRemarks(List<ApplicationRemark> remarks) { this.remarks = remarks; }
    public List<String> getMissingOptionalDocuments() { return missingOptionalDocuments; }
    public void setMissingOptionalDocuments(List<String> value) { this.missingOptionalDocuments = value; }
    public List<String> getMissingMandatoryDocuments() { return missingMandatoryDocuments; }
    public void setMissingMandatoryDocuments(List<String> value) { this.missingMandatoryDocuments = value; }
    public String getFieldInspectionRemarks() { return fieldInspectionRemarks; }
    public void setFieldInspectionRemarks(String value) { this.fieldInspectionRemarks = value; }
    public String getDistrictReviewRemarks() { return districtReviewRemarks; }
    public void setDistrictReviewRemarks(String value) { this.districtReviewRemarks = value; }
    public String getFinanceApprovalRemarks() { return financeApprovalRemarks; }
    public void setFinanceApprovalRemarks(String value) { this.financeApprovalRemarks = value; }
    public LocalDateTime getCoolingPeriodEndDate() { return coolingPeriodEndDate; }
    public void setCoolingPeriodEndDate(LocalDateTime value) { this.coolingPeriodEndDate = value; }
    public int getResubmissionCount() { return resubmissionCount; }
    public int getRejectionCount() { return rejectionCount == null ? 0 : rejectionCount; }
    public void setRejectionCount(Integer value) { this.rejectionCount = value == null ? 0 : value; }
    public void setResubmissionCount(int value) { this.resubmissionCount = value; }
    public boolean isAwaitingResubmission() { return awaitingResubmission; }
    public void setAwaitingResubmission(boolean value) { this.awaitingResubmission = value; }
    public String getUtrNumber() { return utrNumber; }
    public void setUtrNumber(String value) { this.utrNumber = value; }
    public LocalDateTime getDisbursedAt() { return disbursedAt; }
    public void setDisbursedAt(LocalDateTime value) { this.disbursedAt = value; }
    public String getDisbursementRemarks() { return disbursementRemarks; }
    public void setDisbursementRemarks(String value) { this.disbursementRemarks = value; }

    public boolean isCoolingPeriodActive() {
        return coolingPeriodEndDate != null && LocalDateTime.now().isBefore(coolingPeriodEndDate);
    }

    public String getPaymentAccountHolderName() { return paymentAccountHolderName; }
    public void setPaymentAccountHolderName(String v) { paymentAccountHolderName = v; }
    public String getPaymentAccountNumber() { return paymentAccountNumber; }
    public void setPaymentAccountNumber(String v) { paymentAccountNumber = v; }
    public String getPaymentIfscCode() { return paymentIfscCode; }
    public void setPaymentIfscCode(String v) { paymentIfscCode = v; }
    public String getPaymentBankName() { return paymentBankName; }
    public void setPaymentBankName(String v) { paymentBankName = v; }
    public boolean isPaymentBankDetailsRequested() { return paymentBankDetailsRequested; }
    public void setPaymentBankDetailsRequested(boolean v) { paymentBankDetailsRequested = v; }
    public boolean isPaymentBankDetailsVerified() { return paymentBankDetailsVerified; }
    public void setPaymentBankDetailsVerified(boolean v) { paymentBankDetailsVerified = v; }
    public String getPaymentBankVerificationStatus() { return paymentBankVerificationStatus; }
    public void setPaymentBankVerificationStatus(String v) { paymentBankVerificationStatus = v; }
}
