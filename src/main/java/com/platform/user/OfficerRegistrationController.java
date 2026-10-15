package com.platform.user;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class OfficerRegistrationController {
    private final OfficerOnboardingService onboardingService;

    public OfficerRegistrationController(OfficerOnboardingService onboardingService) {
        this.onboardingService = onboardingService;
    }

    @GetMapping("/officer-register")
    public String registrationPage(@RequestParam(value = "token", required = false) String token, Model model) {
        OfficerInvitation invitation = onboardingService.getUsableInvitation(token);
        model.addAttribute("invitation", invitation);
        OfficerRegistrationForm form = new OfficerRegistrationForm();
        form.setToken(token);
        model.addAttribute("officerForm", form);
        return "officer-register";
    }

    @PostMapping("/officer-register")
    public String register(@ModelAttribute("officerForm") OfficerRegistrationForm form,
                           Model model, RedirectAttributes redirectAttributes) {
        OfficerInvitation invitation = onboardingService.getUsableInvitation(form.getToken());
        if (invitation == null) {
            model.addAttribute("invitation", null);
            model.addAttribute("error", "This invitation is invalid, expired or already used.");
            return "officer-register";
        }
        try {
            onboardingService.registerOfficer(form);
            redirectAttributes.addFlashAttribute("registrationSuccess",
                    "Officer profile submitted successfully. Login will be available after administrator approval.");
            return "redirect:/login";
        } catch (RuntimeException ex) {
            model.addAttribute("invitation", invitation);
            model.addAttribute("error", ex.getMessage());
            return "officer-register";
        }
    }
}
