package com.agentjars.catalog;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.agentjars.config.AgentJarsProperties;
import com.agentjars.model.AgentManifest;
import com.agentjars.model.JarFileEntry;
import com.agentjars.packaging.AgentManifestParser;
import com.agentjars.packaging.AgentScanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Reads the contents of a packaged AgentJar without writing it to disk. */
@Component
public class JarInspector {

    private static final Logger log = LoggerFactory.getLogger(JarInspector.class);

    private final AgentJarsProperties properties;
    private final AgentManifestParser parser;

    public JarInspector(AgentJarsProperties properties, AgentManifestParser parser) {
        this.properties = properties;
        this.parser = parser;
    }

    /** Every entry in the jar, sorted so that a tree renders in a sensible order. */
    public List<JarFileEntry> listEntries(byte[] jar) {
        List<JarFileEntry> entries = new ArrayList<>();
        if (jar == null || jar.length == 0) {
            return entries;
        }
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(jar))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries.add(entry.isDirectory()
                        ? JarFileEntry.directory(entry.getName())
                        : JarFileEntry.file(entry.getName(), sizeOf(entry, zip)));
            }
        } catch (IOException e) {
            log.warn("Could not read jar entries: {}", e.getMessage());
            return List.of();
        }
        entries.sort(Comparator.comparing(JarFileEntry::path));
        return List.copyOf(entries);
    }

    /** Reads a single text file out of the jar. */
    public Optional<String> readText(byte[] jar, String path) {
        if (jar == null || jar.length == 0) {
            return Optional.empty();
        }
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(jar))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!entry.isDirectory() && entry.getName().equals(path)) {
                    return Optional.of(new String(zip.readAllBytes(), StandardCharsets.UTF_8));
                }
            }
        } catch (IOException e) {
            log.warn("Could not read {} from jar: {}", path, e.getMessage());
        }
        return Optional.empty();
    }

    /** Parses the packaged {@code AGENT.md}, wherever under the agents root it lives. */
    public Optional<AgentManifest> readManifest(byte[] jar, String fallbackName) {
        if (jar == null || jar.length == 0) {
            return Optional.empty();
        }
        String root = properties.agentsRoot() + "/";
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(jar))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if (!entry.isDirectory()
                        && name.startsWith(root)
                        && name.endsWith("/" + AgentScanner.MANIFEST_FILE)) {
                    String content = new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                    return Optional.of(parser.parse(content, fallbackName));
                }
            }
        } catch (IOException e) {
            log.warn("Could not read the agent manifest from jar: {}", e.getMessage());
        }
        return Optional.empty();
    }

    /**
     * Zip entries only carry a size when the archive has a central directory entry for them; when
     * the stream reports none, the bytes are counted instead.
     */
    private static long sizeOf(ZipEntry entry, ZipInputStream zip) throws IOException {
        if (entry.getSize() >= 0) {
            return entry.getSize();
        }
        long total = 0;
        byte[] buffer = new byte[8192];
        int read;
        while ((read = zip.read(buffer)) > 0) {
            total += read;
        }
        return total;
    }
}
