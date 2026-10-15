package com.platform.admin;

import com.platform.scheme.Scheme;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Admin-only form object for scheme master-data management.
 * Eligibility scoring itself remains in EligibilityService and is not changed here.
 */
public class SchemeAdminForm {

    private Long id;

    @NotBlank(message = "Scheme name is required.")
    private String schemeName;

    private String emoji;

    private String description;
    private String benefitSummary;
    private String targetBeneficiary;

    @NotNull(message = "Minimum eligible age is required.")
    @Min(value = 0, message = "Minimum age cannot be negative.")
    private Integer eligibleAgeMin;

    @NotNull(message = "Maximum eligible age is required.")
    @Min(value = 0, message = "Maximum age cannot be negative.")
    private Integer eligibleAgeMax;

    @NotNull(message = "Maximum income threshold is required.")
    @DecimalMin(value = "0.0", inclusive = true, message = "Maximum income cannot be negative.")
    private Double maxIncomeThreshold;

    @NotNull(message = "Regional budget is required.")
    @DecimalMin(value = "0.0", inclusive = false, message = "Regional budget must be greater than zero.")
    private Double totalRegionalBudget;

    @NotNull(message = "Maximum sanction per application is required.")
    @DecimalMin(value = "0.01", inclusive = true, message = "Maximum sanction must be greater than zero.")
    private Double maxSanctionAmount;

    private String requiredOccupation;
    private String requiredGender;
    private boolean requiresDisability;
    private String requiredResidenceType;
    private String requiredDocuments;
    private String optionalDocuments;

    public static SchemeAdminForm from(Scheme scheme) {
        SchemeAdminForm form = new SchemeAdminForm();
        form.setId(scheme.getId());
        form.setSchemeName(scheme.getSchemeName());
        form.setEmoji(scheme.getEmoji());
        form.setDescription(scheme.getDescription());
        form.setBenefitSummary(scheme.getBenefitSummary());
        form.setTargetBeneficiary(scheme.getTargetBeneficiary());
        form.setEligibleAgeMin(scheme.getEligibleAgeMin());
        form.setEligibleAgeMax(scheme.getEligibleAgeMax());
        form.setMaxIncomeThreshold(scheme.getMaxIncomeThreshold());
        form.setTotalRegionalBudget(scheme.getTotalRegionalBudget());
        form.setMaxSanctionAmount(scheme.getMaxSanctionAmount());
        form.setRequiredOccupation(scheme.getRequiredOccupation());
        form.setRequiredGender(scheme.getRequiredGender());
        form.setRequiresDisability(scheme.isRequiresDisability());
        form.setRequiredResidenceType(scheme.getRequiredResidenceType());
        form.setRequiredDocuments(scheme.getRequiredDocuments());
        form.setOptionalDocuments(scheme.getOptionalDocuments());
        return form;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSchemeName() { return schemeName; }
    public void setSchemeName(String schemeName) { this.schemeName = schemeName; }
    public String getEmoji() { return emoji; }
    public void setEmoji(String emoji) { this.emoji = emoji; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getBenefitSummary() { return benefitSummary; }
    public void setBenefitSummary(String benefitSummary) { this.benefitSummary = benefitSummary; }
    public String getTargetBeneficiary() { return targetBeneficiary; }
    public void setTargetBeneficiary(String targetBeneficiary) { this.targetBeneficiary = targetBeneficiary; }
    public Integer getEligibleAgeMin() { return eligibleAgeMin; }
    public void setEligibleAgeMin(Integer eligibleAgeMin) { this.eligibleAgeMin = eligibleAgeMin; }
    public Integer getEligibleAgeMax() { return eligibleAgeMax; }
    public void setEligibleAgeMax(Integer eligibleAgeMax) { this.eligibleAgeMax = eligibleAgeMax; }
    public Double getMaxIncomeThreshold() { return maxIncomeThreshold; }
    public void setMaxIncomeThreshold(Double maxIncomeThreshold) { this.maxIncomeThreshold = maxIncomeThreshold; }
    public Double getTotalRegionalBudget() { return totalRegionalBudget; }
    public void setTotalRegionalBudget(Double totalRegionalBudget) { this.totalRegionalBudget = totalRegionalBudget; }
    public Double getMaxSanctionAmount() { return maxSanctionAmount; }
    public void setMaxSanctionAmount(Double maxSanctionAmount) { this.maxSanctionAmount = maxSanctionAmount; }
    public String getRequiredOccupation() { return requiredOccupation; }
    public void setRequiredOccupation(String requiredOccupation) { this.requiredOccupation = requiredOccupation; }
    public String getRequiredGender() { return requiredGender; }
    public void setRequiredGender(String requiredGender) { this.requiredGender = requiredGender; }
    public boolean isRequiresDisability() { return requiresDisability; }
    public void setRequiresDisability(boolean requiresDisability) { this.requiresDisability = requiresDisability; }
    public String getRequiredResidenceType() { return requiredResidenceType; }
    public void setRequiredResidenceType(String requiredResidenceType) { this.requiredResidenceType = requiredResidenceType; }
    public String getRequiredDocuments() { return requiredDocuments; }
    public void setRequiredDocuments(String requiredDocuments) { this.requiredDocuments = requiredDocuments; }
    public String getOptionalDocuments() { return optionalDocuments; }
    public void setOptionalDocuments(String optionalDocuments) { this.optionalDocuments = optionalDocuments; }
}
