package com.platform.security;

import com.platform.config.CustomUserDetailsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class OtpController {

    private final OtpService otpService;
    private final CustomUserDetailsService userDetailsService;

    public OtpController(OtpService otpService,
                         CustomUserDetailsService userDetailsService) {
        this.otpService = otpService;
        this.userDetailsService = userDetailsService;
    }

    @GetMapping("/verify-otp")
    public String showOtpPage(HttpSession session, Model model) {
        String email = (String) session.getAttribute(OtpAuthenticationSuccessHandler.PENDING_EMAIL);
        if (email == null) return "redirect:/login";

        addPageModel(session, model, email);
        return "verify-otp";
    }

    @PostMapping("/verify-otp")
    public String verifyOtp(@RequestParam String otp,
                            HttpServletRequest request,
                            Model model) {
        HttpSession session = request.getSession(false);
        String email = session == null ? null
                : (String) session.getAttribute(OtpAuthenticationSuccessHandler.PENDING_EMAIL);

        if (email == null) return "redirect:/login";

        if (!otpService.verifyOtp(email, otp)) {
            model.addAttribute("error", "Invalid, expired or locked OTP.");
            addPageModel(session, model, email);
            return "verify-otp";
        }

        var details = userDetailsService.loadUserByUsername(email);
        var authentication = new UsernamePasswordAuthenticationToken(
                details, null, details.getAuthorities());

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        session.setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        session.removeAttribute(OtpAuthenticationSuccessHandler.PENDING_EMAIL);
        session.removeAttribute(OtpAuthenticationSuccessHandler.DEV_OTP);

        return "redirect:/dashboard";
    }

    @PostMapping("/cancel-otp")
    public String cancelOtp(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.removeAttribute(OtpAuthenticationSuccessHandler.PENDING_EMAIL);
            session.removeAttribute(OtpAuthenticationSuccessHandler.DEV_OTP);
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return "redirect:/login";
    }

    @PostMapping("/resend-otp")
    public String resendOtp(HttpSession session, Model model) {
        String email = (String) session.getAttribute(OtpAuthenticationSuccessHandler.PENDING_EMAIL);
        if (email == null) return "redirect:/login";

        try {
            String developmentOtp = otpService.issueOtp(email, true);
            if (developmentOtp != null) {
                session.setAttribute(OtpAuthenticationSuccessHandler.DEV_OTP, developmentOtp);
            } else {
                session.removeAttribute(OtpAuthenticationSuccessHandler.DEV_OTP);
            }
            return "redirect:/verify-otp?resent";
        } catch (IllegalStateException ex) {
            model.addAttribute("error", ex.getMessage());
            addPageModel(session, model, email);
            return "verify-otp";
        }
    }

    private void addPageModel(HttpSession session, Model model, String email) {
        model.addAttribute("maskedEmail", maskEmail(email));
        if (otpService.isDevDisplay()) {
            model.addAttribute("devOtp",
                    session.getAttribute(OtpAuthenticationSuccessHandler.DEV_OTP));
        }
    }

    private String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 1) return email;
        return email.charAt(0) + "***" + email.substring(at - 1);
    }
}
