package com.platform.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import com.platform.security.ActiveAccountFilter;
import com.platform.security.OtpAuthenticationSuccessHandler;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           OtpAuthenticationSuccessHandler otpSuccessHandler,
                                           ActiveAccountFilter activeAccountFilter) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        // Public routes
                        .requestMatchers("/", "/register", "/login", "/officer-register", "/error",
                                "/css/**", "/images/**",
                                "/scheme/**", "/verify-otp", "/resend-otp", "/cancel-otp",
                                "/verify-registration-otp", "/resend-registration-otp").permitAll()

                        // REST API v1: public scheme catalogue; citizen tracking requires login.
                        .requestMatchers("/api/v1/schemes/**").permitAll()
                        .requestMatchers("/api/v1/applications/**").hasRole("BENEFICIARY")
                        .requestMatchers("/api/v1/officer/**")
                        .hasAnyRole("FIELD_OFFICER", "DISTRICT_OFFICER",
                                "FINANCE_APPROVER", "DISBURSEMENT_OFFICER", "ADMIN")

                        // Dashboard router
                        .requestMatchers("/dashboard").authenticated()

                        // Administrator console and master-data management
                        .requestMatchers("/admin/**").hasRole("ADMIN")

                        // Existing admin-monitor page remains available to officers and Admin.
                        // IMPORTANT: keep this matcher before /eligibility/** so ADMIN is not
                        // blocked by the broader citizen matcher below.
                        .requestMatchers("/eligibility/admin/monitor")
                        .hasAnyRole("FIELD_OFFICER", "DISTRICT_OFFICER",
                                "FINANCE_APPROVER", "DISBURSEMENT_OFFICER", "ADMIN")

                        // Officer application read-only details MUST come before the
                        // broader /application/** citizen matcher, otherwise officers
                        // receive HTTP 403 when opening application details.
                        .requestMatchers("/officer/application/**")
                        .hasAnyRole("FIELD_OFFICER", "DISTRICT_OFFICER", "FINANCE_APPROVER",
                                "DISBURSEMENT_OFFICER", "ADMIN")

                        // Legacy officer eligibility routes are still used by the
                        // compatibility officer desk and must not be caught by the
                        // broader citizen /eligibility/** matcher.
                        .requestMatchers("/eligibility/officer/**")
                        .hasAnyRole("FIELD_OFFICER", "DISTRICT_OFFICER", "FINANCE_APPROVER",
                                "DISBURSEMENT_OFFICER", "ADMIN")

                        // Citizen routes
                        .requestMatchers("/dashboard/citizen", "/eligibility/**",
                                "/profile/**", "/application/**", "/citizen/milestone/**").hasRole("BENEFICIARY")

                        // Field Officer routes
                        .requestMatchers("/officer/field/**").hasRole("FIELD_OFFICER")

                        // District Officer routes
                        .requestMatchers("/officer/district/**",
                                "/officer/district/eligibility/**").hasRole("DISTRICT_OFFICER")

                        // Finance Officer routes
                        .requestMatchers("/officer/finance/**").hasRole("FINANCE_APPROVER")

                        // Legacy Disbursement Officer routes retained for old accounts.
                        .requestMatchers("/officer/disbursal/**")
                        .hasAnyRole("FINANCE_APPROVER", "DISBURSEMENT_OFFICER")

                        // Shared officer dashboard
                        .requestMatchers("/dashboard/officer")
                        .hasAnyRole("FIELD_OFFICER", "DISTRICT_OFFICER",
                                "FINANCE_APPROVER", "DISBURSEMENT_OFFICER")

                        // Milestone 3: Finance Officer owns the staged disbursement workflow.
                        // The legacy DISBURSEMENT_OFFICER role is retained only for compatibility.
                        .requestMatchers("/milestone3/citizen/**").hasRole("BENEFICIARY")

                        .requestMatchers("/milestone3/disbursement/**")
                        .hasAnyRole("FINANCE_APPROVER", "DISBURSEMENT_OFFICER")

                        // Finance-only compliance reminder trigger.
                        .requestMatchers("/milestone3/reminders/run")
                        .hasRole("FINANCE_APPROVER")

                        // Analytics is read-only and can be viewed by all officers plus Admin.
                        .requestMatchers("/milestone3/analytics")
                        .hasAnyRole("FIELD_OFFICER", "DISTRICT_OFFICER",
                                "FINANCE_APPROVER", "DISBURSEMENT_OFFICER", "ADMIN")

                        // Everything else must be authenticated.
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .successHandler(otpSuccessHandler)
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutSuccessUrl("/login?logout")
                        .permitAll()
                );

        http.addFilterAfter(activeAccountFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}