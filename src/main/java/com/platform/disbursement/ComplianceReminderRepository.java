package com.platform.disbursement;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;
public interface ComplianceReminderRepository extends JpaRepository<ComplianceReminder, Long> {
    List<ComplianceReminder> findTop100ByEmailSentAtIsNullOrderBySentAtAsc();
    boolean existsByMilestoneIdAndReminderType(Long milestoneId, ReminderType reminderType);
    boolean existsByMilestoneIdAndReminderTypeAndSentAtBetween(Long milestoneId, ReminderType reminderType, LocalDateTime start, LocalDateTime end);
    boolean existsByMilestoneId(Long milestoneId);
    List<ComplianceReminder> findByMilestonePlanIdOrderBySentAtDesc(Long planId);
}
