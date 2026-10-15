package com.platform.security;

import com.platform.user.User;
import com.platform.user.UserRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Optional;

@Controller
public class AccountPasswordController {
    private static final String PREFIX = "sagar.reset.";
    private static final String STRONG = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9\\s])\\S{8,}$";
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final ObjectProvider<JavaMailSender> senders;
    private final SecureRandom random = new SecureRandom();

    @Value("${spring.mail.username:}") private String from;

    public AccountPasswordController(UserRepository users, PasswordEncoder encoder,
                                     ObjectProvider<JavaMailSender> senders) {
        this.users = users; this.encoder = encoder; this.senders = senders;
    }

    @GetMapping("/forgot-password")
    public String forgot(Model model, HttpSession session) {
        model.addAttribute("resetEmail", session.getAttribute(PREFIX + "email"));
        model.addAttribute("expiryEpoch", session.getAttribute(PREFIX + "expiry"));
        Long sentAt = (Long) session.getAttribute(PREFIX + "sent");
        model.addAttribute("resendEpoch", sentAt == null ? null : sentAt + 60);
        model.addAttribute("stage", session.getAttribute(PREFIX + "verified") != null ? "password"
                : session.getAttribute(PREFIX + "hash") != null ? "otp" : "email");
        return "forgot-password";
    }

    @PostMapping("/forgot-password/request")
    public String request(@RequestParam String email, HttpSession session, Model model) {
        long now = Instant.now().getEpochSecond();
        Long last = (Long) session.getAttribute(PREFIX + "sent");
        if (last != null && now - last < 60) {
            model.addAttribute("error", "Please wait 60 seconds before requesting another code.");
            return forgot(model, session);
        }
        String normalized = email.trim();
        Optional<User> user = users.findByEmail(normalized);
        if (user.isEmpty()) {
            model.addAttribute("error", "This email is not registered. Please enter a valid account email.");
            return forgot(model, session);
        }
        if (user.isPresent()) {
            JavaMailSender sender = senders.getIfAvailable();
            if (sender == null || from.isBlank()) {
                model.addAttribute("error", "Password reset email is not configured. Please contact the administrator.");
                return forgot(model, session);
            }
            String code = String.format("%06d", random.nextInt(1_000_000));
            for (String key : new String[]{"email", "hash", "expiry", "tries", "verified"}) session.removeAttribute(PREFIX + key);
            session.setAttribute(PREFIX + "sent", now);
            session.setAttribute(PREFIX + "email", normalized);
            session.setAttribute(PREFIX + "hash", encoder.encode(code));
            session.setAttribute(PREFIX + "expiry", now + 300);
            session.setAttribute(PREFIX + "tries", 0);
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from.trim()); message.setTo(normalized);
            message.setSubject("SAGAR password reset code");
            message.setText("Your password reset code is " + code + ". It expires in 5 minutes. Do not share it.");
            try { sender.send(message); } catch (org.springframework.mail.MailException ex) {
                clear(session);
                model.addAttribute("error", "Could not send reset email. Please check mail configuration and try again.");
                return forgot(model, session);
            }
        }
        model.addAttribute("message", "Reset code sent to your registered email.");
        return forgot(model, session);
    }

    @PostMapping("/forgot-password/verify")
    public String verify(@RequestParam String otp, HttpSession session, Model model) {
        String hash = (String) session.getAttribute(PREFIX + "hash");
        Long expiry = (Long) session.getAttribute(PREFIX + "expiry");
        Integer tries = (Integer) session.getAttribute(PREFIX + "tries");
        if (hash == null || expiry == null || tries == null || Instant.now().getEpochSecond() > expiry || tries >= 5) {
            clear(session); model.addAttribute("error", "Code expired or unavailable. Request a new code.");
            return forgot(model, session);
        }
        session.setAttribute(PREFIX + "tries", tries + 1);
        if (!otp.matches("\\d{6}") || !encoder.matches(otp, hash)) {
            if (tries + 1 >= 5) clear(session);
            model.addAttribute("error", "Incorrect code."); return forgot(model, session);
        }
        session.removeAttribute(PREFIX + "hash");
        session.setAttribute(PREFIX + "verified", true);
        return forgot(model, session);
    }

    @PostMapping("/forgot-password/reset")
    public String reset(@RequestParam String password, @RequestParam String confirmPassword,
                        HttpSession session, Model model) {
        String email = (String) session.getAttribute(PREFIX + "email");
        Long expiry = (Long) session.getAttribute(PREFIX + "expiry");
        if (!Boolean.TRUE.equals(session.getAttribute(PREFIX + "verified")) || email == null
                || expiry == null || Instant.now().getEpochSecond() > expiry) {
            clear(session); model.addAttribute("error", "Reset session expired. Start again.");
            return forgot(model, session);
        }
        if (!valid(password, confirmPassword)) {
            model.addAttribute("error", "Passwords must match and contain 8+ characters, uppercase, lowercase, digit and special character.");
            return forgot(model, session);
        }
        User user = users.findByEmail(email).orElse(null);
        if (user == null) { clear(session); return "redirect:/login"; }
        user.setPassword(encoder.encode(password)); users.save(user);
        clear(session);
        model.addAttribute("stage", "success");
        model.addAttribute("message", "Your password has been reset successfully.");
        return "forgot-password";
    }

    @GetMapping("/change-password")
    public String changePage() { return "change-password"; }

    @PostMapping("/change-password")
    public String change(@RequestParam String currentPassword, @RequestParam String password,
                         @RequestParam String confirmPassword, Authentication auth, Model model) {
        if (auth == null || !auth.isAuthenticated()) return "redirect:/login";
        User user = users.findByEmail(auth.getName()).orElse(null);
        if (user == null) { model.addAttribute("error", "Account unavailable."); return "change-password"; }
        if (!encoder.matches(currentPassword, user.getPassword())) {
            model.addAttribute("error", "Current password is incorrect."); return "change-password";
        }
        if (!valid(password, confirmPassword)) {
            model.addAttribute("error", "Passwords must match and contain 8+ characters, uppercase, lowercase, digit and special character.");
            return "change-password";
        }
        user.setPassword(encoder.encode(password)); users.save(user);
        model.addAttribute("message", "Password changed successfully.");
        return "change-password";
    }

    private boolean valid(String password, String confirm) {
        return password != null && password.equals(confirm) && password.matches(STRONG);
    }
    private void clear(HttpSession session) {
        for (String key : new String[]{"email", "hash", "expiry", "tries", "verified", "sent"})
            session.removeAttribute(PREFIX + key);
    }
}
