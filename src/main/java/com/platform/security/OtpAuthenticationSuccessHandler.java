package com.platform.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class OtpAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    public static final String PENDING_EMAIL = "SAGAR_OTP_PENDING_EMAIL";
    public static final String DEV_OTP = "SAGAR_DEV_OTP";

    private final OtpService otpService;

    public OtpAuthenticationSuccessHandler(OtpService otpService) {
        this.otpService = otpService;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication)
            throws IOException, ServletException {

        String email = authentication.getName();
        String developmentOtp = otpService.issueOtp(email, false);

        // Password was correct, but MFA is not complete yet.
        SecurityContextHolder.clearContext();
        request.getSession(true).setAttribute(PENDING_EMAIL, email);

        if (developmentOtp != null) {
            request.getSession().setAttribute(DEV_OTP, developmentOtp);
        } else {
            request.getSession().removeAttribute(DEV_OTP);
        }

        response.sendRedirect(request.getContextPath() + "/login?otp");
    }
}
