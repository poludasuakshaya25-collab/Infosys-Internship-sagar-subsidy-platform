package com.platform.user;

import com.platform.audit.AuditLogService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class OfficerOnboardingService {
    private static final Set<String> ALLOWED_ROLES = Set.of(
            "ROLE_FIELD_OFFICER", "ROLE_DISTRICT_OFFICER", "ROLE_FINANCE_APPROVER");

    private final OfficerInvitationRepository invitationRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${sagar.officer.invitation-hours:24}") private long invitationHours;
    @Value("${sagar.app.base-url:http://localhost:8081}") private String baseUrl;
    @Value("${sagar.mail.enabled:false}") private boolean mailEnabled;

    public OfficerOnboardingService(OfficerInvitationRepository invitationRepository,
                                    UserRepository userRepository,
                                    PasswordEncoder passwordEncoder,
                                    AuditLogService auditLogService,
                                    ObjectProvider<JavaMailSender> mailSenderProvider) {
        this.invitationRepository = invitationRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditLogService = auditLogService;
        this.mailSenderProvider = mailSenderProvider;
    }

    @Transactional
    public String createInvitation(String name, String email, String role, String state,
                                   String district, String createdBy) {
        name = cleanRequired(name, "Officer name");
        email = cleanRequired(email, "Official email").toLowerCase();
        role = cleanRequired(role, "Role");
        state = cleanRequired(state, "State");
        district = district == null ? null : district.trim();

        if (!ALLOWED_ROLES.contains(role)) throw new IllegalArgumentException("Unsupported officer role.");
        if ((role.equals("ROLE_FIELD_OFFICER") || role.equals("ROLE_DISTRICT_OFFICER"))
                && (district == null || district.isBlank())) {
            throw new IllegalArgumentException("District is required for Field and District officers.");
        }
        if (userRepository.existsByEmail(email)) throw new IllegalArgumentException("An account already exists for this email.");

        String token = newToken();
        OfficerInvitation invitation = new OfficerInvitation();
        invitation.setOfficerName(name);
        invitation.setEmail(email);
        invitation.setRole(role);
        invitation.setState(state);
        invitation.setDistrict(role.equals("ROLE_FINANCE_APPROVER") ? null : district);
        invitation.setTokenHash(hash(token));
        invitation.setExpiresAt(LocalDateTime.now().plusHours(invitationHours));
        invitation.setCreatedBy(createdBy);
        invitationRepository.save(invitation);

        auditLogService.record("OFFICER_INVITATION", invitation.getId(), "OFFICER_INVITED",
                null, role + " | " + state + (invitation.getDistrict() == null ? "" : " | " + invitation.getDistrict()),
                "Officer onboarding invitation created for " + email + ".");

        String link = baseUrl.replaceAll("/$", "") + "/officer-register?token=" + token;
        sendInvitationIfConfigured(invitation, link);
        return link;
    }

    @Transactional(readOnly = true)
    public OfficerInvitation getUsableInvitation(String token) {
        if (token == null || token.isBlank()) return null;
        return invitationRepository.findByTokenHash(hash(token)).filter(OfficerInvitation::isUsable).orElse(null);
    }

    @Transactional
    public User registerOfficer(OfficerRegistrationForm form) {
        OfficerInvitation invitation = getUsableInvitation(form.getToken());
        if (invitation == null) throw new IllegalArgumentException("This invitation is invalid, expired or already used.");
        if (userRepository.existsByEmail(invitation.getEmail())) throw new IllegalArgumentException("An account already exists for this email.");

        String username = cleanRequired(form.getUsername(), "Username");
        if (username.length() < 4 || username.length() > 60) throw new IllegalArgumentException("Username must be 4 to 60 characters.");
        if (userRepository.existsByUsernameIgnoreCase(username)) throw new IllegalArgumentException("Username is already in use.");
        String phoneNumber = cleanRequired(form.getPhoneNumber(), "Mobile number");
        if (!phoneNumber.matches("\\d{10}")) {
            throw new IllegalArgumentException("Mobile number must contain exactly 10 digits.");
        }
        if (form.getPassword() == null || !form.getPassword().matches("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9\\s])\\S{8,}$")) {
            throw new IllegalArgumentException("Password must be at least 8 characters and include uppercase, lowercase, number and special character.");
        }
        if (!form.getPassword().equals(form.getConfirmPassword())) throw new IllegalArgumentException("Password and confirmation do not match.");

        User user = new User();
        user.setEmail(invitation.getEmail());
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(form.getPassword()));
        user.setRoles(new HashSet<>(Set.of(invitation.getRole())));
        user.setFullName(invitation.getOfficerName());
        user.setPhoneNumber(phoneNumber);
        user.setAssignedState(invitation.getState());
        user.setAssignedDistrict(invitation.getDistrict());
        user.setAccountStatus("PENDING_APPROVAL");
        user = userRepository.save(user);

        invitation.setStatus("ACCEPTED");
        invitation.setUsedAt(LocalDateTime.now());
        invitationRepository.save(invitation);

        auditLogService.recordAs(user.getEmail(), invitation.getRole(), "OFFICER_ACCOUNT", user.getId(),
                "OFFICER_REGISTERED", null, "PENDING_APPROVAL", "Officer completed invited registration.");
        return user;
    }

    @Transactional
    public void approve(Long userId) {
        User user = officer(userId);
        String previous = effectiveStatus(user);
        user.setAccountStatus("ACTIVE");
        userRepository.save(user);
        auditLogService.record("OFFICER_ACCOUNT", userId, "OFFICER_APPROVED", previous, "ACTIVE", "Administrator approved officer access.");
    }

    @Transactional
    public void reject(Long userId) {
        User user = officer(userId);
        String previous = effectiveStatus(user);
        user.setAccountStatus("REJECTED");
        userRepository.save(user);
        auditLogService.record("OFFICER_ACCOUNT", userId, "OFFICER_REJECTED", previous, "REJECTED", "Administrator rejected officer registration.");
    }

    @Transactional
    public void suspend(Long userId) {
        User user = officer(userId);
        String previous = effectiveStatus(user);
        user.setAccountStatus("SUSPENDED");
        userRepository.save(user);
        auditLogService.record("OFFICER_ACCOUNT", userId, "OFFICER_SUSPENDED", previous, "SUSPENDED", "Administrator suspended officer access.");
    }

    @Transactional
    public void reactivate(Long userId) {
        User user = officer(userId);
        String previous = effectiveStatus(user);
        user.setAccountStatus("ACTIVE");
        userRepository.save(user);
        auditLogService.record("OFFICER_ACCOUNT", userId, "OFFICER_REACTIVATED", previous, "ACTIVE", "Administrator reactivated officer access.");
    }

    @Transactional(readOnly = true)
    public List<User> getOfficerAccounts() {
        return userRepository.findAll().stream().filter(this::isOfficer).toList();
    }

    @Transactional(readOnly = true)
    public List<OfficerInvitation> getInvitations() { return invitationRepository.findAllByOrderByCreatedAtDesc(); }

    public String effectiveStatus(User user) {
        return user.getAccountStatus() == null || user.getAccountStatus().isBlank() ? "ACTIVE" : user.getAccountStatus();
    }

    private User officer(Long id) {
        User user = userRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Officer account not found."));
        if (!isOfficer(user)) throw new IllegalArgumentException("The selected account is not an officer account.");
        return user;
    }

    private boolean isOfficer(User user) {
        if (user.getRoles() == null) return false;
        return user.getRoles().stream().anyMatch(ALLOWED_ROLES::contains);
    }

    private String cleanRequired(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required.");
        return value.trim();
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (byte b : digest) out.append(String.format("%02x", b));
            return out.toString();
        } catch (Exception ex) { throw new IllegalStateException("Unable to secure invitation token.", ex); }
    }

    private void sendInvitationIfConfigured(OfficerInvitation invitation, String link) {
        if (!mailEnabled) return;
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null) return;
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(invitation.getEmail());
            message.setSubject("SAGAR Officer Registration Invitation");
            message.setText("You have been invited to register as " + invitation.getRole().replace("ROLE_", "").replace('_', ' ')
                    + " for " + invitation.getState() + (invitation.getDistrict() == null ? "" : ", " + invitation.getDistrict())
                    + ".\n\nComplete your registration using this secure link (valid for " + invitationHours + " hours):\n" + link
                    + "\n\nIf you did not expect this invitation, ignore this message.");
            sender.send(message);
        } catch (RuntimeException ignored) {
            // Invitation remains valid. Admin receives the development copy-link on screen.
        }
    }
}
