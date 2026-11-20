package com.swiftdrop.backend.notification.service;

import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class TemplateRenderer {

    /**
     * Replaces placeholders of the format {{key}} in template with values from variables map.
     */
    public String render(String template, Map<String, Object> variables) {
        if (template == null) {
            return "";
        }
        String result = template;
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            String placeholder = "{{" + entry.getKey() + "}}";
            String replacement = entry.getValue() != null ? String.valueOf(entry.getValue()) : "";
            result = result.replace(placeholder, replacement);
        }
        return result;
    }
}

