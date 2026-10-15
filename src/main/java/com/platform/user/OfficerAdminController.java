package com.platform.admin;

import com.platform.user.OfficerOnboardingService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/officers")
public class OfficerAdminController {
    private final OfficerOnboardingService onboardingService;

    public OfficerAdminController(OfficerOnboardingService onboardingService) {
        this.onboardingService = onboardingService;
    }

    @GetMapping
    public String page(Model model) {
        model.addAttribute("officers", onboardingService.getOfficerAccounts());
        model.addAttribute("invitations", onboardingService.getInvitations());
        return "admin-officers";
    }

    @PostMapping("/invite")
    public String invite(@AuthenticationPrincipal UserDetails admin,
                         @RequestParam String officerName,
                         @RequestParam String email,
                         @RequestParam String role,
                         @RequestParam String state,
                         @RequestParam(required = false) String district,
                         RedirectAttributes redirectAttributes) {
        try {
            String link = onboardingService.createInvitation(officerName, email, role, state, district,
                    admin == null ? "ADMIN" : admin.getUsername());
            redirectAttributes.addFlashAttribute("success", "Officer invitation created successfully.");
            redirectAttributes.addFlashAttribute("invitationLink", link);
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/officers";
    }

    @PostMapping("/{id}/approve")
    public String approve(@PathVariable Long id, RedirectAttributes ra) { return action(() -> onboardingService.approve(id), "Officer approved and activated.", ra); }
    @PostMapping("/{id}/reject")
    public String reject(@PathVariable Long id, RedirectAttributes ra) { return action(() -> onboardingService.reject(id), "Officer registration rejected.", ra); }
    @PostMapping("/{id}/suspend")
    public String suspend(@PathVariable Long id, RedirectAttributes ra) { return action(() -> onboardingService.suspend(id), "Officer access suspended.", ra); }
    @PostMapping("/{id}/reactivate")
    public String reactivate(@PathVariable Long id, RedirectAttributes ra) { return action(() -> onboardingService.reactivate(id), "Officer access reactivated.", ra); }

    private String action(Runnable action, String message, RedirectAttributes ra) {
        try { action.run(); ra.addFlashAttribute("success", message); }
        catch (RuntimeException ex) { ra.addFlashAttribute("error", ex.getMessage()); }
        return "redirect:/admin/officers";
    }
}
