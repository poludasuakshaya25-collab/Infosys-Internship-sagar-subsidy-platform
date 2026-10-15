package com.platform.disbursement;

import com.platform.application.ApplicationStatus;
import com.platform.application.SubsidyApplication;
import com.platform.application.SubsidyApplicationRepository;
import com.platform.scheme.Scheme;
import com.platform.scheme.SchemeRepository;
import com.platform.security.JurisdictionService;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
public class DisbursementAnalyticsService {

    private final SubsidyApplicationRepository applicationRepository;
    private final DisbursementPlanRepository planRepository;
    private final SchemeRepository schemeRepository;
    private final JurisdictionService jurisdictionService;

    public DisbursementAnalyticsService(SubsidyApplicationRepository applicationRepository,
                                        DisbursementPlanRepository planRepository,
                                        SchemeRepository schemeRepository,
                                        JurisdictionService jurisdictionService) {
        this.applicationRepository = applicationRepository;
        this.planRepository = planRepository;
        this.schemeRepository = schemeRepository;
        this.jurisdictionService = jurisdictionService;
    }

    @Transactional(readOnly = true)
    public AnalyticsSnapshot buildSnapshot() {
        return buildSnapshot(null);
    }

    @Transactional(readOnly = true)
    public AnalyticsSnapshot buildSnapshot(Long schemeId) {
        return buildSnapshot(schemeId, null);
    }

    @Transactional(readOnly = true)
    public AnalyticsSnapshot buildSnapshot(Long schemeId, UserDetails principal) {
        List<SubsidyApplication> applications = applicationRepository.findAll();
        if (principal != null) {
            applications = jurisdictionService.filter(principal, applications);
        }
        java.util.Set<Long> allowedApplicationIds = applications.stream()
                .map(SubsidyApplication::getId)
                .collect(Collectors.toSet());
        List<DisbursementPlan> plans = planRepository.findAll().stream()
                .filter(p -> p.getApplication() != null
                        && allowedApplicationIds.contains(p.getApplication().getId()))
                .toList();
        List<Scheme> schemes = schemeRepository.findAll();

        if (schemeId != null) {
            applications = applications.stream()
                    .filter(a -> a.getScheme() != null && schemeId.equals(a.getScheme().getId()))
                    .toList();
            plans = plans.stream()
                    .filter(p -> p.getApplication() != null
                            && p.getApplication().getScheme() != null
                            && schemeId.equals(p.getApplication().getScheme().getId()))
                    .toList();
            schemes = schemes.stream()
                    .filter(sc -> schemeId.equals(sc.getId()))
                    .toList();
        }

        Map<Long, DisbursementPlan> plansByApplication = plans.stream()
                .filter(p -> p.getApplication() != null && p.getApplication().getId() != null)
                .collect(Collectors.toMap(
                        p -> p.getApplication().getId(),
                        p -> p,
                        (a, b) -> a));

        double sanctioned = 0;
        double released = 0;
        double allocatedBudget = schemes.stream()
                .mapToDouble(s -> Math.max(0, s.getTotalRegionalBudget()))
                .sum();
        // Analytics remaining budget is based on ACTUAL fund release, not merely sanction.
        // Therefore it decreases immediately whenever a milestone is released.
        double remainingBudget = 0;

        int activePlans = 0;
        int completedPlans = 0;
        int pendingMilestones = 0;
        int overdueMilestones = 0;
        int approvedApplications = 0;

        Map<String, MutableSchemeSummary> schemeMap = new TreeMap<>();
        Map<String, MutableRegionSummary> regionMap = new TreeMap<>();

        for (SubsidyApplication app : applications) {
            double appSanction = Math.max(0, app.getSanctionAmount());
            DisbursementPlan plan = plansByApplication.get(app.getId());
            double appReleased = actualReleasedAmount(app, plan);

            sanctioned += appSanction;
            released += appReleased;
            if (app.getApplicationStatus() == ApplicationStatus.APPROVED) approvedApplications++;

            if (plan != null) {
                if (plan.getStatus() == DisbursementPlanStatus.COMPLETED) completedPlans++;
                else activePlans++;

                for (DisbursementMilestone milestone : plan.getMilestones()) {
                    if (milestone.getStatus() != MilestoneStatus.RELEASED) pendingMilestones++;
                    if (milestone.isOverdue()) overdueMilestones++;
                }
            }

            String schemeName = app.getScheme() != null && app.getScheme().getSchemeName() != null
                    ? app.getScheme().getSchemeName() : "Unassigned Scheme";
            double schemeBudget = app.getScheme() == null
                    ? 0 : Math.max(0, app.getScheme().getTotalRegionalBudget());
            double schemeRemaining = app.getScheme() == null
                    ? 0 : Math.max(0, app.getScheme().getRemainingBudget());

            schemeMap.computeIfAbsent(schemeName,
                            key -> new MutableSchemeSummary(key, schemeBudget, schemeRemaining))
                    .add(appSanction, appReleased);

            String region = app.getUser() != null && app.getUser().getBeneficiary() != null
                    && app.getUser().getBeneficiary().getState() != null
                    && !app.getUser().getBeneficiary().getState().isBlank()
                    ? app.getUser().getBeneficiary().getState().trim()
                    : "Region Not Available";

            regionMap.computeIfAbsent(region, MutableRegionSummary::new)
                    .add(appSanction, appReleased);
        }

        // Include schemes with zero applications so the analytics page represents
        // the complete configured scheme master, not only schemes already used.
        for (Scheme scheme : schemes) {
            schemeMap.computeIfAbsent(scheme.getSchemeName(),
                    key -> new MutableSchemeSummary(
                            key,
                            Math.max(0, scheme.getTotalRegionalBudget()),
                            Math.max(0, scheme.getRemainingBudget())));
        }

        remainingBudget = Math.max(0, allocatedBudget - released);

        double overallUtilization = sanctioned > 0 ? released * 100.0 / sanctioned : 0;
        double budgetUtilization = allocatedBudget > 0
                ? (released * 100.0 / allocatedBudget)
                : 0;

        return new AnalyticsSnapshot(
                applications.size(),
                approvedApplications,
                sanctioned,
                released,
                Math.max(0, sanctioned - released),
                overallUtilization,
                allocatedBudget,
                remainingBudget,
                budgetUtilization,
                activePlans,
                completedPlans,
                pendingMilestones,
                overdueMilestones,
                new ArrayList<>(schemeMap.values().stream().map(MutableSchemeSummary::toView).toList()),
                new ArrayList<>(regionMap.values().stream().map(MutableRegionSummary::toView).toList())
        );
    }

