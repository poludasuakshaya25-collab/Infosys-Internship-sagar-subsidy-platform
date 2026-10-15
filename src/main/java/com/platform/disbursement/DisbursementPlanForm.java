package com.platform.disbursement;

import java.util.ArrayList;
import java.util.List;

public class DisbursementPlanForm {

    private List<MilestoneForm> milestones = new ArrayList<>();

    public List<MilestoneForm> getMilestones() {
        return milestones;
    }

    public void setMilestones(List<MilestoneForm> milestones) {
        this.milestones = milestones;
    }

    public static class MilestoneForm {

        private String name;

        private String requirement;

        private String dueDate;
        private String dueTime = "10:00";

        private double scheduledAmount;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getRequirement() {
            return requirement;
        }

        public void setRequirement(String requirement) {
            this.requirement = requirement;
        }

        public String getDueDate() {
            return dueDate;
        }

        public void setDueDate(String dueDate) {
            this.dueDate = dueDate;
        }

        public String getDueTime() { return dueTime; }
        public void setDueTime(String dueTime) { this.dueTime = dueTime; }

        public double getScheduledAmount() {
            return scheduledAmount;
        }

        public void setScheduledAmount(double scheduledAmount) {
            this.scheduledAmount = scheduledAmount;
        }
    }
}