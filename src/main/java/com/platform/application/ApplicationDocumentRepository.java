package com.platform.application;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ApplicationDocumentRepository extends JpaRepository<ApplicationDocument, Long> {
    List<ApplicationDocument> findByApplicationIdOrderByOptionalDocumentAscDocumentNameAsc(Long applicationId);
    Optional<ApplicationDocument> findByApplicationIdAndDocumentKey(Long applicationId, String documentKey);
}
