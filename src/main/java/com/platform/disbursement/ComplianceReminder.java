package com.platform.disbursement;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "compliance_reminders")
public class ComplianceReminder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "milestone_id", nullable = false)
    private DisbursementMilestone milestone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ReminderType reminderType;

    @Column(nullable = false)
    private LocalDateTime sentAt;

    @Column(length = 500)
    private String message;

    // Null means not yet emailed; persisted to avoid resending delivered alerts.
    private LocalDateTime emailSentAt;
    private int emailAttempts;
    private LocalDateTime emailLastAttemptAt;
    public LocalDateTime getEmailSentAt() { return emailSentAt; }
    public void setEmailSentAt(LocalDateTime value) { emailSentAt = value; }
    public int getEmailAttempts() { return emailAttempts; }
    public void setEmailAttempts(int value) { emailAttempts = value; }
    public LocalDateTime getEmailLastAttemptAt() { return emailLastAttemptAt; }
    public void setEmailLastAttemptAt(LocalDateTime value) { emailLastAttemptAt = value; }

    public Long getId() {
        return id;
    }

    public DisbursementMilestone getMilestone() {
        return milestone;
    }

    public void setMilestone(DisbursementMilestone milestone) {
        this.milestone = milestone;
    }

    public ReminderType getReminderType() {
        return reminderType;
    }

    public void setReminderType(ReminderType reminderType) {
        this.reminderType = reminderType;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }

    public void setSentAt(LocalDateTime sentAt) {
        this.sentAt = sentAt;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}