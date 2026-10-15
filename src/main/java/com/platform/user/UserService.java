package com.platform.user;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Transactional
    public User registerUser(User user) {
        Beneficiary beneficiary = user.getBeneficiary();
        if (beneficiary == null) {
            throw new UserRegistrationException("Registration Failed: Profile demographic data missing.");
        }

        if (beneficiary.getAge() <= 0 || beneficiary.getAge() > 120) {
            throw new UserRegistrationException("Registration Failed: Invalid age values.");
        }

        if (userRepository.findByEmail(user.getEmail()).isPresent()) {
            throw new UserRegistrationException("Registration Failed: Email is already registered!");
        }

        if (userRepository.existsByGovernmentId(beneficiary.getGovernmentId())) {
            throw new UserRegistrationException("Registration Failed: Government ID already exists!");
        }

        // Encrypt the password securely before saving
        user.setPassword(passwordEncoder.encode(user.getPassword()));

        // 🔍 FIXED: Utilizing the initialized mutable Set collection to safely append roles on a clean insert statement path
        if (user.getRoles() == null) {
            user.setRoles(new java.util.HashSet<>());
        }
        user.getRoles().add("ROLE_BENEFICIARY");

        // Saves BOTH tables safely due to CascadeType.ALL rules
        return userRepository.save(user);
    }
    // 🔍 HELPER METHOD: Allows the backend to safely hash default credentials
    public String registerUserPassword(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }

}
