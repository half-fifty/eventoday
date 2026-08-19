package com.min.edu.ai.rag;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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
            String content = """
                %s
                reasonCode: %s

                %s
                """.formatted(title, section.reasonCode(), section.body()).trim();
            Map<String, Object> metadata = Map.of(
                "policyType", definition.policyType().name(),
                "reasonCode", section.reasonCode(),
                "section", section.reasonCode(),
                "documentName", definition.documentName(),
                "version", definition.version()
            );
            documents.add(new Document(id(definition, section.reasonCode()), content, metadata));
        }
        return documents;
    }

    public List<PolicySection> parseSections(String markdown, String documentName) {
        List<PolicySection> sections = new ArrayList<>();
        String currentReasonCode = null;
        StringBuilder currentBody = new StringBuilder();
        for (String line : markdown.split("\\R", -1)) {
            if (line.startsWith("## ")) {
                appendSection(sections, currentReasonCode, currentBody, documentName);
                currentReasonCode = line.substring(3).trim();
                currentBody = new StringBuilder();
                continue;
            }
            if (currentReasonCode != null) {
                currentBody.append(line).append('\n');
            }
        }
        appendSection(sections, currentReasonCode, currentBody, documentName);
        return sections;
    }

    public List<PolicyDocumentDefinition> defaultDefinitions() {
        return List.of(
            new PolicyDocumentDefinition(PolicyType.REFUND, "refund-policy.md", 1),
            new PolicyDocumentDefinition(PolicyType.ADMISSION, "admission-policy.md", 1)
        );
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
            .anyMatch(section -> section.reasonCode().equals(reasonCode));
        if (duplicate) {
            throw new IllegalStateException(
                "Duplicate policy section in " + documentName + ": " + reasonCode
            );
        }
        sections.add(new PolicySection(reasonCode, text));
    }

    private String id(PolicyDocumentDefinition definition, String reasonCode) {
        String source = definition.policyType().name()
            + ":"
            + definition.documentName()
            + ":"
            + definition.version()
            + ":"
            + reasonCode;
        return UUID.nameUUIDFromBytes(source.getBytes(StandardCharsets.UTF_8)).toString();
    }

    public record PolicySection(String reasonCode, String body) {
    }
}
