package com.platform.util;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Small dependency-free one-page PDF writer for application documents/receipts. */
public final class SimplePdfUtil {
    private SimplePdfUtil() {}

    public static byte[] onePage(String title, List<String> lines) {
        List<String> all = new ArrayList<>();
        all.add(title);
        all.add("");
        if (lines != null) all.addAll(lines);

        StringBuilder stream = new StringBuilder();
        stream.append("BT\n/F1 18 Tf\n50 760 Td\n");
        stream.append("(").append(escape(all.get(0))).append(") Tj\n");
        stream.append("/F1 10 Tf\n");
        for (int i = 1; i < all.size(); i++) {
            stream.append("0 -18 Td\n(").append(escape(all.get(i))).append(") Tj\n");
        }
        stream.append("ET\n");
        byte[] content = stream.toString().getBytes(StandardCharsets.ISO_8859_1);

        String[] objects = new String[] {
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>",
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
                "<< /Length " + content.length + " >>\nstream\n" + new String(content, StandardCharsets.ISO_8859_1) + "endstream"
        };

        StringBuilder pdf = new StringBuilder("%PDF-1.4\n");
        List<Integer> offsets = new ArrayList<>();
        offsets.add(0);
        for (int i = 0; i < objects.length; i++) {
            offsets.add(pdf.toString().getBytes(StandardCharsets.ISO_8859_1).length);
            pdf.append(i + 1).append(" 0 obj\n").append(objects[i]).append("\nendobj\n");
        }
        int xref = pdf.toString().getBytes(StandardCharsets.ISO_8859_1).length;
        pdf.append("xref\n0 ").append(objects.length + 1).append("\n");
        pdf.append("0000000000 65535 f \n");
        for (int i = 1; i < offsets.size(); i++) {
            pdf.append(String.format("%010d 00000 n \n", offsets.get(i)));
        }
        pdf.append("trailer\n<< /Size ").append(objects.length + 1).append(" /Root 1 0 R >>\nstartxref\n")
                .append(xref).append("\n%%EOF\n");
        return pdf.toString().getBytes(StandardCharsets.ISO_8859_1);
    }

    private static String escape(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")
                .replace("\r", " ").replace("\n", " ");
    }
}
