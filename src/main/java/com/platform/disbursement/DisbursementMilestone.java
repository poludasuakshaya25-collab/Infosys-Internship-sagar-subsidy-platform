package com.platform.disbursement;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "disbursement_milestones")
public class DisbursementMilestone {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    private DisbursementPlan plan;

    @Column(nullable = false)
    private int sequenceNo;

    @Column(nullable = false, length = 120)
    private String milestoneName;

    @Column(length = 500)
    private String complianceRequirement;

    @Column(nullable = false)
    private LocalDate dueDate;

    @Column(nullable = false)
    private LocalTime dueTime = LocalTime.of(10, 0);

    @Column(nullable = false)
    private double scheduledAmount;

    @Column(nullable = false)
    private double releasedAmount = 0.0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private MilestoneStatus status = MilestoneStatus.PENDING;

    private LocalDate complianceSubmittedDate;
    private LocalDate complianceVerifiedDate;
    private LocalDate releaseDate;
    private LocalDateTime releasedAt;

    @Column(length = 120)
    private String releaseReference;

    @Column(length = 1000)
    private String complianceRemarks;

    @Column(length = 1000)
    private String releaseRemarks;

    private LocalDateTime lastReminderAt;
    private String lastReminderType;

    @Column(nullable = false)
    private boolean complianceRequested = false;

    @Column(length = 1000)
    private String complianceRequestRemarks;

    private LocalDateTime complianceRequestedAt;
    private LocalDateTime complianceUploadedAt;

    @Column(length = 500)
    private String complianceOriginalFilename;

    @Column(length = 500)
    private String complianceStoredFilename;

    public Long getId() { return id; }
    public DisbursementPlan getPlan() { return plan; }
    public void setPlan(DisbursementPlan plan) { this.plan = plan; }
    public int getSequenceNo() { return sequenceNo; }
    public void setSequenceNo(int sequenceNo) { this.sequenceNo = sequenceNo; }
    public String getMilestoneName() { return milestoneName; }
    public void setMilestoneName(String milestoneName) { this.milestoneName = milestoneName; }
    public String getComplianceRequirement() { return complianceRequirement; }
    public void setComplianceRequirement(String complianceRequirement) { this.complianceRequirement = complianceRequirement; }
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
    public LocalTime getDueTime() { return dueTime; }
    public void setDueTime(LocalTime dueTime) { this.dueTime = dueTime; }
    public double getScheduledAmount() { return scheduledAmount; }
    public void setScheduledAmount(double scheduledAmount) { this.scheduledAmount = scheduledAmount; }
    public double getReleasedAmount() { return releasedAmount; }
    public void setReleasedAmount(double releasedAmount) { this.releasedAmount = releasedAmount; }
    public MilestoneStatus getStatus() { return status; }
    public void setStatus(MilestoneStatus status) { this.status = status; }
    public LocalDate getComplianceSubmittedDate() { return complianceSubmittedDate; }
    public void setComplianceSubmittedDate(LocalDate value) { this.complianceSubmittedDate = value; }
    public LocalDate getComplianceVerifiedDate() { return complianceVerifiedDate; }
    public void setComplianceVerifiedDate(LocalDate value) { this.complianceVerifiedDate = value; }
    public LocalDate getReleaseDate() { return releaseDate; }
    public void setReleaseDate(LocalDate value) { this.releaseDate = value; }
    public LocalDateTime getReleasedAt() { return releasedAt; }
    public void setReleasedAt(LocalDateTime value) { this.releasedAt = value; }
    public String getReleaseReference() { return releaseReference; }
    public void setReleaseReference(String value) { this.releaseReference = value; }
    public String getComplianceRemarks() { return complianceRemarks; }
    public void setComplianceRemarks(String value) { this.complianceRemarks = value; }
    public String getReleaseRemarks() { return releaseRemarks; }
    public void setReleaseRemarks(String value) { this.releaseRemarks = value; }
    public LocalDateTime getLastReminderAt() { return lastReminderAt; }
    public void setLastReminderAt(LocalDateTime value) { this.lastReminderAt = value; }
    public String getLastReminderType() { return lastReminderType; }
    public void setLastReminderType(String value) { this.lastReminderType = value; }
    public boolean isComplianceRequested() { return complianceRequested; }
    public void setComplianceRequested(boolean value) { this.complianceRequested = value; }
    public String getComplianceRequestRemarks() { return complianceRequestRemarks; }
    public void setComplianceRequestRemarks(String value) { this.complianceRequestRemarks = value; }
    public LocalDateTime getComplianceRequestedAt() { return complianceRequestedAt; }
    public void setComplianceRequestedAt(LocalDateTime value) { this.complianceRequestedAt = value; }
    public LocalDateTime getComplianceUploadedAt() { return complianceUploadedAt; }
    public void setComplianceUploadedAt(LocalDateTime value) { this.complianceUploadedAt = value; }
    public String getComplianceOriginalFilename() { return complianceOriginalFilename; }
    public void setComplianceOriginalFilename(String value) { this.complianceOriginalFilename = value; }
    public String getComplianceStoredFilename() { return complianceStoredFilename; }
    public void setComplianceStoredFilename(String value) { this.complianceStoredFilename = value; }

    @Transient
    public boolean isOverdue() {
        return status != MilestoneStatus.RELEASED
                && status != MilestoneStatus.COMPLIANCE_VERIFIED
                && dueDate != null
                && LocalDate.now().isAfter(dueDate);
    }

    @Transient
    public double getPendingAmount() {
        return Math.max(0.0, scheduledAmount - releasedAmount);
    }
}
