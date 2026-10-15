package com.platform.security;

import com.platform.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LoginOtpRepository extends JpaRepository<LoginOtp, Long> {
    Optional<LoginOtp> findTopByUserAndUsedFalseOrderByCreatedAtDesc(User user);
    List<LoginOtp> findByUserAndUsedFalse(User user);
}
