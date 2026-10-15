package com.platform.user;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/beneficiary")
public class BeneficiaryController {

    private final BeneficiaryService beneficiaryService;

    // Standard constructor injection matching the service bean
    public BeneficiaryController(BeneficiaryService beneficiaryService) {
        this.beneficiaryService = beneficiaryService;
    }

    /**
     * REST Endpoint for Module 5 API integrations.
     * Evaluates beneficiary eligibility criteria and returns a structured response string.
     */
    @GetMapping("/verify/{userId}")
    public ResponseEntity<String> verifyBeneficiary(@PathVariable Long userId) {
        try {
            // Invokes the fixed string evaluation routine
            String validationOutcome = beneficiaryService.verifyBeneficiary(userId);

            // If the criteria logic flags a rejection, catch it and return a 400 Bad Request
            if (validationOutcome.contains("REJECTED")) {
                return ResponseEntity.badRequest().body(validationOutcome);
            }

            // Otherwise return a standard 200 OK status containing the verification log
            return ResponseEntity.ok(validationOutcome);

        } catch (RuntimeException exception) {
            // Handles missing user exception loops gracefully without crashing the runtime
            return ResponseEntity.status(404).body("Error: " + exception.getMessage());
        }
    }
}
