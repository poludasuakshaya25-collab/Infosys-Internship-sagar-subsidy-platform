package com.platform.web;

import com.platform.application.ApplicationStatus;
import com.platform.application.SubsidyApplication;
import com.platform.application.SubsidyApplicationRepository;
import com.platform.security.JurisdictionService;
import com.platform.util.SimplePdfUtil;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Controller
public class OfficerHistoryExportController {
    private final SubsidyApplicationRepository applications;
    private final JurisdictionService jurisdiction;

    public OfficerHistoryExportController(SubsidyApplicationRepository applications, JurisdictionService jurisdiction) {
        this.applications = applications;
        this.jurisdiction = jurisdiction;
    }

    private List<SubsidyApplication> history(UserDetails principal) {
        if (principal == null) throw new org.springframework.security.access.AccessDeniedException("Login required");
        boolean field = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().contains("FIELD_OFFICER"));
        boolean district = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().contains("DISTRICT_OFFICER"));
        boolean finance = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().contains("FINANCE_APPROVER") || a.getAuthority().contains("DISBURSEMENT_OFFICER"));
        if (!field && !district && !finance) throw new org.springframework.security.access.AccessDeniedException("Officer access required");
        return jurisdiction.filter(principal, applications.findAllByOrderByApplicationDateDesc().stream()
                .filter(app -> {
                    if (field) return app.getFieldInspectionRemarks() != null
                            && app.getApplicationStatus() != ApplicationStatus.FIELD_VERIFICATION
                            && app.getApplicationStatus() != ApplicationStatus.RESUBMITTED;
                    if (district) return app.getDistrictReviewRemarks() != null
                            && app.getApplicationStatus() != ApplicationStatus.DISTRICT_REVIEW;
                    return app.getApplicationStatus() == ApplicationStatus.APPROVED
                            || app.getApplicationStatus() == ApplicationStatus.REJECTED;
                }).toList());
    }

    @GetMapping("/officer/applications/history/excel")
    public ResponseEntity<byte[]> excel(@AuthenticationPrincipal UserDetails principal) throws IOException {
        List<SubsidyApplication> records = history(principal);
        byte[] result;
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Completed History");
            String[] headings = {"Application ID", "Applicant", "Email", "Scheme", "Status", "Score", "Sanction Amount", "Application Date"};
            CellStyle headerStyle = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            headerStyle.setFont(font);
            Row header = sheet.createRow(0);
            for (int i = 0; i < headings.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(headings[i]);
                cell.setCellStyle(headerStyle);
            }
            int index = 1;
            for (SubsidyApplication app : records) {
                Row row = sheet.createRow(index++);
                row.createCell(0).setCellValue(app.getId() == null ? "" : app.getId().toString());
                row.createCell(1).setCellValue(app.getUser() == null ? "" : safe(app.getUser().getBeneficiary() == null ? app.getUser().getFullName() : app.getUser().getBeneficiary().getFullName()));
                row.createCell(2).setCellValue(app.getUser() == null ? "" : safe(app.getUser().getEmail()));
                row.createCell(3).setCellValue(app.getScheme() == null ? "" : safe(app.getScheme().getSchemeName()));
                row.createCell(4).setCellValue(app.getApplicationStatus() == null ? "" : app.getApplicationStatus().name());
                row.createCell(5).setCellValue(app.getEligibilityScore());
                row.createCell(6).setCellValue(app.getSanctionAmount());
                row.createCell(7).setCellValue(app.getApplicationDate() == null ? "" : app.getApplicationDate().toString());
            }
            sheet.createFreezePane(0, 1);
            if (index > 1) sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, index - 1, 0, headings.length - 1));
            for (int i = 0; i < headings.length; i++) sheet.setColumnWidth(i, i == 1 || i == 2 || i == 3 ? 7500 : 5200);
            workbook.write(output);
            result = output.toByteArray();
        }
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=officer-completed-history.xlsx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(result);
    }

    @GetMapping("/officer/applications/history/pdf")
    public ResponseEntity<byte[]> pdf(@AuthenticationPrincipal UserDetails principal) {
        List<String> lines = new ArrayList<>();
        List<SubsidyApplication> records = history(principal);
        lines.add("Completed applications: " + records.size());
        for (SubsidyApplication app : records.stream().limit(30).toList()) {
            lines.add("#" + app.getId() + " | " + app.getApplicationStatus() + " | " + app.getEligibilityScore() + "/100");
        }
        if (records.size() > 30) lines.add("Showing first 30 records. Download Excel for complete history.");
        byte[] pdf = SimplePdfUtil.onePage("SAGAR - Officer Completed History", lines);
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=officer-completed-history.pdf")
                .contentType(MediaType.APPLICATION_PDF).body(pdf);
    }

    private static String safe(String s) {
        if (s == null) return "";
        // Prevent spreadsheet formula execution for user-controlled text.
        return s.matches("^[\\s]*[=+@-].*") ? "'" + s : s;
    }
}
