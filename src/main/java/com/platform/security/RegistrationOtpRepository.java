package com.platform.security;

import com.platform.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RegistrationOtpRepository extends JpaRepository<RegistrationOtp, Long> {
    Optional<RegistrationOtp> findTopByUserAndUsedFalseOrderByCreatedAtDesc(User user);
    List<RegistrationOtp> findByUserAndUsedFalse(User user);
}