    private double actualReleasedAmount(SubsidyApplication app, DisbursementPlan plan) {
        if (plan != null) {
            return plan.getMilestones().stream()
                    .mapToDouble(DisbursementMilestone::getReleasedAmount)
                    .sum();
        }
        return app.getApplicationStatus() == ApplicationStatus.APPROVED && app.getDisbursedAt() != null
                ? Math.max(0, app.getSanctionAmount()) : 0;
    }

    private static class MutableSchemeSummary {
        private final String name;
        private final double allocatedBudget;
        private final double remainingBudget;
        private double sanctioned;
        private double released;
        private int applications;

        MutableSchemeSummary(String name, double allocatedBudget, double remainingBudget) {
            this.name = name;
            this.allocatedBudget = allocatedBudget;
            this.remainingBudget = remainingBudget;
        }

        void add(double sanctioned, double released) {
            this.sanctioned += sanctioned;
            this.released += released;
            this.applications++;
        }

        SchemeSummaryView toView() {
            double utilization = sanctioned > 0 ? released * 100.0 / sanctioned : 0;
            double budgetUtilization = allocatedBudget > 0
                    ? (released * 100.0 / allocatedBudget)
                    : 0;
            double liveRemainingBudget = Math.max(0, allocatedBudget - released);
            return new SchemeSummaryView(name, applications, allocatedBudget, liveRemainingBudget,
                    sanctioned, released, utilization, budgetUtilization);
        }
    }

    private static class MutableRegionSummary {
        private final String name;
        private double sanctioned;
        private double released;
        private int applications;

        MutableRegionSummary(String name) { this.name = name; }

        void add(double sanctioned, double released) {
            this.sanctioned += sanctioned;
            this.released += released;
            this.applications++;
        }

        RegionSummaryView toView() {
            double utilization = sanctioned > 0 ? released * 100.0 / sanctioned : 0;
            return new RegionSummaryView(name, applications, sanctioned, released, utilization);
        }
    }

    public record SchemeSummaryView(
            String name,
            int applications,
            double allocatedBudget,
            double remainingBudget,
            double sanctioned,
            double released,
            double utilization,
            double budgetUtilization) {}

    public record RegionSummaryView(
            String name,
            int applications,
            double sanctioned,
            double released,
            double utilization) {}

    public record AnalyticsSnapshot(
            int totalApplications,
            int approvedApplications,
            double totalSanctioned,
            double totalReleased,
            double totalPendingRelease,
            double overallUtilization,
            double totalAllocatedBudget,
            double totalRemainingBudget,
            double budgetUtilization,
            int activePlans,
            int completedPlans,
            int pendingMilestones,
            int overdueMilestones,
            List<SchemeSummaryView> schemeSummaries,
            List<RegionSummaryView> regionSummaries) {}
}
