package com.platform.scheme;

import com.platform.user.User;
import com.platform.user.UserRepository;
import com.platform.application.SubsidyApplicationRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;

@Configuration
public class SchemeDataLoader {

    @Autowired
    private SubsidyApplicationRepository applicationRepository;

    @Value("${sagar.seed.demo-officers:false}")
    private boolean seedDemoOfficers;

    @Bean
    CommandLineRunner loadSchemes(SchemeRepository schemeRepository,
                                  UserRepository userRepository,
                                  PasswordEncoder passwordEncoder) {
        return args -> {

            upsertScheme(schemeRepository, "Education Subsidy", "🎓",
                    "A central government scheme providing financial assistance to meritorious students from economically weaker sections enrolled in recognized institutions. The grant covers tuition fees, books, and educational materials.",
                    "Up to ₹50,000 per academic year — direct bank transfer",
                    "Students aged 18–30 from low-income families (Annual income ≤ ₹5,00,000)",
                    18, 30, 500000.0, 5000000.0, 50000.0,
                    "Student", null, false, null,
                    "Aadhaar Card,Bank Passbook,Student ID / Admission Proof,Income Certificate",
                    "Previous Year Marksheet,College Fee Receipt,Scholarship Certificate");

            upsertScheme(schemeRepository, "Farmer Support Grant", "🌾",
                    "Financial support for small and marginal farmers to purchase seeds, fertilizers, and agricultural equipment. The grant helps improve crop yield and income stability for rural farming families.",
                    "Up to ₹1,00,000 per harvest season — direct bank transfer",
                    "Farmers and agriculturists aged 18–65 with annual household income ≤ ₹4,00,000",
                    18, 65, 400000.0, 8000000.0, 100000.0,
                    "Farmer,Agriculturist", null, false, null,
                    "Aadhaar Card,Bank Passbook,Land Ownership Document,Kisan Credit Card",
                    "Soil Health Card,Crop Insurance Certificate,PM-KISAN Registration Proof");

            upsertScheme(schemeRepository, "Housing Assistance Scheme", "🏠",
                    "Provides a one-time grant for construction or renovation of pucca housing for eligible families who currently live in kutcha or semi-pucca structures. Applicable to families without ownership of a pucca house.",
                    "Up to ₹2,00,000 for construction · ₹1,00,000 for renovation",
                    "Adults 21–70 with annual income ≤ ₹3,00,000 who do not own a pucca house",
                    21, 70, 300000.0, 12000000.0, 200000.0,
                    null, null, false, null,
                    "Aadhaar Card,Bank Passbook,Income Certificate,Residence Proof,Property Documents",
                    "Photograph of existing dwelling,No-objection certificate from local body");

            upsertScheme(schemeRepository, "Women Entrepreneurship Grant", "💼",
                    "Supports women entrepreneurs in establishing or expanding their small businesses. The grant covers startup costs, equipment purchase, working capital, and skill development training. Previous government loans must not be in default.",
                    "Up to ₹5,00,000 — phased disbursement based on business milestones",
                    "Women aged 18–55, self-employed or entrepreneur, with annual income ≤ ₹8,00,000",
                    18, 55, 800000.0, 6000000.0, 500000.0,
                    null, "Female", false, null,
                    "Aadhaar Card,Bank Passbook,Business Registration Certificate,Business Plan",
                    "GST Registration,Trade License,Previous Business Turnover Statement");

            upsertScheme(schemeRepository, "Senior Citizen Assistance", "👴",
                    "Monthly pension support and medical reimbursement for senior citizens aged 60 and above who are retired or not employed and have limited income. The scheme also provides access to healthcare and rehabilitation services.",
                    "₹2,000–₹5,000/month + medical reimbursement up to ₹25,000/year",
                    "Senior citizens aged 60+ with annual income ≤ ₹3,00,000, retired or unemployed",
                    60, 100, 300000.0, 4000000.0, 25000.0,
                    null, null, false, null,
                    "Aadhaar Card,Bank Passbook,Age Proof,Income Certificate,Residence Proof",
                    "Medical Records,Pension Statement,Disability Certificate (if applicable)");

            upsertScheme(schemeRepository, "Divyang Disability Support", "♿",
                    "Comprehensive support for persons with disabilities (PwD) including financial assistance for assistive devices, rehabilitation, vocational training, and monthly living allowance. Applicant must hold a valid disability certificate with ≥40% disability.",
                    "Up to ₹1,50,000 for assistive devices + ₹1,500/month living allowance",
                    "Persons with Disabilities aged 18–80 with annual income ≤ ₹3,50,000",
                    18, 80, 350000.0, 3500000.0, 150000.0,
                    null, null, true, null,
                    "Aadhaar Card,Bank Passbook,Disability Certificate (UDID),Income Certificate",
                    "Medical Prescription for Assistive Device,Vocational Training Certificate");

            upsertScheme(schemeRepository, "Rural Skill Development Grant", "🔧",
                    "Skill development and vocational training support for rural youth to improve their employability and income. Covers training fees, certification costs, and a stipend during the training period.",
                    "Up to ₹75,000 for training + ₹500/month stipend during training",
                    "Rural youth aged 18–45 with annual income ≤ ₹4,50,000",
                    18, 45, 450000.0, 7000000.0, 75000.0,
                    null, null, false, "Rural",
                    "Aadhaar Card,Bank Passbook,Residence Proof (Rural),Income Certificate",
                    "Skill Assessment Certificate,Enrolment Letter from Training Institute");

            System.out.println(">> Master Scheme Rules Sync Complete. 7 schemes verified/updated.");

            // Demo credentials must never be created automatically in production.
            if (seedDemoOfficers) {
                // Always ensure officer accounts exist — regardless of citizen registrations.
                ensureOfficer(userRepository, passwordEncoder, "field.officer@gmail.com",    "field123",    "ROLE_FIELD_OFFICER");
                ensureOfficer(userRepository, passwordEncoder, "district.officer@gmail.com", "district123", "ROLE_DISTRICT_OFFICER");
                ensureOfficer(userRepository, passwordEncoder, "finance.approver@gmail.com", "finance123",  "ROLE_FINANCE_APPROVER");
                ensureOfficer(userRepository, passwordEncoder, "disbursement.officer@gmail.com", "disburse123", "ROLE_DISBURSEMENT_OFFICER");
                ensureOfficer(userRepository, passwordEncoder, "sagar.subsidy.admin@gmail.com", "admin123", "ROLE_ADMIN");
                System.out.println(">> Officer and administrator accounts verified.");
            }
        };
    }

