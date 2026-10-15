package com.platform.application;

import java.util.ArrayList;
import java.util.List;

public class EligibilityResult {
    private String userEmail;
    private String targetScheme;
    private boolean isEligible;
    private int eligibilityScore;
    private String remarks;

    // Checklist metrics collection arrays mapping back to your loop outputs
    private List<String> passedCriteria = new ArrayList<>();
    private List<String> failedCriteria = new ArrayList<>();

    // Getters and Setters
    public String getUserEmail() { return userEmail; }
    public void setUserEmail(String userEmail) { this.userEmail = userEmail; }
    public String getTargetScheme() { return targetScheme; }
    public void setTargetScheme(String targetScheme) { this.targetScheme = targetScheme; }
    public boolean isEligible() { return isEligible; }
    public void setEligible(boolean eligible) { isEligible = eligible; }
    public int getEligibilityScore() { return eligibilityScore; }
    public void setEligibilityScore(int eligibilityScore) { this.eligibilityScore = eligibilityScore; }
    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }
    public List<String> getPassedCriteria() { return passedCriteria; }
    public void setPassedCriteria(List<String> passedCriteria) { this.passedCriteria = passedCriteria; }
    public List<String> getFailedCriteria() { return failedCriteria; }
    public void setFailedCriteria(List<String> failedCriteria) { this.failedCriteria = failedCriteria; }
}
