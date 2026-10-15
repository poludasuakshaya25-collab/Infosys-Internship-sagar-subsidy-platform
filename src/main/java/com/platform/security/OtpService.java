package com.platform.security;

import com.platform.audit.AuditLogService;
import com.platform.user.User;
import com.platform.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.core.env.Environment;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;

@Service
public class OtpService {

    private final LoginOtpRepository otpRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;
    private final Environment environment;
    private final java.net.http.HttpClient http = java.net.http.HttpClient.newHttpClient();
    @Value("${SAGAR_OTP_WEBAPP_URL:}") private String otpWebappUrl;
    @Value("${SAGAR_OTP_SECRET:}") private String otpSecret;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${sagar.otp.expiry-minutes:5}")
    private long expiryMinutes;

    @Value("${sagar.otp.max-attempts:5}")
    private int maxAttempts;

    @Value("${sagar.otp.resend-seconds:60}")
    private long resendSeconds;

    @Value("${sagar.otp.dev-display:false}")
    private boolean devDisplay;



    public OtpService(LoginOtpRepository otpRepository,
                      UserRepository userRepository,
                      PasswordEncoder passwordEncoder,
                      AuditLogService auditLogService,
                      Environment environment) {
        this.otpRepository = otpRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditLogService = auditLogService;
        this.environment = environment;
    }

    @Transactional
    public String issueOtp(String email, boolean resend) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Account not found."));

        LoginOtp latest = otpRepository.findTopByUserAndUsedFalseOrderByCreatedAtDesc(user).orElse(null);
        if (resend && latest != null
                && Duration.between(latest.getCreatedAt(), LocalDateTime.now()).getSeconds() < resendSeconds) {
            throw new IllegalStateException("Please wait before requesting another OTP.");
        }

        for (LoginOtp old : otpRepository.findByUserAndUsedFalse(user)) {
            old.setUsed(true);
            otpRepository.save(old);
        }

        String code = String.format("%06d", secureRandom.nextInt(1_000_000));

        LoginOtp otp = new LoginOtp();
        otp.setUser(user);
        otp.setOtpHash(passwordEncoder.encode(code));
        otp.setCreatedAt(LocalDateTime.now());
        otp.setExpiresAt(LocalDateTime.now().plusMinutes(expiryMinutes));
        otp.setAttemptCount(0);
        otp.setUsed(false);
        otpRepository.save(otp);

        sendOtpEmail(user.getEmail(), code);
        auditLogService.recordAs(user.getEmail(), primaryRole(user),
                "AUTHENTICATION", user.getId(), "OTP_ISSUED",
                null, null, "A login OTP was issued.");

        return isDevDisplay() ? code : null;
    }

    @Transactional
    public boolean verifyOtp(String email, String submittedCode) {
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) return false;

        LoginOtp otp = otpRepository.findTopByUserAndUsedFalseOrderByCreatedAtDesc(user).orElse(null);
        if (otp == null) return false;

        LocalDateTime now = LocalDateTime.now();
        if (otp.getExpiresAt().isBefore(now) || otp.getAttemptCount() >= maxAttempts) {
            otp.setUsed(true);
            otpRepository.save(otp);
            auditLogService.recordAs(user.getEmail(), primaryRole(user),
                    "AUTHENTICATION", user.getId(), "OTP_FAILED",
                    null, null, "Login OTP expired or was locked.");
            return false;
        }

        String candidate = submittedCode == null ? "" : submittedCode.trim();
        if (!candidate.matches("\\d{6}") || !passwordEncoder.matches(candidate, otp.getOtpHash())) {
            otp.setAttemptCount(otp.getAttemptCount() + 1);
            if (otp.getAttemptCount() >= maxAttempts) otp.setUsed(true);
            otpRepository.save(otp);
            auditLogService.recordAs(user.getEmail(), primaryRole(user),
                    "AUTHENTICATION", user.getId(), "OTP_FAILED",
                    null, null, "Incorrect login OTP.");
            return false;
        }

        otp.setUsed(true);
        otpRepository.save(otp);
        auditLogService.recordAs(user.getEmail(), primaryRole(user),
                "AUTHENTICATION", user.getId(), "OTP_VERIFIED",
                null, null, "Login OTP verified.");
        return true;
    }

    public boolean isDevDisplay() {
        return devDisplay && !isProduction();
    }

    private void sendOtpEmail(String recipient, String code) {
        sendAppsScript(recipient, code);
    }

    private String primaryRole(User user) {
        return user.getRoles() == null || user.getRoles().isEmpty()
                ? "UNKNOWN"
                : user.getRoles().iterator().next();
    }

    private boolean isProduction() {
        return java.util.Arrays.stream(environment.getActiveProfiles()).anyMatch(p -> p.equalsIgnoreCase("prod"));
    }

    private void sendAppsScript(String recipient, String code) {
        if (otpWebappUrl.isBlank() || otpSecret.isBlank()) {
            if (isDevDisplay()) return;
            throw new IllegalStateException("Google Apps Script OTP is not configured. Set SAGAR_OTP_WEBAPP_URL and SAGAR_OTP_SECRET.");
        }
        if (!otpWebappUrl.startsWith("https://script.google.com/macros/s/") || !otpWebappUrl.endsWith("/exec")) {
            throw new IllegalStateException("Invalid Google Apps Script OTP URL.");
        }
        try {
            String payload = "{\"secret\":\"" + escapeJson(otpSecret)
                    + "\",\"to\":\"" + escapeJson(recipient)
                    + "\",\"otp\":\"" + escapeJson(code) + "\"}";
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder(java.net.URI.create(otpWebappUrl))
                    .header("Content-Type", "text/plain; charset=utf-8")
                    .timeout(java.time.Duration.ofSeconds(25))
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(payload)).build();
            java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                    .followRedirects(java.net.http.HttpClient.Redirect.NORMAL).build();
            java.net.http.HttpResponse<String> response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200 || !"SUCCESS".equals(response.body().trim())) {
                throw new IllegalStateException("Google Apps Script did not confirm OTP sending (HTTP "
                        + response.statusCode() + "). Check Apps Script Executions and deployment permissions.");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("OTP email sending interrupted.", ex);
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("Google Apps Script OTP request failed.", ex);
        }
    }
    private static String escapeJson(String value) {
        StringBuilder out = new StringBuilder();
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
                }
            }
        }
        return out.toString();
    }

}
