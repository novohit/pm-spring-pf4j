package com.pmplugin4j.manager;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.pf4j.JarPluginRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Discovers the latest plugin JAR from each plugin-specific directory. */
public class PmJarPluginRepository extends JarPluginRepository {

    private static final Logger log = LoggerFactory.getLogger(PmJarPluginRepository.class);

    public PmJarPluginRepository(List<Path> pluginsRoots) {
        super(pluginsRoots);
    }

    @Override
    protected Stream<File> streamFiles(Path directory, FileFilter filter) {
        Stream<File> jarFiles = findLatestJarFilesInRoot(directory);
        return filter != null ? jarFiles.filter(filter::accept) : jarFiles;
    }

    Stream<File> findLatestJarFilesInRoot(Path pluginRoot) {
        if (!Files.isDirectory(pluginRoot)) {
            log.warn("Plugin root is not a directory: {}", pluginRoot);
            return Stream.empty();
        }
        try (Stream<Path> subDirectories = Files.list(pluginRoot)) {
            return subDirectories.filter(Files::isDirectory)
                .flatMap(this::findLatestJarFileInDirectory)
                .toList()
                .stream();
        } catch (IOException exception) {
            log.error("Failed to list plugin root directory: {}", pluginRoot, exception);
            return Stream.empty();
        }
    }

    private Stream<File> findLatestJarFileInDirectory(Path pluginDirectory) {
        try (Stream<Path> paths = Files.list(pluginDirectory)) {
            String baseName = removeVersion(pluginDirectory.getFileName().toString());
            return paths.filter(Files::isRegularFile)
                .filter(path -> path.toString().toLowerCase().endsWith(".jar"))
                .map(path -> toVersionedJar(path, baseName))
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(VersionedJar::baseName,
                        Collectors.maxBy(Comparator.comparing(VersionedJar::version))))
                .values()
                .stream()
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(VersionedJar::file)
                .toList()
                .stream();
        } catch (IOException exception) {
            log.error("Failed to read plugin directory: {}", pluginDirectory, exception);
            return Stream.empty();
        }
    }

    private VersionedJar toVersionedJar(Path path, String expectedBaseName) {
        String fileName = path.getFileName().toString();
        if (!fileName.toLowerCase().endsWith(".jar")) {
            return null;
        }
        String nameWithoutExtension = fileName.substring(0, fileName.length() - 4);
        if (!nameWithoutExtension.startsWith(expectedBaseName + "-")) {
            return null;
        }
        String versionText = nameWithoutExtension.substring(expectedBaseName.length() + 1);
        if (versionText.isEmpty() || !versionText.matches(".*\\d.*")) {
            return null;
        }
        return new VersionedJar(path.toFile(), expectedBaseName, new Version(versionText));
    }

    private String removeVersion(String name) {
        return name.replaceFirst("-\\d+(?:\\.\\d+)*(?:-[a-zA-Z0-9.-]+)?$", "");
    }

    private record VersionedJar(File file, String baseName, Version version) {
    }

    private static final class Version implements Comparable<Version> {

        private static final Pattern VERSION_PATTERN = Pattern.compile("^(\\d+(?:\\.\\d+)*)(?:-([a-zA-Z0-9.-]+))?$");
        private final String release;
        private final String preRelease;

        private Version(String versionText) {
            Matcher matcher = VERSION_PATTERN.matcher(versionText.toLowerCase().trim());
            if (matcher.matches()) {
                release = matcher.group(1);
                preRelease = matcher.group(2);
            } else {
                release = "0.0.0";
                preRelease = versionText.toLowerCase().trim();
            }
        }

        @Override
        public int compareTo(Version other) {
            int releaseComparison = compareRelease(release, other.release);
            if (releaseComparison != 0) {
                return releaseComparison;
            }
            if (preRelease == null && other.preRelease != null) {
                return 1;
            }
            if (preRelease != null && other.preRelease == null) {
                return -1;
            }
            return preRelease == null ? 0 : preRelease.compareTo(other.preRelease);
        }

        private static int compareRelease(String first, String second) {
            String[] firstParts = first.split("\\.");
            String[] secondParts = second.split("\\.");
            int length = Math.max(firstParts.length, secondParts.length);
            for (int index = 0; index < length; index++) {
                int firstPart = index < firstParts.length ? Integer.parseInt(firstParts[index]) : 0;
                int secondPart = index < secondParts.length ? Integer.parseInt(secondParts[index]) : 0;
                if (firstPart != secondPart) {
                    return Integer.compare(firstPart, secondPart);
                }
            }
            return 0;
        }
    }
}
