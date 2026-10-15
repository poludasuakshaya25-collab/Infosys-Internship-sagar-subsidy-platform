package com.platform.security;

import com.platform.application.SubsidyApplication;
import com.platform.application.SubsidyApplicationRepository;
import com.platform.user.User;
import com.platform.user.UserRepository;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Controller
public class SecureDocumentController {

    private final SubsidyApplicationRepository applicationRepository;
    private final UserRepository userRepository;
    private final JurisdictionService jurisdictionService;
    private final com.platform.config.CloudinaryStorageService cloudinaryStorage;

    public SecureDocumentController(SubsidyApplicationRepository applicationRepository,
                                    UserRepository userRepository,
                                    JurisdictionService jurisdictionService,
                                    com.platform.config.CloudinaryStorageService cloudinaryStorage) {
        this.applicationRepository = applicationRepository;
        this.userRepository = userRepository;
        this.jurisdictionService = jurisdictionService;
        this.cloudinaryStorage = cloudinaryStorage;
    }

    /**
     * Secure replacement for the old public /uploads/applications/{appId}/{filename} resource handler.
     * Existing Thymeleaf doc.url values do not need to change.
     */
    @GetMapping("/uploads/applications/{applicationId}/{filename:.+}")
    public ResponseEntity<byte[]> applicationDocument(@PathVariable Long applicationId,
                                                      @PathVariable String filename,
                                                      @AuthenticationPrincipal UserDetails principal) {
        if (principal == null) return ResponseEntity.status(401).build();

        SubsidyApplication app = applicationRepository.findDetailedById(applicationId).orElse(null);
        if (app == null) return ResponseEntity.notFound().build();

        if (!canRead(principal, app)) {
            return ResponseEntity.status(403).build();
        }

        try {
            byte[] content = cloudinaryStorage.read(applicationId, filename, false);
            MediaType mediaType = mediaTypeFor(filename);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + safeHeaderFilename(filename) + "\"")
                    .contentType(mediaType).body(content);
        } catch (Exception ex) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Legacy root-level upload links are officer-only. New citizen documents are stored
     * under /uploads/applications/{id}/ and use the ownership/jurisdiction check above.
     */
    @GetMapping("/uploads/{filename:.+}")
    public ResponseEntity<byte[]> legacyOfficerDocument(@PathVariable String filename,
                                                        @AuthenticationPrincipal UserDetails principal) {
        if (principal == null || !hasOfficerRole(principal)) {
            return ResponseEntity.status(403).build();
        }

        try {
            Path root = Paths.get(System.getProperty("user.dir"), "uploads")
                    .toAbsolutePath().normalize();
            Path file = root.resolve(filename).normalize();
            if (!file.startsWith(root) || !Files.isRegularFile(file)) {
                return ResponseEntity.notFound().build();
            }

            String detected = Files.probeContentType(file);
            MediaType mediaType;
            try {
                mediaType = detected == null
                        ? MediaType.APPLICATION_OCTET_STREAM
                        : MediaType.parseMediaType(detected);
            } catch (Exception ignored) {
                mediaType = MediaType.APPLICATION_OCTET_STREAM;
            }

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "inline; filename=\"" + safeHeaderFilename(filename) + "\"")
                    .contentType(mediaType)
                    .body(Files.readAllBytes(file));
        } catch (Exception ex) {
            return ResponseEntity.notFound().build();
        }
    }

    private boolean canRead(UserDetails principal, SubsidyApplication app) {
        User current = userRepository.findByEmail(principal.getUsername()).orElse(null);
        if (current == null) return false;

        if (hasRole(principal, "ROLE_BENEFICIARY")) {
            return app.getUser() != null
                    && app.getUser().getId() != null
                    && app.getUser().getId().equals(current.getId());
        }

        if (hasOfficerRole(principal)) {
            return jurisdictionService.canAccess(principal, app);
        }

        return false;
    }

    private boolean hasOfficerRole(UserDetails principal) {
        return principal.getAuthorities().stream().anyMatch(a ->
                a.getAuthority().matches(
                        "ROLE_(FIELD_OFFICER|DISTRICT_OFFICER|FINANCE_APPROVER|DISBURSEMENT_OFFICER|ADMIN)"));
    }

    private boolean hasRole(UserDetails principal, String role) {
        return principal.getAuthorities().stream()
                .anyMatch(a -> role.equals(a.getAuthority()));
    }

    private MediaType mediaTypeFor(String filename) {
        String f = filename.toLowerCase(java.util.Locale.ROOT);
        if (f.endsWith(".pdf")) return MediaType.APPLICATION_PDF;
        if (f.endsWith(".png")) return MediaType.IMAGE_PNG;
        if (f.endsWith(".jpg") || f.endsWith(".jpeg")) return MediaType.IMAGE_JPEG;
        return MediaType.APPLICATION_OCTET_STREAM;
    }

    private String safeHeaderFilename(String filename) {
        return filename.replace("\"", "").replace("\r", "").replace("\n", "");
    }
}
