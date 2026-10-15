package com.platform.disbursement;

import com.platform.application.SubsidyApplication;
import com.platform.application.SubsidyApplicationRepository;
import com.platform.security.JurisdictionService;
import com.platform.util.SimplePdfUtil;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import java.io.*;
import java.util.*;

@Controller
@RequestMapping("/milestone3/reports")
public class Milestone3ReportController {
    private final DisbursementAnalyticsService analytics;
    private final DisbursementPlanService plans;
    private final SubsidyApplicationRepository applications;
    private final JurisdictionService jurisdiction;

    public Milestone3ReportController(DisbursementAnalyticsService analytics, DisbursementPlanService plans,
                                      SubsidyApplicationRepository applications, JurisdictionService jurisdiction) {
        this.analytics = analytics; this.plans = plans; this.applications = applications; this.jurisdiction = jurisdiction;
    }
    private void officer(UserDetails user) {
        if (user == null || user.getAuthorities().stream().noneMatch(a ->
                List.of("ROLE_ADMIN","ROLE_FIELD_OFFICER","ROLE_DISTRICT_OFFICER","ROLE_FINANCE_APPROVER","ROLE_DISBURSEMENT_OFFICER").contains(a.getAuthority())))
            throw new AccessDeniedException("Officer role required");
    }
    private void finance(UserDetails user) {
        if (user == null || user.getAuthorities().stream().noneMatch(a ->
                List.of("ROLE_FINANCE_APPROVER","ROLE_DISBURSEMENT_OFFICER").contains(a.getAuthority())))
            throw new AccessDeniedException("Finance role required");
    }
    private DisbursementPlan plan(Long id, UserDetails user) {
        finance(user);
        SubsidyApplication app = applications.findDetailedById(id).orElseThrow(() -> new IllegalArgumentException("Application not found"));
        jurisdiction.requireAccess(user, app);
        return plans.getOrCreatePlan(app);
    }
    private ResponseEntity<byte[]> download(byte[] bytes, String name, String type) {
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\""+name+"\"")
                .contentType(MediaType.parseMediaType(type)).body(bytes);
    }
    private static final String XLSX="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static void header(Sheet s, String... cols) {
        Row r=s.createRow(0); for(int i=0;i<cols.length;i++)r.createCell(i).setCellValue(cols[i]);
        s.createFreezePane(0,1); for(int i=0;i<cols.length;i++)s.setColumnWidth(i,6800);
    }
    private static byte[] bytes(Workbook book) throws IOException {
        try(book; ByteArrayOutputStream out=new ByteArrayOutputStream()){book.write(out);return out.toByteArray();}
    }
    private static void num(Row r,int col,double v){r.createCell(col).setCellValue(v);}
    private static void val(Row r,int col,Object v){r.createCell(col).setCellValue(v==null?"":String.valueOf(v));}

    @GetMapping("/analytics/excel")
    @Transactional(readOnly=true)
    public ResponseEntity<byte[]> analyticsExcel(@RequestParam(required=false) Long schemeId,
                                                 @AuthenticationPrincipal UserDetails user) throws IOException {
        officer(user); var a=analytics.buildSnapshot(schemeId,user);
        Workbook book=new XSSFWorkbook(); Sheet summary=book.createSheet("Overview");
        header(summary,"Metric","Value");
        Object[][] metrics={{"Applications",a.totalApplications()},{"Approved",a.approvedApplications()},
                {"Sanctioned",a.totalSanctioned()},{"Released",a.totalReleased()},
                {"Pending release",a.totalPendingRelease()},{"Allocated budget",a.totalAllocatedBudget()},
                {"Remaining budget",a.totalRemainingBudget()},{"Budget utilization %",a.budgetUtilization()},
                {"Active plans",a.activePlans()},{"Completed plans",a.completedPlans()},
                {"Pending milestones",a.pendingMilestones()},{"Overdue milestones",a.overdueMilestones()}};
        for(int i=0;i<metrics.length;i++){Row r=summary.createRow(i+1);val(r,0,metrics[i][0]);
            if(metrics[i][1] instanceof Number n)num(r,1,n.doubleValue());else val(r,1,metrics[i][1]);}
        Sheet schemes=book.createSheet("Schemes");header(schemes,"Scheme","Applications","Allocated","Remaining","Sanctioned","Released","Utilization %","Budget utilization %");
        int n=1;for(var s:a.schemeSummaries()){Row r=schemes.createRow(n++);val(r,0,s.name());num(r,1,s.applications());num(r,2,s.allocatedBudget());num(r,3,s.remainingBudget());num(r,4,s.sanctioned());num(r,5,s.released());num(r,6,s.utilization());num(r,7,s.budgetUtilization());}
        Sheet regions=book.createSheet("Regions");header(regions,"Region","Applications","Sanctioned","Released","Utilization %");
        n=1;for(var s:a.regionSummaries()){Row r=regions.createRow(n++);val(r,0,s.name());num(r,1,s.applications());num(r,2,s.sanctioned());num(r,3,s.released());num(r,4,s.utilization());}
        return download(bytes(book),"sagar-analytics.xlsx",XLSX);
    }
    @GetMapping("/analytics/pdf")
    @Transactional(readOnly=true)
    public ResponseEntity<byte[]> analyticsPdf(@RequestParam(required=false) Long schemeId,
                                               @AuthenticationPrincipal UserDetails user) {
        officer(user);var a=analytics.buildSnapshot(schemeId,user);
        List<String> lines=new ArrayList<>(List.of("Applications: "+a.totalApplications(),"Approved: "+a.approvedApplications(),
                "Sanctioned: "+a.totalSanctioned(),"Released: "+a.totalReleased(),"Pending: "+a.totalPendingRelease(),
                "Allocated budget: "+a.totalAllocatedBudget(),"Remaining budget: "+a.totalRemainingBudget(),
                "Active plans: "+a.activePlans(),"Completed plans: "+a.completedPlans(),"Overdue milestones: "+a.overdueMilestones(),"", "Schemes:"));
        a.schemeSummaries().stream().limit(12).forEach(s->lines.add(s.name()+": "+s.applications()+" apps, released "+s.released()));
        return download(SimplePdfUtil.onePage("SAGAR - Fund Analytics",lines),"sagar-analytics.pdf","application/pdf");
    }
    @GetMapping("/disbursement/{id}/excel")
    @Transactional
    public ResponseEntity<byte[]> disbursementExcel(@PathVariable Long id,
                                                    @AuthenticationPrincipal UserDetails user) throws IOException {
        DisbursementPlan p=plan(id,user);Workbook book=new XSSFWorkbook();Sheet sheet=book.createSheet("Milestones");
        header(sheet,"Sequence","Milestone","Due date","Due time","Status","Scheduled","Released","Reference","Compliance requested","Last reminder");
        int n=1;for(var m:p.getMilestones()){Row r=sheet.createRow(n++);num(r,0,m.getSequenceNo());val(r,1,m.getMilestoneName());val(r,2,m.getDueDate());val(r,3,m.getDueTime());val(r,4,m.getStatus());num(r,5,m.getScheduledAmount());num(r,6,m.getReleasedAmount());val(r,7,m.getReleaseReference());val(r,8,m.isComplianceRequested());val(r,9,m.getLastReminderType());}
        return download(bytes(book),"sagar-disbursement-"+id+".xlsx",XLSX);
    }
    @GetMapping("/disbursement/{id}/pdf")
    @Transactional
    public ResponseEntity<byte[]> disbursementPdf(@PathVariable Long id,
                                                  @AuthenticationPrincipal UserDetails user) {
        DisbursementPlan p=plan(id,user);List<String> lines=new ArrayList<>(List.of(
                "Application: "+id,"Status: "+p.getStatus(),"Planned: "+p.getTotalPlannedAmount(),
                "Released: "+p.getReleasedAmount(),"Pending: "+p.getPendingAmount(),"", "Milestones:"));
        p.getMilestones().stream().limit(18).forEach(m->lines.add(m.getSequenceNo()+". "+m.getMilestoneName()+" | "+m.getStatus()+" | "+m.getReleasedAmount()));
        return download(SimplePdfUtil.onePage("SAGAR - Disbursement Plan",lines),"sagar-disbursement-"+id+".pdf","application/pdf");
    }
}
