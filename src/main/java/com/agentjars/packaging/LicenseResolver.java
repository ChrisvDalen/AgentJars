package com.agentjars.packaging;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.agentjars.model.DiscoveredAgent;
import org.springframework.stereotype.Component;

/**
 * Resolves the SPDX license identifier for a discovered agent.
 *
 * <p>Maven Central requires a license on every artifact, so an agent whose license cannot be
 * determined is skipped during deployment rather than failing the whole run. Resolution order,
 * most specific first: {@code AGENT.md} front matter, a LICENSE file next to the agent, the
 * repository's root LICENSE file.
 */
@Component
public class LicenseResolver {

    private static final List<String> LICENSE_FILE_NAMES =
            List.of("LICENSE", "LICENSE.txt", "LICENSE.md", "COPYING", "COPYING.txt");

    /** Fingerprints that identify the common licenses from the text of a LICENSE file. */
    private static final Map<String, String> FINGERPRINTS = Map.of(
            "apache license", "Apache-2.0",
            "mit license", "MIT",
            "permission is hereby granted, free of charge", "MIT",
            "gnu general public license", "GPL-3.0",
            "gnu lesser general public license", "LGPL-3.0",
            "mozilla public license", "MPL-2.0",
            "redistribution and use in source and binary forms", "BSD-3-Clause",
            "this is free and unencumbered software released into the public domain", "Unlicense",
            "eclipse public license", "EPL-2.0");

    /**
     * @param agent          the agent being packaged
     * @param repositoryRoot root of the checked-out repository
     * @return the SPDX identifier, or empty when no license could be resolved
     */
    public Optional<String> resolve(DiscoveredAgent agent, Path repositoryRoot) {
        String declared = agent.manifest().license();
        if (declared != null && !declared.isBlank()) {
            return Optional.of(declared.strip());
        }
        return fromDirectory(agent.directory()).or(() -> fromDirectory(repositoryRoot));
    }

    private Optional<String> fromDirectory(Path directory) {
        if (directory == null || !Files.isDirectory(directory)) {
            return Optional.empty();
        }
        for (String fileName : LICENSE_FILE_NAMES) {
            Path candidate = directory.resolve(fileName);
            if (Files.isRegularFile(candidate)) {
                Optional<String> identifier = identify(candidate);
                if (identifier.isPresent()) {
                    return identifier;
                }
            }
        }
        return Optional.empty();
    }

    private Optional<String> identify(Path licenseFile) {
        try {
            String text = Files.readString(licenseFile, StandardCharsets.UTF_8)
                    .toLowerCase(Locale.ROOT);
            return FINGERPRINTS.entrySet().stream()
                    .filter(entry -> text.contains(entry.getKey()))
                    .map(Map.Entry::getValue)
                    .findFirst();
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    /** Canonical URL for the well-known licenses, used in the generated pom. */
    public String urlFor(String spdxId) {
        return switch (spdxId == null ? "" : spdxId.toUpperCase(Locale.ROOT)) {
            case "APACHE-2.0" -> "https://www.apache.org/licenses/LICENSE-2.0.txt";
            case "MIT" -> "https://opensource.org/licenses/MIT";
            case "BSD-3-CLAUSE" -> "https://opensource.org/licenses/BSD-3-Clause";
            case "GPL-3.0" -> "https://www.gnu.org/licenses/gpl-3.0.txt";
            case "LGPL-3.0" -> "https://www.gnu.org/licenses/lgpl-3.0.txt";
            case "MPL-2.0" -> "https://www.mozilla.org/media/MPL/2.0/index.txt";
            case "EPL-2.0" -> "https://www.eclipse.org/legal/epl-2.0/";
            case "UNLICENSE" -> "https://unlicense.org/";
            default -> "https://spdx.org/licenses/" + spdxId;
        };
    }
}