    private void upsertScheme(SchemeRepository repo, String name, String emoji,
                              String description, String benefitSummary, String targetBeneficiary,
                              int ageMin, int ageMax, double maxIncome, double totalBudget, double maxSanctionAmount,
                              String requiredOccupation, String requiredGender,
                              boolean requiresDisability, String requiredResidenceType,
                              String requiredDocuments, String optionalDocuments) {
        java.util.Optional<Scheme> existing = repo.findBySchemeName(name);
        if (existing.isPresent()) {
            // Do not overwrite administrator-managed scheme configuration on every startup.
            // The loader is now seed-only for existing schemes.
            Scheme scheme = existing.get();
            boolean changed = false;

            // Backfill ONLY missing document rules in older/local databases.
            // Existing configured values are never overwritten.
            if ((scheme.getRequiredDocuments() == null || scheme.getRequiredDocuments().isBlank())
                    && requiredDocuments != null && !requiredDocuments.isBlank()) {
                scheme.setRequiredDocuments(requiredDocuments);
                changed = true;
            }
            if ((scheme.getOptionalDocuments() == null || scheme.getOptionalDocuments().isBlank())
                    && optionalDocuments != null && !optionalDocuments.isBlank()) {
                scheme.setOptionalDocuments(optionalDocuments);
                changed = true;
            }

            if (changed) {
                repo.save(scheme);
            }

            // Backfill the new per-application sanction ceiling for existing databases.
            if (scheme.getMaxSanctionAmount() <= 0 && maxSanctionAmount > 0) {
                scheme.setMaxSanctionAmount(maxSanctionAmount);
                repo.save(scheme);
            }
            if (scheme.getRemainingBudget() <= 0 && scheme.getTotalRegionalBudget() > 0) {
                // Legacy databases created before remainingBudget was populated.
                // Do not resurrect an actually-used budget when applications already exist.
                if (applicationRepository.countByScheme(scheme) == 0) {
                    scheme.setRemainingBudget(scheme.getTotalRegionalBudget());
                    repo.save(scheme);
                }
            }
            return;
        }

        Scheme scheme = new Scheme();
        boolean isNew = true;
        scheme.setSchemeName(name);
        scheme.setEmoji(emoji);
        scheme.setDescription(description);
        scheme.setBenefitSummary(benefitSummary);
        scheme.setTargetBeneficiary(targetBeneficiary);
        scheme.setEligibleAgeMin(ageMin);
        scheme.setEligibleAgeMax(ageMax);
        scheme.setMaxIncomeThreshold(maxIncome);
        scheme.setTotalRegionalBudget(totalBudget);
        scheme.setMaxSanctionAmount(maxSanctionAmount);
        scheme.setRequiredOccupation(requiredOccupation);
        scheme.setRequiredGender(requiredGender);
        scheme.setRequiresDisability(requiresDisability);
        scheme.setRequiredResidenceType(requiredResidenceType);
        scheme.setRequiredDocuments(requiredDocuments);
        scheme.setOptionalDocuments(optionalDocuments);
        if (isNew) scheme.setRemainingBudget(totalBudget);
        repo.save(scheme);
    }

    private void ensureOfficer(UserRepository repo, PasswordEncoder encoder,
                               String email, String rawPassword, String role) {
        Optional<User> existing = repo.findByEmail(email);
        if (existing.isEmpty()) {
            User u = new User();
            u.setEmail(email);
            u.setPassword(encoder.encode(rawPassword));
            u.setRoles(new HashSet<>(Collections.singletonList(role)));
            repo.save(u);
            System.out.println(">> Created account: " + email + " [" + role + "]");
            return;
        }

        User u = existing.get();
        if (u.getRoles() == null) u.setRoles(new HashSet<>());
        if (!u.getRoles().contains(role)) {
            u.getRoles().add(role);
            repo.save(u);
            System.out.println(">> Added role " + role + " to account: " + email);
        }
    }
}