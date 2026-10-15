package com.platform.user;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface OfficerInvitationRepository extends JpaRepository<OfficerInvitation, Long> {
    Optional<OfficerInvitation> findByTokenHash(String tokenHash);
    List<OfficerInvitation> findAllByOrderByCreatedAtDesc();
}
