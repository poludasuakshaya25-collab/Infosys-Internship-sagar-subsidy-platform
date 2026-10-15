package com.platform.application;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "application_documents",
        uniqueConstraints = @UniqueConstraint(columnNames = {"application_id", "document_key"}))
public class ApplicationDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "application_id", nullable = false)
    private SubsidyApplication application;

    @Column(name = "document_key", nullable = false, length = 120)
    private String documentKey;

    @Column(name = "document_name", nullable = false, length = 250)
    private String documentName;

    @Column(name = "original_filename", nullable = false, length = 500)
    private String originalFilename;

    @Column(name = "stored_filename", nullable = false, length = 500)
    private String storedFilename;

    @Column(nullable = false)
    private boolean optionalDocument;

    @Column(nullable = false)
    private LocalDateTime uploadedAt;

    @Column(nullable = false, length = 30)
    private String verificationStatus = "PENDING";

    @Column(length = 2000)
    private String verificationRemarks;

    private LocalDateTime verifiedAt;
    private String verifiedBy;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public SubsidyApplication getApplication() { return application; }
    public void setApplication(SubsidyApplication application) { this.application = application; }

    public String getDocumentKey() { return documentKey; }
    public void setDocumentKey(String documentKey) { this.documentKey = documentKey; }

    public String getDocumentName() { return documentName; }
    public void setDocumentName(String documentName) { this.documentName = documentName; }

    public String getOriginalFilename() { return originalFilename; }
    public void setOriginalFilename(String originalFilename) { this.originalFilename = originalFilename; }

    public String getStoredFilename() { return storedFilename; }
    public void setStoredFilename(String storedFilename) { this.storedFilename = storedFilename; }

    public boolean isOptionalDocument() { return optionalDocument; }
    public void setOptionalDocument(boolean optionalDocument) { this.optionalDocument = optionalDocument; }

    public LocalDateTime getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(LocalDateTime uploadedAt) { this.uploadedAt = uploadedAt; }
    public String getVerificationStatus() { return verificationStatus; }
    public void setVerificationStatus(String verificationStatus) { this.verificationStatus = verificationStatus; }
    public String getVerificationRemarks() { return verificationRemarks; }
    public void setVerificationRemarks(String verificationRemarks) { this.verificationRemarks = verificationRemarks; }
    public LocalDateTime getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(LocalDateTime verifiedAt) { this.verifiedAt = verifiedAt; }
    public String getVerifiedBy() { return verifiedBy; }
    public void setVerifiedBy(String verifiedBy) { this.verifiedBy = verifiedBy; }

    public String getUrl() { return "/uploads/applications/" + application.getId() + "/" + storedFilename; }
}
