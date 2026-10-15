package com.platform.scheme;

import jakarta.persistence.*;

@Entity
@Table(name = "schemes")
public class Scheme {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String schemeName;

    // ── Description & eligibility info (for scheme cards and citizen dashboard) ──
    @Column(length = 1000)
    private String description;           // What this scheme is about

    @Column(length = 500)
    private String benefitSummary;        // E.g. "Up to ₹1,00,000 direct bank transfer"

    private String targetBeneficiary;     // E.g. "Students aged 18–30 from low-income families"

    private String emoji;                 // UI icon: 🎓 🌾 🏠 💼 👴 ♿ 🔧

    // ── Core eligibility parameters ──
    private int eligibleAgeMin;
    private int eligibleAgeMax;
    private double maxIncomeThreshold;
    private double totalRegionalBudget;
    private double remainingBudget;

    // Maximum amount Finance may sanction to one application under this scheme.
    // This is a scheme rule, not an officer-entered arbitrary limit.
    private double maxSanctionAmount;

    // ── Scheme-specific eligibility criteria ──
    private String requiredOccupation;     // Comma-separated accepted values
    private String requiredGender;
    private boolean requiresDisability;
    private String requiredResidenceType;

    // ── Required documents for this scheme ──
    @Column(length = 500)
    private String requiredDocuments;     // Comma-separated, shown on eligibility form

    // ── Optional documents (for score bonus) ──
    @Column(length = 500)
    private String optionalDocuments;

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSchemeName() { return schemeName; }
    public void setSchemeName(String schemeName) { this.schemeName = schemeName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getBenefitSummary() { return benefitSummary; }
    public void setBenefitSummary(String benefitSummary) { this.benefitSummary = benefitSummary; }

    public String getTargetBeneficiary() { return targetBeneficiary; }
    public void setTargetBeneficiary(String targetBeneficiary) { this.targetBeneficiary = targetBeneficiary; }

    public String getEmoji() { return emoji != null ? emoji : "📋"; }
    public void setEmoji(String emoji) { this.emoji = emoji; }

    public int getEligibleAgeMin() { return eligibleAgeMin; }
    public void setEligibleAgeMin(int eligibleAgeMin) { this.eligibleAgeMin = eligibleAgeMin; }

    public int getEligibleAgeMax() { return eligibleAgeMax; }
    public void setEligibleAgeMax(int eligibleAgeMax) { this.eligibleAgeMax = eligibleAgeMax; }

    public double getMaxIncomeThreshold() { return maxIncomeThreshold; }
    public void setMaxIncomeThreshold(double maxIncomeThreshold) { this.maxIncomeThreshold = maxIncomeThreshold; }

    public double getTotalRegionalBudget() { return totalRegionalBudget; }
    public void setTotalRegionalBudget(double totalRegionalBudget) { this.totalRegionalBudget = totalRegionalBudget; }

    public double getRemainingBudget() { return remainingBudget; }
    public void setRemainingBudget(double remainingBudget) { this.remainingBudget = remainingBudget; }

    public double getMaxSanctionAmount() { return maxSanctionAmount; }
    public void setMaxSanctionAmount(double maxSanctionAmount) { this.maxSanctionAmount = maxSanctionAmount; }

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

    // Convenience flags
    public boolean isOccupationRestricted() { return requiredOccupation != null && !requiredOccupation.isBlank(); }
    public boolean isGenderRestricted()     { return requiredGender != null && !requiredGender.isBlank(); }
    public boolean isResidenceRestricted()  { return requiredResidenceType != null && !requiredResidenceType.isBlank(); }
}