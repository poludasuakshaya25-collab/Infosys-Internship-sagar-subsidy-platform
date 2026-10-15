package com.platform.security;

import com.platform.application.SubsidyApplication;
import com.platform.user.Beneficiary;
import com.platform.user.User;
import com.platform.user.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class JurisdictionService {
    private final UserRepository userRepository;

    public JurisdictionService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public boolean canAccess(UserDetails principal, SubsidyApplication app) {
        if (principal == null || app == null) return false;
        User officer = userRepository.findByEmail(principal.getUsername()).orElse(null);
        if (officer == null) return false;

        if (hasRole(officer, "ROLE_ADMIN")) return true;

        boolean field = hasRole(officer, "ROLE_FIELD_OFFICER");
        boolean districtOfficer = hasRole(officer, "ROLE_DISTRICT_OFFICER");
        boolean finance = hasRole(officer, "ROLE_FINANCE_APPROVER") || hasRole(officer, "ROLE_DISBURSEMENT_OFFICER");
        if (!field && !districtOfficer && !finance) return false;

        // Milestones 1-3 seeded/test officers had no jurisdiction columns.
        // Keep those legacy accounts working until Admin replaces them with managed accounts.
        String assignedState = clean(officer.getAssignedState());
        String assignedDistrict = clean(officer.getAssignedDistrict());
        if (assignedState == null) return true;

        Beneficiary citizen = app.getUser() == null ? null : app.getUser().getBeneficiary();
        if (citizen == null) return false;

        if (!same(assignedState, citizen.getState())) return false;

        // Finance is state-scoped. Field/District are state + district scoped.
        if (finance && !field && !districtOfficer) return true;

        // Old citizen rows created before 4A.2 have no district. State check is the
        // backward-compatible fallback; all newly registered citizens have district.
        String citizenDistrict = clean(citizen.getDistrict());
        if (assignedDistrict == null || citizenDistrict == null) return true;
        return same(assignedDistrict, citizenDistrict);
    }

    public void requireAccess(UserDetails principal, SubsidyApplication app) {
        if (!canAccess(principal, app)) {
            throw new SecurityException("This application is outside your assigned jurisdiction.");
        }
    }

    public List<SubsidyApplication> filter(UserDetails principal, List<SubsidyApplication> applications) {
        return applications.stream().filter(a -> canAccess(principal, a)).toList();
    }

    private boolean hasRole(User user, String role) {
        return user.getRoles() != null && user.getRoles().stream().anyMatch(r -> role.equalsIgnoreCase(r));
    }

    private boolean same(String a, String b) {
        return clean(a) != null && clean(b) != null && clean(a).equalsIgnoreCase(clean(b));
    }

    private String clean(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        return value.trim();
    }
}
