package com.platform.application;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ApplicationRemarkRepository extends JpaRepository<ApplicationRemark, Long> {
    List<ApplicationRemark> findByApplicationIdAndVisibleToApplicantTrueOrderByCreatedAtAsc(Long applicationId);
    List<ApplicationRemark> findByApplicationIdOrderByCreatedAtAsc(Long applicationId);
}
