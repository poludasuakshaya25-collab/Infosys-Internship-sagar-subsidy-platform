package com.platform.security;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class RegistrationOtpController {

    private final RegistrationOtpService registrationOtpService;

    public RegistrationOtpController(RegistrationOtpService registrationOtpService) {
        this.registrationOtpService = registrationOtpService;
    }

    @GetMapping("/verify-registration-otp")
    public String showVerification(HttpSession session) {
        String email = (String) session.getAttribute(RegistrationOtpService.PENDING_EMAIL);
        return email == null ? "redirect:/register" : "redirect:/register?otp";
    }

    @PostMapping("/verify-registration-otp")
    public String verify(@RequestParam String otp, HttpSession session, RedirectAttributes redirectAttributes) {
        String email = (String) session.getAttribute(RegistrationOtpService.PENDING_EMAIL);
        if (email == null) return "redirect:/register";

        if (!registrationOtpService.verifyAndActivate(email, otp)) {
            redirectAttributes.addFlashAttribute("otpError", "Invalid, expired or locked registration OTP.");
            return "redirect:/register?otp";
        }

        session.removeAttribute(RegistrationOtpService.PENDING_EMAIL);
        session.removeAttribute(RegistrationOtpService.DEV_OTP);
        return "redirect:/login?registrationVerified";
    }

    @PostMapping("/resend-registration-otp")
    public String resend(HttpSession session, RedirectAttributes redirectAttributes) {
        String email = (String) session.getAttribute(RegistrationOtpService.PENDING_EMAIL);
        if (email == null) return "redirect:/register";

        try {
            String devOtp = registrationOtpService.issueOtp(email, true);
            if (devOtp != null) {
                session.setAttribute(RegistrationOtpService.DEV_OTP, devOtp);
            } else {
                session.removeAttribute(RegistrationOtpService.DEV_OTP);
            }
            return "redirect:/register?otp&resent";
        } catch (IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("otpError", ex.getMessage());
            return "redirect:/register?otp";
        }
    }

}
