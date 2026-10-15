package com.platform.api;

import com.platform.api.dto.SchemeApiResponse;
import com.platform.scheme.Scheme;
import com.platform.scheme.SchemeRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/schemes")
public class SchemeApiController {

    private final SchemeRepository schemeRepository;

    public SchemeApiController(SchemeRepository schemeRepository) {
        this.schemeRepository = schemeRepository;
    }

    @GetMapping
    public List<SchemeApiResponse> allSchemes() {
        return schemeRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<SchemeApiResponse> scheme(@PathVariable Long id) {
        return schemeRepository.findById(id)
                .map(s -> ResponseEntity.ok(toResponse(s)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private SchemeApiResponse toResponse(Scheme s) {
        return new SchemeApiResponse(
                s.getId(), s.getSchemeName(), s.getDescription(),
                s.getBenefitSummary(), s.getTargetBeneficiary(),
                s.getEligibleAgeMin(), s.getEligibleAgeMax(),
                s.getMaxIncomeThreshold(), s.getMaxSanctionAmount(),
                s.getRequiredDocuments(), s.getOptionalDocuments()
        );
    }
}
