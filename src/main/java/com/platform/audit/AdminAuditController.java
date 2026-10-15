package com.platform.audit;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Controller
@RequestMapping("/admin/audit")
public class AdminAuditController {
    private final AuditLogRepository auditLogRepository;

    public AdminAuditController(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @GetMapping
    public String auditTrail(@RequestParam(required=false) String actor,
                             @RequestParam(required=false) String role,
                             @RequestParam(required=false) String action,
                             @RequestParam(required=false) String entityType,
                             @RequestParam(required=false) Long entityId,
                             @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate fromDate,
                             @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate toDate,
                             @RequestParam(defaultValue="0") int page,
                             Model model) {
        Specification<AuditLog> spec=(root,query,cb)->{
            List<Predicate> p=new ArrayList<>();
            // Exclude beneficiary-originated activity from the admin-facing audit trail.
            // Keep stored audit records intact for historical integrity.
            p.add(cb.or(cb.isNull(root.get("actorRole")),
                    cb.and(cb.notLike(cb.upper(root.get("actorRole")), "%BENEFICIARY%"),
                            cb.notLike(cb.upper(root.get("actorRole")), "%CITIZEN%"))));
            if(StringUtils.hasText(actor)) p.add(cb.like(cb.lower(root.get("actorEmail")),"%"+actor.trim().toLowerCase()+"%"));
            if(StringUtils.hasText(role)) p.add(cb.equal(cb.lower(root.get("actorRole")),role.trim().toLowerCase()));
            if(StringUtils.hasText(action)) p.add(cb.like(cb.lower(root.get("action")),"%"+action.trim().toLowerCase()+"%"));
            if(StringUtils.hasText(entityType)) p.add(cb.equal(cb.lower(root.get("entityType")),entityType.trim().toLowerCase()));
            if(entityId!=null) p.add(cb.equal(root.get("entityId"),entityId));
            if(fromDate!=null) p.add(cb.greaterThanOrEqualTo(root.get("createdAt"),fromDate.atStartOfDay()));
            if(toDate!=null) {
                LocalDateTime end=toDate.plusDays(1).atStartOfDay();
                p.add(cb.lessThan(root.get("createdAt"),end));
            }
            return cb.and(p.toArray(new Predicate[0]));
        };
        Page<AuditLog> result=auditLogRepository.findAll(spec,
                PageRequest.of(Math.max(page,0),25,Sort.by(Sort.Direction.DESC,"createdAt")));
        model.addAttribute("auditPage",result);
        model.addAttribute("logs",result.getContent());
        model.addAttribute("actor",actor); model.addAttribute("role",role);
        model.addAttribute("action",action); model.addAttribute("entityType",entityType);
        model.addAttribute("entityId",entityId); model.addAttribute("fromDate",fromDate);
        model.addAttribute("toDate",toDate);
        return "admin-audit";
    }
}
