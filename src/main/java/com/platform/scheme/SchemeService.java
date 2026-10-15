package com.platform.scheme;

import com.platform.admin.SchemeAdminForm;
import com.platform.application.EligibilityResult;
import com.platform.application.SubsidyApplicationRepository;
import com.platform.user.User;
import com.platform.user.Beneficiary;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SchemeService {

    @Autowired
    private SchemeRepository schemeRepository;

    @Autowired
    private SubsidyApplicationRepository applicationRepository;

    public List<Scheme> getActiveSchemes() {
        // There is currently no separate ACTIVE column in the existing schema.
        // Keep this method name for backward compatibility with the citizen UI.
        return schemeRepository.findAll();
    }

    public List<Scheme> getAllSchemes() {
        return schemeRepository.findAll();
    }

    public Scheme getSchemeById(Long id) {
        return schemeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Scheme configuration missing for ID: " + id));
    }

    /**
     * Creates or updates scheme master data from the administrator console.
     *
     * Important workflow protection:
     * once applications exist for a scheme, the eligibility/scoring inputs are
     * frozen so a later admin edit cannot silently change an applicant's score.
     * Descriptive fields and budget can still be maintained.
     */
    @Transactional
    public Scheme createOrUpdateFromAdmin(SchemeAdminForm form) {
        validateForm(form);

        Scheme scheme;
        boolean creating = form.getId() == null;
        if (creating) {
            if (schemeRepository.findBySchemeNameIgnoreCase(form.getSchemeName().trim()).isPresent()) {
                throw new IllegalArgumentException("A scheme with this name already exists.");
            }
            scheme = new Scheme();
            scheme.setRemainingBudget(form.getTotalRegionalBudget());
        } else {
            scheme = getSchemeById(form.getId());
            schemeRepository.findBySchemeNameIgnoreCase(form.getSchemeName().trim())
                    .filter(existing -> !existing.getId().equals(scheme.getId()))
                    .ifPresent(existing -> {
                        throw new IllegalArgumentException("A different scheme already uses this name.");
                    });

            long applicationCount = applicationRepository.countByScheme(scheme);
            if (applicationCount > 0 && hasEligibilityConfigurationChanged(scheme, form)) {
                throw new IllegalStateException(
                        "Eligibility criteria and document rules are locked because this scheme already has applications. " +
                                "This protects previously calculated eligibility decisions.");
            }

            double oldBudget = Math.max(0, scheme.getTotalRegionalBudget());
            double oldRemaining = Math.max(0, scheme.getRemainingBudget());
            double utilized = Math.max(0, oldBudget - oldRemaining);
            if (form.getTotalRegionalBudget() + 0.01 < utilized) {
                throw new IllegalArgumentException(
                        "New regional budget cannot be lower than funds already committed/utilized (₹"
                                + String.format("%,.2f", utilized) + ").");
            }
            scheme.setRemainingBudget(round(form.getTotalRegionalBudget() - utilized));
        }

        scheme.setSchemeName(form.getSchemeName().trim());
        scheme.setEmoji(clean(form.getEmoji(), "📋"));
        scheme.setDescription(clean(form.getDescription(), null));
        scheme.setBenefitSummary(clean(form.getBenefitSummary(), null));
        scheme.setTargetBeneficiary(clean(form.getTargetBeneficiary(), null));
        scheme.setEligibleAgeMin(form.getEligibleAgeMin());
        scheme.setEligibleAgeMax(form.getEligibleAgeMax());
        scheme.setMaxIncomeThreshold(form.getMaxIncomeThreshold());
        scheme.setTotalRegionalBudget(form.getTotalRegionalBudget());
        scheme.setMaxSanctionAmount(form.getMaxSanctionAmount());
        scheme.setRequiredOccupation(clean(form.getRequiredOccupation(), null));
        scheme.setRequiredGender(clean(form.getRequiredGender(), null));
        scheme.setRequiresDisability(form.isRequiresDisability());
        scheme.setRequiredResidenceType(clean(form.getRequiredResidenceType(), null));
        scheme.setRequiredDocuments(clean(form.getRequiredDocuments(), null));
        scheme.setOptionalDocuments(clean(form.getOptionalDocuments(), null));

        // On a newly-created scheme the remaining budget was already initialized above.
        if (creating) {
            scheme.setRemainingBudget(form.getTotalRegionalBudget());
        }

        return schemeRepository.save(scheme);
    }

    @Transactional
    public void deleteByAdmin(Long id) {
        Scheme scheme = getSchemeById(id);
        long applicationCount = applicationRepository.countByScheme(scheme);
        if (applicationCount > 0) {
            throw new IllegalStateException(
                    "This scheme cannot be deleted because " + applicationCount
                            + " application(s) are linked to it. Archive/deactivate support can be added later without destroying history.");
        }
        schemeRepository.delete(scheme);
    }

    private void validateForm(SchemeAdminForm form) {
        if (form.getSchemeName() == null || form.getSchemeName().isBlank()) {
            throw new IllegalArgumentException("Scheme name is required.");
        }
        if (form.getEligibleAgeMin() == null || form.getEligibleAgeMax() == null
                || form.getEligibleAgeMin() > form.getEligibleAgeMax()) {
            throw new IllegalArgumentException("Eligible minimum age cannot be greater than maximum age.");
        }
        if (form.getMaxIncomeThreshold() == null || form.getMaxIncomeThreshold() < 0) {
            throw new IllegalArgumentException("Maximum income threshold cannot be negative.");
        }
        if (form.getTotalRegionalBudget() == null || form.getTotalRegionalBudget() <= 0) {
            throw new IllegalArgumentException("Regional budget must be greater than zero.");
        }
        if (form.getMaxSanctionAmount() == null || form.getMaxSanctionAmount() <= 0) {
            throw new IllegalArgumentException("Maximum sanction per application must be greater than zero.");
        }
        if (form.getMaxSanctionAmount() > form.getTotalRegionalBudget()) {
            throw new IllegalArgumentException("Maximum sanction per application cannot exceed the scheme regional budget.");
        }
    }

    private boolean hasEligibilityConfigurationChanged(Scheme scheme, SchemeAdminForm form) {
        return scheme.getEligibleAgeMin() != form.getEligibleAgeMin()
                || scheme.getEligibleAgeMax() != form.getEligibleAgeMax()
                || !sameDouble(scheme.getMaxIncomeThreshold(), form.getMaxIncomeThreshold())
                || !sameText(scheme.getRequiredOccupation(), form.getRequiredOccupation())
                || !sameText(scheme.getRequiredGender(), form.getRequiredGender())
                || scheme.isRequiresDisability() != form.isRequiresDisability()
                || !sameText(scheme.getRequiredResidenceType(), form.getRequiredResidenceType())
                || !sameText(scheme.getRequiredDocuments(), form.getRequiredDocuments())
                || !sameText(scheme.getOptionalDocuments(), form.getOptionalDocuments());
    }

    private boolean sameText(String a, String b) {
        return clean(a, "").equals(clean(b, ""));
    }

    private boolean sameDouble(double a, Double b) {
        return b != null && Math.abs(a - b) <= 0.0001;
    }

    private String clean(String value, String fallback) {
        if (value == null || value.isBlank()) return fallback;
        return value.trim();
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    /**
     * Existing legacy eligibility method intentionally retained unchanged.
     */
    public EligibilityResult checkEligibility(User user, Scheme scheme) {
        EligibilityResult result = new EligibilityResult();
        result.setUserEmail(user.getEmail() != null ? user.getEmail() : "Anonymous Guest");
        result.setTargetScheme(scheme.getSchemeName());

        Beneficiary beneficiary = user.getBeneficiary();
        if (beneficiary == null) {
            result.setEligible(false);
            result.setEligibilityScore(0);
            result.setRemarks("REJECTED: Demographic profile parameters missing.");
            return result;
        }

        String schemeName = scheme.getSchemeName().trim();
        String userOcc = beneficiary.getOccupation() != null ? beneficiary.getOccupation().trim() : "";
        int age = beneficiary.getAge();
        double income = beneficiary.getIncome();
        boolean passed = true;

        if (schemeName.equalsIgnoreCase("Education Subsidy")) {
            if (age < 18 || age > 30) {
                result.getFailedCriteria().add("Age floor/ceiling constraint match failed (Required: 18-30).");
                passed = false;
            } else {
                result.getPassedCriteria().add("Age check passed within 18-30 range.");
            }
            if (!userOcc.equalsIgnoreCase("Student")) {
                result.getFailedCriteria().add("Occupation mismatch: Educational grant tracking requires 'Student' profile tag.");
                passed = false;
            } else {
                result.getPassedCriteria().add("Verified 'Student' target occupation context.");
            }
        } else if (schemeName.equalsIgnoreCase("Farmer Support Grant")) {
            if (age < 18 || age > 65) {
                result.getFailedCriteria().add("Age constraint match failed (Required: 18-65).");
                passed = false;
            } else {
                result.getPassedCriteria().add("Age eligibility parameters verified.");
            }
            if (!userOcc.equalsIgnoreCase("Farmer")) {
                result.getFailedCriteria().add("Occupation profile drop: Agriculture pool requires 'Farmer' status.");
                passed = false;
            } else {
                result.getPassedCriteria().add("Verified primary agriculture producer status.");
            }
        } else if (schemeName.equalsIgnoreCase("Housing Assistance Scheme")) {
            if (age < 18 || age > 70) {
                result.getFailedCriteria().add("Age parameters out of bounds (Required: 18-70).");
                passed = false;
            } else {
                result.getPassedCriteria().add("Age parameter bounds passed.");
            }
        } else if (schemeName.equalsIgnoreCase("Women Entrepreneurship Grant")) {
            if (age < 18 || age > 60) {
                result.getFailedCriteria().add("Age parameter bounds match failed (Required: 18-60).");
                passed = false;
            } else {
                result.getPassedCriteria().add("Age parameter bounds approved.");
            }
            if (!userOcc.equalsIgnoreCase("Self Employed") && !userOcc.equalsIgnoreCase("Business")) {
                result.getFailedCriteria().add("Occupation profile mismatch: Requires commercial 'Self Employed' indicator.");
                passed = false;
            } else {
                result.getPassedCriteria().add("Verified commercial self-employment background.");
            }
        } else if (schemeName.equalsIgnoreCase("Senior Citizen Assistance")) {
            if (age < 60) {
                result.getFailedCriteria().add("Age threshold gate error: Floor minimum requires 60 years.");
                passed = false;
            } else {
                result.getPassedCriteria().add("Senior age status criteria passed.");
            }
        }

        if (income > scheme.getMaxIncomeThreshold()) {
            result.getFailedCriteria().add("Wealth limit exceeded: Income ₹" + income + " crosses scheme ceiling of ₹" + scheme.getMaxIncomeThreshold());
            passed = false;
        } else {
            result.getPassedCriteria().add("Income criteria verified within ceiling limit.");
        }

        if (beneficiary.getGovernmentId() == null || beneficiary.getGovernmentId().trim().isEmpty()) {
            result.getFailedCriteria().add("Identity validation fault: Missing government Aadhaar/PAN key.");
            passed = false;
        } else {
            result.getPassedCriteria().add("Government Identity check passed.");
        }

        result.setEligible(passed);
        if (passed) {
            double wealthRatio = income / scheme.getMaxIncomeThreshold();
            int compiledPriorityScore = (int) Math.round((1.0 - wealthRatio) * 100);
            if (compiledPriorityScore < 10) compiledPriorityScore = 15;
            result.setEligibilityScore(compiledPriorityScore);
            result.setRemarks("PASSED: Profile successfully metrics-validated. Candidate fast-tracked into system queue.");
        } else {
            result.setEligibilityScore(0);
            result.setRemarks("REJECTED: Pipeline threshold constraint violation. Request dropped from workflow queues.");
        }
        return result;
    }
}




//package com.platform.scheme;
//
//import com.platform.admin.SchemeAdminForm;
//import com.platform.application.EligibilityResult;
//import com.platform.application.SubsidyApplicationRepository;
//import com.platform.user.User;
//import com.platform.user.Beneficiary;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.stereotype.Service;
//import org.springframework.transaction.annotation.Transactional;
//
//import java.util.List;
//
//@Service
//public class SchemeService {
//
//    @Autowired
//    private SchemeRepository schemeRepository;
//
//    @Autowired
//    private SubsidyApplicationRepository applicationRepository;
//package com.platform.scheme;
//
//import com.platform.admin.SchemeAdminForm;
//import com.platform.application.EligibilityResult;
//import com.platform.application.SubsidyApplicationRepository;
//import com.platform.user.User;
//import com.platform.user.Beneficiary;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.stereotype.Service;
//import org.springframework.transaction.annotation.Transactional;
//
//import java.util.List;
//
//    @Service
//    public class SchemeService {
//
//        @Autowired
//        private SchemeRepository schemeRepository;
//
//        @Autowired
//        private SubsidyApplicationRepository applicationRepository;
//
//        public List<Scheme> getActiveSchemes() {
//            // There is currently no separate ACTIVE column in the existing schema.
//            // Keep this method name for backward compatibility with the citizen UI.
//            return schemeRepository.findAll();
//        }
//
//        public List<Scheme> getAllSchemes() {
//            return schemeRepository.findAll();
//        }
//
//        public Scheme getSchemeById(Long id) {
//            return schemeRepository.findById(id)
//                    .orElseThrow(() -> new RuntimeException("Scheme configuration missing for ID: " + id));
//        }
//
//        /**
//         * Creates or updates scheme master data from the administrator console.
//         *
//         * Important workflow protection:
//         * once applications exist for a scheme, the eligibility/scoring inputs are
//         * frozen so a later admin edit cannot silently change an applicant's score.
//         * Descriptive fields and budget can still be maintained.
//         */
//        @Transactional
//        public Scheme createOrUpdateFromAdmin(SchemeAdminForm form) {
//            validateForm(form);
//
//            Scheme scheme;
//            boolean creating = form.getId() == null;
//            if (creating) {
//                if (schemeRepository.findBySchemeNameIgnoreCase(form.getSchemeName().trim()).isPresent()) {
//                    throw new IllegalArgumentException("A scheme with this name already exists.");
//                }
//                scheme = new Scheme();
//                scheme.setRemainingBudget(form.getTotalRegionalBudget());
//            } else {
//                scheme = getSchemeById(form.getId());
//                schemeRepository.findBySchemeNameIgnoreCase(form.getSchemeName().trim())
//                        .filter(existing -> !existing.getId().equals(scheme.getId()))
//                        .ifPresent(existing -> {
//                            throw new IllegalArgumentException("A different scheme already uses this name.");
//                        });
//
//                long applicationCount = applicationRepository.countByScheme(scheme);
//                if (applicationCount > 0 && hasEligibilityConfigurationChanged(scheme, form)) {
//                    throw new IllegalStateException(
//                            "Eligibility criteria and document rules are locked because this scheme already has applications. " +
//                                    "This protects previously calculated eligibility decisions.");
//                }
//
//                double oldBudget = Math.max(0, scheme.getTotalRegionalBudget());
//                double oldRemaining = Math.max(0, scheme.getRemainingBudget());
//                double utilized = Math.max(0, oldBudget - oldRemaining);
//                if (form.getTotalRegionalBudget() + 0.01 < utilized) {
//                    throw new IllegalArgumentException(
//                            "New regional budget cannot be lower than funds already committed/utilized (₹"
//                                    + String.format("%,.2f", utilized) + ").");
//                }
//                scheme.setRemainingBudget(round(form.getTotalRegionalBudget() - utilized));
//            }
//
//            scheme.setSchemeName(form.getSchemeName().trim());
//            scheme.setEmoji(clean(form.getEmoji(), "📋"));
//            scheme.setDescription(clean(form.getDescription(), null));
//            scheme.setBenefitSummary(clean(form.getBenefitSummary(), null));
//            scheme.setTargetBeneficiary(clean(form.getTargetBeneficiary(), null));
//            scheme.setEligibleAgeMin(form.getEligibleAgeMin());
//            scheme.setEligibleAgeMax(form.getEligibleAgeMax());
//            scheme.setMaxIncomeThreshold(form.getMaxIncomeThreshold());
//            scheme.setTotalRegionalBudget(form.getTotalRegionalBudget());
//            scheme.setRequiredOccupation(clean(form.getRequiredOccupation(), null));
//            scheme.setRequiredGender(clean(form.getRequiredGender(), null));
//            scheme.setRequiresDisability(form.isRequiresDisability());
//            scheme.setRequiredResidenceType(clean(form.getRequiredResidenceType(), null));
//            scheme.setRequiredDocuments(clean(form.getRequiredDocuments(), null));
//            scheme.setOptionalDocuments(clean(form.getOptionalDocuments(), null));
//
//            // On a newly-created scheme the remaining budget was already initialized above.
//            if (creating) {
//                scheme.setRemainingBudget(form.getTotalRegionalBudget());
//            }
//
//            return schemeRepository.save(scheme);
//        }
//
//        @Transactional
//        public void deleteByAdmin(Long id) {
//            Scheme scheme = getSchemeById(id);
//            long applicationCount = applicationRepository.countByScheme(scheme);
//            if (applicationCount > 0) {
//                throw new IllegalStateException(
//                        "This scheme cannot be deleted because " + applicationCount
//                                + " application(s) are linked to it. Archive/deactivate support can be added later without destroying history.");
//            }
//            schemeRepository.delete(scheme);
//        }
//
//        private void validateForm(SchemeAdminForm form) {
//            if (form.getSchemeName() == null || form.getSchemeName().isBlank()) {
//                throw new IllegalArgumentException("Scheme name is required.");
//            }
//            if (form.getEligibleAgeMin() == null || form.getEligibleAgeMax() == null
//                    || form.getEligibleAgeMin() > form.getEligibleAgeMax()) {
//                throw new IllegalArgumentException("Eligible minimum age cannot be greater than maximum age.");
//            }
//            if (form.getMaxIncomeThreshold() == null || form.getMaxIncomeThreshold() < 0) {
//                throw new IllegalArgumentException("Maximum income threshold cannot be negative.");
//            }
//            if (form.getTotalRegionalBudget() == null || form.getTotalRegionalBudget() <= 0) {
//                throw new IllegalArgumentException("Regional budget must be greater than zero.");
//            }
//        }
//
//        private boolean hasEligibilityConfigurationChanged(Scheme scheme, SchemeAdminForm form) {
//            return scheme.getEligibleAgeMin() != form.getEligibleAgeMin()
//                    || scheme.getEligibleAgeMax() != form.getEligibleAgeMax()
//                    || !sameDouble(scheme.getMaxIncomeThreshold(), form.getMaxIncomeThreshold())
//                    || !sameText(scheme.getRequiredOccupation(), form.getRequiredOccupation())
//                    || !sameText(scheme.getRequiredGender(), form.getRequiredGender())
//                    || scheme.isRequiresDisability() != form.isRequiresDisability()
//                    || !sameText(scheme.getRequiredResidenceType(), form.getRequiredResidenceType())
//                    || !sameText(scheme.getRequiredDocuments(), form.getRequiredDocuments())
//                    || !sameText(scheme.getOptionalDocuments(), form.getOptionalDocuments());
//        }
//
//        private boolean sameText(String a, String b) {
//            return clean(a, "").equals(clean(b, ""));
//        }
//
//        private boolean sameDouble(double a, Double b) {
//            return b != null && Math.abs(a - b) <= 0.0001;
//        }
//
//        private String clean(String value, String fallback) {
//            if (value == null || value.isBlank()) return fallback;
//            return value.trim();
//        }
//
//        private double round(double value) {
//            return Math.round(value * 100.0) / 100.0;
//        }
//
//        /**
//         * Existing legacy eligibility method intentionally retained unchanged.
//         */
//        public EligibilityResult checkEligibility(User user, Scheme scheme) {
//            EligibilityResult result = new EligibilityResult();
//            result.setUserEmail(user.getEmail() != null ? user.getEmail() : "Anonymous Guest");
//            result.setTargetScheme(scheme.getSchemeName());
//
//            Beneficiary beneficiary = user.getBeneficiary();
//            if (beneficiary == null) {
//                result.setEligible(false);
//                result.setEligibilityScore(0);
//                result.setRemarks("REJECTED: Demographic profile parameters missing.");
//                return result;
//            }
//
//            String schemeName = scheme.getSchemeName().trim();
//            String userOcc = beneficiary.getOccupation() != null ? beneficiary.getOccupation().trim() : "";
//            int age = beneficiary.getAge();
//            double income = beneficiary.getIncome();
//            boolean passed = true;
//
//            if (schemeName.equalsIgnoreCase("Education Subsidy")) {
//                if (age < 18 || age > 30) {
//                    result.getFailedCriteria().add("Age floor/ceiling constraint match failed (Required: 18-30).");
//                    passed = false;
//                } else {
//                    result.getPassedCriteria().add("Age check passed within 18-30 range.");
//                }
//                if (!userOcc.equalsIgnoreCase("Student")) {
//                    result.getFailedCriteria().add("Occupation mismatch: Educational grant tracking requires 'Student' profile tag.");
//                    passed = false;
//                } else {
//                    result.getPassedCriteria().add("Verified 'Student' target occupation context.");
//                }
//            } else if (schemeName.equalsIgnoreCase("Farmer Support Grant")) {
//                if (age < 18 || age > 65) {
//                    result.getFailedCriteria().add("Age constraint match failed (Required: 18-65).");
//                    passed = false;
//                } else {
//                    result.getPassedCriteria().add("Age eligibility parameters verified.");
//                }
//                if (!userOcc.equalsIgnoreCase("Farmer")) {
//                    result.getFailedCriteria().add("Occupation profile drop: Agriculture pool requires 'Farmer' status.");
//                    passed = false;
//                } else {
//                    result.getPassedCriteria().add("Verified primary agriculture producer status.");
//                }
//            } else if (schemeName.equalsIgnoreCase("Housing Assistance Scheme")) {
//                if (age < 18 || age > 70) {
//                    result.getFailedCriteria().add("Age parameters out of bounds (Required: 18-70).");
//                    passed = false;
//                } else {
//                    result.getPassedCriteria().add("Age parameter bounds passed.");
//                }
//            } else if (schemeName.equalsIgnoreCase("Women Entrepreneurship Grant")) {
//                if (age < 18 || age > 60) {
//                    result.getFailedCriteria().add("Age parameter bounds match failed (Required: 18-60).");
//                    passed = false;
//                } else {
//                    result.getPassedCriteria().add("Age parameter bounds approved.");
//                }
//                if (!userOcc.equalsIgnoreCase("Self Employed") && !userOcc.equalsIgnoreCase("Business")) {
//                    result.getFailedCriteria().add("Occupation profile mismatch: Requires commercial 'Self Employed' indicator.");
//                    passed = false;
//                } else {
//                    result.getPassedCriteria().add("Verified commercial self-employment background.");
//                }
//            } else if (schemeName.equalsIgnoreCase("Senior Citizen Assistance")) {
//                if (age < 60) {
//                    result.getFailedCriteria().add("Age threshold gate error: Floor minimum requires 60 years.");
//                    passed = false;
//                } else {
//                    result.getPassedCriteria().add("Senior age status criteria passed.");
//                }
//            }
//
//            if (income > scheme.getMaxIncomeThreshold()) {
//                result.getFailedCriteria().add("Wealth limit exceeded: Income ₹" + income + " crosses scheme ceiling of ₹" + scheme.getMaxIncomeThreshold());
//                passed = false;
//            } else {
//                result.getPassedCriteria().add("Income criteria verified within ceiling limit.");
//            }
//
//            if (beneficiary.getGovernmentId() == null || beneficiary.getGovernmentId().trim().isEmpty()) {
//                result.getFailedCriteria().add("Identity validation fault: Missing government Aadhaar/PAN key.");
//                passed = false;
//            } else {
//                result.getPassedCriteria().add("Government Identity check passed.");
//            }
//
//            result.setEligible(passed);
//            if (passed) {
//                double wealthRatio = income / scheme.getMaxIncomeThreshold();
//                int compiledPriorityScore = (int) Math.round((1.0 - wealthRatio) * 100);
//                if (compiledPriorityScore < 10) compiledPriorityScore = 15;
//                result.setEligibilityScore(compiledPriorityScore);
//                result.setRemarks("PASSED: Profile successfully metrics-validated. Candidate fast-tracked into system queue.");
//            } else {
//                result.setEligibilityScore(0);
//                result.setRemarks("REJECTED: Pipeline threshold constraint violation. Request dropped from workflow queues.");
//            }
//            return result;
//        }
//    }
//
//    public List<Scheme> getActiveSchemes() {
//        // There is currently no separate ACTIVE column in the existing schema.
//        // Keep this method name for backward compatibility with the citizen UI.
//        return schemeRepository.findAll();
//    }
//
//    public List<Scheme> getAllSchemes() {
//        return schemeRepository.findAll();
//    }
//
//    public Scheme getSchemeById(Long id) {
//        return schemeRepository.findById(id)
//                .orElseThrow(() -> new RuntimeException("Scheme configuration missing for ID: " + id));
//    }
//
//    /**
//     * Creates or updates scheme master data from the administrator console.
//     *
//     * Important workflow protection:
//     * once applications exist for a scheme, the eligibility/scoring inputs are
//     * frozen so a later admin edit cannot silently change an applicant's score.
//     * Descriptive fields and budget can still be maintained.
//     */
//    @Transactional
//    public Scheme createOrUpdateFromAdmin(SchemeAdminForm form) {
//        validateForm(form);
//
//        Scheme scheme;
//        boolean creating = form.getId() == null;
//        if (creating) {
//            if (schemeRepository.findBySchemeNameIgnoreCase(form.getSchemeName().trim()).isPresent()) {
//                throw new IllegalArgumentException("A scheme with this name already exists.");
//            }
//            scheme = new Scheme();
//            scheme.setRemainingBudget(form.getTotalRegionalBudget());
//        } else {
//            scheme = getSchemeById(form.getId());
//            schemeRepository.findBySchemeNameIgnoreCase(form.getSchemeName().trim())
//                    .filter(existing -> !existing.getId().equals(scheme.getId()))
//                    .ifPresent(existing -> {
//                        throw new IllegalArgumentException("A different scheme already uses this name.");
//                    });
//
//            long applicationCount = applicationRepository.countByScheme(scheme);
//            if (applicationCount > 0 && hasEligibilityConfigurationChanged(scheme, form)) {
//                throw new IllegalStateException(
//                        "Eligibility criteria and document rules are locked because this scheme already has applications. " +
//                                "This protects previously calculated eligibility decisions.");
//            }
//
//            double oldBudget = Math.max(0, scheme.getTotalRegionalBudget());
//            double oldRemaining = Math.max(0, scheme.getRemainingBudget());
//            double utilized = Math.max(0, oldBudget - oldRemaining);
//            if (form.getTotalRegionalBudget() + 0.01 < utilized) {
//                throw new IllegalArgumentException(
//                        "New regional budget cannot be lower than funds already committed/utilized (₹"
//                                + String.format("%,.2f", utilized) + ").");
//            }
//            scheme.setRemainingBudget(round(form.getTotalRegionalBudget() - utilized));
//        }
//
//        scheme.setSchemeName(form.getSchemeName().trim());
//        scheme.setEmoji(clean(form.getEmoji(), "📋"));
//        scheme.setDescription(clean(form.getDescription(), null));
//        scheme.setBenefitSummary(clean(form.getBenefitSummary(), null));
//        scheme.setTargetBeneficiary(clean(form.getTargetBeneficiary(), null));
//        scheme.setEligibleAgeMin(form.getEligibleAgeMin());
//        scheme.setEligibleAgeMax(form.getEligibleAgeMax());
//        scheme.setMaxIncomeThreshold(form.getMaxIncomeThreshold());
//        scheme.setTotalRegionalBudget(form.getTotalRegionalBudget());
//        scheme.setRequiredOccupation(clean(form.getRequiredOccupation(), null));
//        scheme.setRequiredGender(clean(form.getRequiredGender(), null));
//        scheme.setRequiresDisability(form.isRequiresDisability());
//        scheme.setRequiredResidenceType(clean(form.getRequiredResidenceType(), null));
//        scheme.setRequiredDocuments(clean(form.getRequiredDocuments(), null));
//        scheme.setOptionalDocuments(clean(form.getOptionalDocuments(), null));
//
//        // On a newly-created scheme the remaining budget was already initialized above.
//        if (creating) {
//            scheme.setRemainingBudget(form.getTotalRegionalBudget());
//        }
//
//        return schemeRepository.save(scheme);
//    }
//
//    @Transactional
//    public void deleteByAdmin(Long id) {
//        Scheme scheme = getSchemeById(id);
//        long applicationCount = applicationRepository.countByScheme(scheme);
//        if (applicationCount > 0) {
//            throw new IllegalStateException(
//                    "This scheme cannot be deleted because " + applicationCount
//                            + " application(s) are linked to it. Archive/deactivate support can be added later without destroying history.");
//        }
//        schemeRepository.delete(scheme);
//    }
//
//    private void validateForm(SchemeAdminForm form) {
//        if (form.getSchemeName() == null || form.getSchemeName().isBlank()) {
//            throw new IllegalArgumentException("Scheme name is required.");
//        }
//        if (form.getEligibleAgeMin() == null || form.getEligibleAgeMax() == null
//                || form.getEligibleAgeMin() > form.getEligibleAgeMax()) {
//            throw new IllegalArgumentException("Eligible minimum age cannot be greater than maximum age.");
//        }
//        if (form.getMaxIncomeThreshold() == null || form.getMaxIncomeThreshold() < 0) {
//            throw new IllegalArgumentException("Maximum income threshold cannot be negative.");
//        }
//        if (form.getTotalRegionalBudget() == null || form.getTotalRegionalBudget() <= 0) {
//            throw new IllegalArgumentException("Regional budget must be greater than zero.");
//        }
//    }
//
//    private boolean hasEligibilityConfigurationChanged(Scheme scheme, SchemeAdminForm form) {
//        return scheme.getEligibleAgeMin() != form.getEligibleAgeMin()
//                || scheme.getEligibleAgeMax() != form.getEligibleAgeMax()
//                || !sameDouble(scheme.getMaxIncomeThreshold(), form.getMaxIncomeThreshold())
//                || !sameText(scheme.getRequiredOccupation(), form.getRequiredOccupation())
//                || !sameText(scheme.getRequiredGender(), form.getRequiredGender())
//                || scheme.isRequiresDisability() != form.isRequiresDisability()
//                || !sameText(scheme.getRequiredResidenceType(), form.getRequiredResidenceType())
//                || !sameText(scheme.getRequiredDocuments(), form.getRequiredDocuments())
//                || !sameText(scheme.getOptionalDocuments(), form.getOptionalDocuments());
//    }
//
//    private boolean sameText(String a, String b) {
//        return clean(a, "").equals(clean(b, ""));
//    }
//
//    private boolean sameDouble(double a, Double b) {
//        return b != null && Math.abs(a - b) <= 0.0001;
//    }
//
//    private String clean(String value, String fallback) {
//        if (value == null || value.isBlank()) return fallback;
//        return value.trim();
//    }
//
//    private double round(double value) {
//        return Math.round(value * 100.0) / 100.0;
//    }
//
//    /**
//     * Existing legacy eligibility method intentionally retained unchanged.
//     */
//    public EligibilityResult checkEligibility(User user, Scheme scheme) {
//        EligibilityResult result = new EligibilityResult();
//        result.setUserEmail(user.getEmail() != null ? user.getEmail() : "Anonymous Guest");
//        result.setTargetScheme(scheme.getSchemeName());
//
//        Beneficiary beneficiary = user.getBeneficiary();
//        if (beneficiary == null) {
//            result.setEligible(false);
//            result.setEligibilityScore(0);
//            result.setRemarks("REJECTED: Demographic profile parameters missing.");
//            return result;
//        }
//
//        String schemeName = scheme.getSchemeName().trim();
//        String userOcc = beneficiary.getOccupation() != null ? beneficiary.getOccupation().trim() : "";
//        int age = beneficiary.getAge();
//        double income = beneficiary.getIncome();
//        boolean passed = true;
//
//        if (schemeName.equalsIgnoreCase("Education Subsidy")) {
//            if (age < 18 || age > 30) {
//                result.getFailedCriteria().add("Age floor/ceiling constraint match failed (Required: 18-30).");
//                passed = false;
//            } else {
//                result.getPassedCriteria().add("Age check passed within 18-30 range.");
//            }
//            if (!userOcc.equalsIgnoreCase("Student")) {
//                result.getFailedCriteria().add("Occupation mismatch: Educational grant tracking requires 'Student' profile tag.");
//                passed = false;
//            } else {
//                result.getPassedCriteria().add("Verified 'Student' target occupation context.");
//            }
//        } else if (schemeName.equalsIgnoreCase("Farmer Support Grant")) {
//            if (age < 18 || age > 65) {
//                result.getFailedCriteria().add("Age constraint match failed (Required: 18-65).");
//                passed = false;
//            } else {
//                result.getPassedCriteria().add("Age eligibility parameters verified.");
//            }
//            if (!userOcc.equalsIgnoreCase("Farmer")) {
//                result.getFailedCriteria().add("Occupation profile drop: Agriculture pool requires 'Farmer' status.");
//                passed = false;
//            } else {
//                result.getPassedCriteria().add("Verified primary agriculture producer status.");
//            }
//        } else if (schemeName.equalsIgnoreCase("Housing Assistance Scheme")) {
//            if (age < 18 || age > 70) {
//                result.getFailedCriteria().add("Age parameters out of bounds (Required: 18-70).");
//                passed = false;
//            } else {
//                result.getPassedCriteria().add("Age parameter bounds passed.");
//            }
//        } else if (schemeName.equalsIgnoreCase("Women Entrepreneurship Grant")) {
//            if (age < 18 || age > 60) {
//                result.getFailedCriteria().add("Age parameter bounds match failed (Required: 18-60).");
//                passed = false;
//            } else {
//                result.getPassedCriteria().add("Age parameter bounds approved.");
//            }
//            if (!userOcc.equalsIgnoreCase("Self Employed") && !userOcc.equalsIgnoreCase("Business")) {
//                result.getFailedCriteria().add("Occupation profile mismatch: Requires commercial 'Self Employed' indicator.");
//                passed = false;
//            } else {
//                result.getPassedCriteria().add("Verified commercial self-employment background.");
//            }
//        } else if (schemeName.equalsIgnoreCase("Senior Citizen Assistance")) {
//            if (age < 60) {
//                result.getFailedCriteria().add("Age threshold gate error: Floor minimum requires 60 years.");
//                passed = false;
//            } else {
//                result.getPassedCriteria().add("Senior age status criteria passed.");
//            }
//        }
//
//        if (income > scheme.getMaxIncomeThreshold()) {
//            result.getFailedCriteria().add("Wealth limit exceeded: Income ₹" + income + " crosses scheme ceiling of ₹" + scheme.getMaxIncomeThreshold());
//            passed = false;
//        } else {
//            result.getPassedCriteria().add("Income criteria verified within ceiling limit.");
//        }
//
//        if (beneficiary.getGovernmentId() == null || beneficiary.getGovernmentId().trim().isEmpty()) {
//            result.getFailedCriteria().add("Identity validation fault: Missing government Aadhaar/PAN key.");
//            passed = false;
//        } else {
//            result.getPassedCriteria().add("Government Identity check passed.");
//        }
//
//        result.setEligible(passed);
//        if (passed) {
//            double wealthRatio = income / scheme.getMaxIncomeThreshold();
//            int compiledPriorityScore = (int) Math.round((1.0 - wealthRatio) * 100);
//            if (compiledPriorityScore < 10) compiledPriorityScore = 15;
//            result.setEligibilityScore(compiledPriorityScore);
//            result.setRemarks("PASSED: Profile successfully metrics-validated. Candidate fast-tracked into system queue.");
//        } else {
//            result.setEligibilityScore(0);
//            result.setRemarks("REJECTED: Pipeline threshold constraint violation. Request dropped from workflow queues.");
//        }
//        return result;
//    }
//}
