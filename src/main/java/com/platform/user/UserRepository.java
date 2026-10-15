package com.platform.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    boolean existsByUsernameIgnoreCase(String username);

    // Verifies the field match across your OneToOne mapping layer
    @Query("SELECT COUNT(u) > 0 FROM User u WHERE u.beneficiary.governmentId = :govId")
    boolean existsByGovernmentId(@Param("govId") String govId);
}
