package com.platform.user;

import org.springframework.stereotype.Service;

@Service
public class BeneficiaryService {

    private final UserRepository userRepository;
    private final BeneficiaryRepository beneficiaryRepository;

    // Standard constructor injection for your repositories
    public BeneficiaryService(UserRepository userRepository, BeneficiaryRepository beneficiaryRepository) {
        this.userRepository = userRepository;
        this.beneficiaryRepository = beneficiaryRepository;
    }

    /**
     * Verifies a beneficiary's profile criteria by correctly pulling metrics
     * from the linked bidirectional demographic relationship.
     */
    public String verifyBeneficiary(Long userId) {
        // 1. Fetch user account credentials safely
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User profile session not found."));

        // 2. Safely grab the child demographics profile linked via your OneToOne map
        Beneficiary beneficiary = user.getBeneficiary();
        if (beneficiary == null) {
            return "REJECTED: Demographic profile dataset missing.";
        }

        // 3. FIX: Read validation parameters directly from the Beneficiary model fields
        boolean verified = beneficiary.getGovernmentId() != null &&
                !beneficiary.getGovernmentId().trim().isEmpty() &&
                beneficiary.getAge() >= 18 &&
                beneficiary.getContactNumber() != null &&
                !beneficiary.getContactNumber().trim().isEmpty();

        // 4. Update the state or pass back clean workflow tracking feedback strings
        if (verified) {
            return "VERIFIED: Beneficiary pipeline criteria validated successfully.";
        }

        return "REJECTED: Profile configuration failed identity minimum constraints.";
    }
}
