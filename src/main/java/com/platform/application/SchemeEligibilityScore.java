package com.platform.application;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Single scoring DTO used by both the live browser preview and final submit.
 *
 * Score = 50 profile points + 40 mandatory-document points + 10 optional-document points.
 * Mandatory profile failures make the application ineligible, while the score still
 * reflects the information/documents currently supplied by the citizen.
 */
public class SchemeEligibilityScore {

    private String schemeName;
    private boolean eligible;

    private boolean agePass;
    private boolean incomePass;
    private boolean identityPass;
    private boolean occupationPass;
    private boolean genderPass;
    private boolean disabilityPass;
    private boolean residencePass;

    private boolean mandatoryDocumentsComplete;
    private int uploadedMandatoryDocuments;
    private int totalMandatoryDocuments;
    private int uploadedOptionalDocuments;
    private int totalOptionalDocuments;

    private int profilePts;
    private int mandatoryDocumentPts;
    private int optionalDocumentPts;
    private int totalScore;

    // Kept for compatibility with the existing result template/controller.
    private int otherCriteriaPts;
    private int documentPts;
    private int identityDocPts;
    private int incomePts;
    private int residenceOccupationPts;
    private int optionalDocPts;

    private List<String> missingRequiredDocuments = new ArrayList<>();
    private List<String> uploadedDocuments = new ArrayList<>();
    private Map<String, Integer> documentPoints = new LinkedHashMap<>();
    private String failureReasons;
    private String suggestedActions;

    public String getSchemeName() { return schemeName; }
    public void setSchemeName(String schemeName) { this.schemeName = schemeName; }

    public boolean isEligible() { return eligible; }
    public void setEligible(boolean eligible) { this.eligible = eligible; }

    public boolean isAgePass() { return agePass; }
    public void setAgePass(boolean agePass) { this.agePass = agePass; }

    public boolean isIncomePass() { return incomePass; }
    public void setIncomePass(boolean incomePass) { this.incomePass = incomePass; }

    public boolean isIdentityPass() { return identityPass; }
    public void setIdentityPass(boolean identityPass) { this.identityPass = identityPass; }

    public boolean isOccupationPass() { return occupationPass; }
    public void setOccupationPass(boolean occupationPass) { this.occupationPass = occupationPass; }

    public boolean isGenderPass() { return genderPass; }
    public void setGenderPass(boolean genderPass) { this.genderPass = genderPass; }

    public boolean isDisabilityPass() { return disabilityPass; }
    public void setDisabilityPass(boolean disabilityPass) { this.disabilityPass = disabilityPass; }

    public boolean isResidencePass() { return residencePass; }
    public void setResidencePass(boolean residencePass) { this.residencePass = residencePass; }

    public boolean isMandatoryDocumentsComplete() { return mandatoryDocumentsComplete; }
    public void setMandatoryDocumentsComplete(boolean value) { this.mandatoryDocumentsComplete = value; }

    public int getUploadedMandatoryDocuments() { return uploadedMandatoryDocuments; }
    public void setUploadedMandatoryDocuments(int value) { this.uploadedMandatoryDocuments = value; }

    public int getTotalMandatoryDocuments() { return totalMandatoryDocuments; }
    public void setTotalMandatoryDocuments(int value) { this.totalMandatoryDocuments = value; }

    public int getUploadedOptionalDocuments() { return uploadedOptionalDocuments; }
    public void setUploadedOptionalDocuments(int value) { this.uploadedOptionalDocuments = value; }

    public int getTotalOptionalDocuments() { return totalOptionalDocuments; }
    public void setTotalOptionalDocuments(int value) { this.totalOptionalDocuments = value; }

    public int getProfilePts() { return profilePts; }
    public void setProfilePts(int value) { this.profilePts = value; }

    public int getMandatoryDocumentPts() { return mandatoryDocumentPts; }
    public void setMandatoryDocumentPts(int value) { this.mandatoryDocumentPts = value; }

    public int getOptionalDocumentPts() { return optionalDocumentPts; }
    public void setOptionalDocumentPts(int value) { this.optionalDocumentPts = value; }

    public int getTotalScore() { return totalScore; }
    public void setTotalScore(int totalScore) { this.totalScore = totalScore; }

    public int getOtherCriteriaPts() { return otherCriteriaPts; }
    public void setOtherCriteriaPts(int value) { this.otherCriteriaPts = value; }

    public int getDocumentPts() { return documentPts; }
    public void setDocumentPts(int value) { this.documentPts = value; }

    public int getIdentityDocPts() { return identityDocPts; }
    public void setIdentityDocPts(int value) { this.identityDocPts = value; }

    public int getIncomePts() { return incomePts; }
    public void setIncomePts(int value) { this.incomePts = value; }

    public int getResidenceOccupationPts() { return residenceOccupationPts; }
    public void setResidenceOccupationPts(int value) { this.residenceOccupationPts = value; }

    public int getOptionalDocPts() { return optionalDocPts; }
    public void setOptionalDocPts(int value) { this.optionalDocPts = value; }

    public List<String> getMissingRequiredDocuments() { return missingRequiredDocuments; }
    public void setMissingRequiredDocuments(List<String> value) { this.missingRequiredDocuments = value; }

    public List<String> getUploadedDocuments() { return uploadedDocuments; }
    public void setUploadedDocuments(List<String> value) { this.uploadedDocuments = value; }

    public Map<String, Integer> getDocumentPoints() { return documentPoints; }
    public void setDocumentPoints(Map<String, Integer> value) { this.documentPoints = value; }

    public String getFailureReasons() { return failureReasons; }
    public void setFailureReasons(String failureReasons) { this.failureReasons = failureReasons; }

    public String getSuggestedActions() { return suggestedActions; }
    public void setSuggestedActions(String suggestedActions) { this.suggestedActions = suggestedActions; }
}
