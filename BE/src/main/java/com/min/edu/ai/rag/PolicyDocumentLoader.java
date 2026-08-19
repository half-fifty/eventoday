package com.min.edu.ai.rag;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.ai.document.Document;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

@Component
public class PolicyDocumentLoader {

    public List<Document> loadAll(String policyLocation) {
        return loadAll(policyLocation, defaultDefinitions());
    }

    public List<Document> loadAll(
            String policyLocation,
            List<PolicyDocumentDefinition> definitions) {
        List<Document> documents = new ArrayList<>();
        for (PolicyDocumentDefinition definition : definitions) {
            documents.addAll(load(policyLocation, definition));
        }
        return documents;
    }

    public List<Document> load(String policyLocation, PolicyDocumentDefinition definition) {
        String markdown = read(policyLocation, definition.documentName());
        String title = parseTitle(markdown);
        List<PolicySection> sections = parseSections(markdown, definition.documentName());
        List<Document> documents = new ArrayList<>();
        for (PolicySection section : sections) {
            String content = content(title, section, definition.reasonCodeMapped());
            Map<String, Object> metadata = metadata(definition, section);
            documents.add(new Document(id(definition, section.section()), content, metadata));
        }
        return documents;
    }

    public List<PolicySection> parseSections(String markdown, String documentName) {
        List<PolicySection> sections = new ArrayList<>();
        String currentSection = null;
        StringBuilder currentBody = new StringBuilder();
        for (String line : markdown.split("\\R", -1)) {
            if (line.startsWith("## ")) {
                appendSection(sections, currentSection, currentBody, documentName);
                currentSection = line.substring(3).trim();
                currentBody = new StringBuilder();
                continue;
            }
            if (currentSection != null) {
                currentBody.append(line).append('\n');
            }
        }
        appendSection(sections, currentSection, currentBody, documentName);
        return sections;
    }

    public List<PolicyDocumentDefinition> defaultDefinitions() {
        return List.of(
            new PolicyDocumentDefinition(PolicyType.REFUND, "refund-policy.md", 1),
            new PolicyDocumentDefinition(PolicyType.ADMISSION, "admission-policy.md", 1),
            new PolicyDocumentDefinition(PolicyType.EXCHANGE_CODE, "exchange-code-policy.md", 1, false),
            new PolicyDocumentDefinition(PolicyType.TICKET_OPERATION, "ticket-operation-policy.md", 1, false)
        );
    }

    private String content(String title, PolicySection section, boolean reasonCodeMapped) {
        if (reasonCodeMapped) {
            return """
                %s
                reasonCode: %s

                %s
                """.formatted(title, section.section(), section.body()).trim();
        }
        return """
            %s
            section: %s

            %s
            """.formatted(title, section.section(), section.body()).trim();
    }

    private Map<String, Object> metadata(
            PolicyDocumentDefinition definition,
            PolicySection section) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("policyType", definition.policyType().name());
        metadata.put("section", section.section());
        metadata.put("documentName", definition.documentName());
        metadata.put("version", definition.version());
        if (definition.reasonCodeMapped()) {
            metadata.put("reasonCode", section.section());
        }
        return Map.copyOf(metadata);
    }

    private String read(String policyLocation, String documentName) {
        String path = policyLocation.replace("classpath:", "");
        if (!path.endsWith("/")) {
            path = path + "/";
        }
        ClassPathResource resource = new ClassPathResource(path + documentName);
        try {
            return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load policy document: " + documentName, exception);
        }
    }

    private String parseTitle(String markdown) {
        for (String line : markdown.split("\\R")) {
            if (line.startsWith("# ")) {
                return line.substring(2).trim();
            }
        }
        return "Eventoday Policy";
    }

    private void appendSection(
            List<PolicySection> sections,
            String reasonCode,
            StringBuilder body,
            String documentName) {
        if (reasonCode == null) {
            return;
        }
        if (!reasonCode.matches("[A-Z][A-Z0-9_]*")) {
            throw new IllegalStateException(
                "Invalid policy section heading in " + documentName + ": " + reasonCode
            );
        }
        String text = body.toString().trim();
        if (text.isBlank()) {
            throw new IllegalStateException(
                "Empty policy section in " + documentName + ": " + reasonCode
            );
        }
        boolean duplicate = sections.stream()
            .anyMatch(section -> section.section().equals(reasonCode));
        if (duplicate) {
            throw new IllegalStateException(
                "Duplicate policy section in " + documentName + ": " + reasonCode
            );
        }
        sections.add(new PolicySection(reasonCode, text));
    }

    private String id(PolicyDocumentDefinition definition, String section) {
        String source = definition.policyType().name()
            + ":"
            + definition.documentName()
            + ":"
            + definition.version()
            + ":"
            + section;
        return UUID.nameUUIDFromBytes(source.getBytes(StandardCharsets.UTF_8)).toString();
    }

    public record PolicySection(String section, String body) {
    }
}
