package com.platform.util;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Converts the comma-separated document configuration stored on Scheme into
 * stable keys used by HTML file inputs and ApplicationDocument records.
 */
public final class DocumentKeyUtil {

    private DocumentKeyUtil() { }

    public static LinkedHashMap<String, String> parse(String csv) {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        if (csv == null || csv.isBlank()) {
            return result;
        }

        for (String raw : csv.split(",")) {
            String name = raw == null ? "" : raw.trim();
            if (!name.isBlank()) {
                result.put(key(name), name);
            }
        }
        return result;
    }

    public static String key(String documentName) {
        if (documentName == null) return "document";
        String key = documentName.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        return key.isBlank() ? "document" : key;
    }

    public static boolean containsDocument(Map<String, ?> documents, String documentName) {
        return documents != null && documents.containsKey(key(documentName));
    }
}
