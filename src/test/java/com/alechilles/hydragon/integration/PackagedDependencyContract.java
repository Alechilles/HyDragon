package com.alechilles.hydragon.integration;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.NodeList;

/** Test-stage verifier for the packaged HyDragon/Tamework dependency contract. */
final class PackagedDependencyContract {
    private static final String TAMEWORK_DEPENDENCY =
            "Alechilles:Alec's Tamework!";
    private static final Pattern VERSION = Pattern.compile(
            "(\\d+)\\.(\\d+)\\.(\\d+)");
    private static final Pattern RANGE_CLAUSE = Pattern.compile(
            "(>=|<=|>|<|=)?(\\d+\\.\\d+\\.\\d+)");

    enum IssueCode {
        HYDRAGON_JAR_MISSING,
        TAMEWORK_JAR_MISSING,
        PACKAGED_MANIFEST_UNREADABLE,
        PACKAGED_VERSION_MISSING,
        PACKAGED_VERSION_MISMATCH,
        TAMEWORK_DEPENDENCY_MISSING,
        POM_UNREADABLE,
        POM_TAMEWORK_VERSION_MISSING,
        DEPENDENCY_RANGE_MISMATCH,
        DEPENDENCY_RANGE_INVALID,
        TAMEWORK_VERSION_MISMATCH,
        TAMEWORK_VERSION_OUT_OF_RANGE
    }

    record Issue(IssueCode code, String detail) {
    }

    record Evidence(
            String hydragonVersion,
            String tameworkVersion,
            String pomTameworkVersion,
            String manifestDependencyRange) {
    }

    record Verification(Evidence evidence, List<Issue> issues) {
        Verification {
            issues = List.copyOf(issues);
        }

        boolean valid() {
            return issues.isEmpty();
        }

        List<IssueCode> issueCodes() {
            return issues.stream().map(Issue::code).toList();
        }

        String describe() {
            return issues.toString();
        }
    }

    private record Artifact(String version, String dependencyRange) {
    }

    private record SemanticVersion(int major, int minor, int patch)
            implements Comparable<SemanticVersion> {
        @Override
        public int compareTo(SemanticVersion other) {
            int majorOrder = Integer.compare(major, other.major);
            if (majorOrder != 0) return majorOrder;
            int minorOrder = Integer.compare(minor, other.minor);
            if (minorOrder != 0) return minorOrder;
            return Integer.compare(patch, other.patch);
        }
    }

    private PackagedDependencyContract() {
    }

    static Verification verify(
            Path hydragonJar,
            Path tameworkJar,
            Path pom,
            String bridgeRange) {
        List<Issue> issues = new ArrayList<>();
        Artifact hydragon = readArtifact(
                hydragonJar, true, IssueCode.HYDRAGON_JAR_MISSING, issues);
        Artifact tamework = readArtifact(
                tameworkJar, false, IssueCode.TAMEWORK_JAR_MISSING, issues);
        String pomTameworkVersion = readPomTameworkVersion(pom, issues);

        String manifestRange = hydragon == null
                ? null : hydragon.dependencyRange();
        compareRanges(manifestRange, bridgeRange, issues);
        compareTameworkVersions(tamework, pomTameworkVersion, issues);
        verifySupportedVersion(tamework, manifestRange, issues);

        return new Verification(new Evidence(
                hydragon == null ? null : hydragon.version(),
                tamework == null ? null : tamework.version(),
                pomTameworkVersion,
                manifestRange), issues);
    }

    private static Artifact readArtifact(
            Path jar,
            boolean requireTameworkDependency,
            IssueCode missingCode,
            List<Issue> issues) {
        Path normalized = jar.toAbsolutePath().normalize();
        if (!java.nio.file.Files.isRegularFile(normalized)) {
            issues.add(new Issue(missingCode, normalized.toString()));
            return null;
        }
        try (ZipFile zip = new ZipFile(normalized.toFile())) {
            JsonObject manifest = JsonParser.parseString(
                    readText(zip, "manifest.json")).getAsJsonObject();
            String version = jsonString(manifest, "Version");
            if (version == null) {
                issues.add(new Issue(
                        IssueCode.PACKAGED_VERSION_MISSING,
                        normalized + " manifest.json"));
            }
            verifyJavaManifestVersion(zip, normalized, version, issues);
            String range = requiredDependencyRange(manifest);
            if (requireTameworkDependency && range == null) {
                issues.add(new Issue(
                        IssueCode.TAMEWORK_DEPENDENCY_MISSING,
                        normalized.toString()));
            }
            return new Artifact(version, range);
        } catch (IOException | RuntimeException failure) {
            issues.add(new Issue(
                    IssueCode.PACKAGED_MANIFEST_UNREADABLE,
                    normalized + ": " + failure.getMessage()));
            return null;
        }
    }

    private static void verifyJavaManifestVersion(
            ZipFile zip,
            Path jar,
            String pluginVersion,
            List<Issue> issues) throws IOException {
        ZipEntry entry = zip.getEntry("META-INF/MANIFEST.MF");
        if (entry == null) {
            issues.add(new Issue(
                    IssueCode.PACKAGED_VERSION_MISSING,
                    jar + " META-INF/MANIFEST.MF"));
            return;
        }
        String javaManifestVersion;
        try (InputStream input = zip.getInputStream(entry)) {
            javaManifestVersion = new Manifest(input)
                    .getMainAttributes().getValue("Plugin-Version");
        }
        if (javaManifestVersion == null) {
            issues.add(new Issue(
                    IssueCode.PACKAGED_VERSION_MISSING,
                    jar + " Plugin-Version"));
        } else if (pluginVersion != null
                && !pluginVersion.equals(javaManifestVersion)) {
            issues.add(new Issue(
                    IssueCode.PACKAGED_VERSION_MISMATCH,
                    jar + " manifest.json=" + pluginVersion
                            + ", Plugin-Version=" + javaManifestVersion));
        }
    }

    private static String readPomTameworkVersion(
            Path pom,
            List<Issue> issues) {
        Path normalized = pom.toAbsolutePath().normalize();
        if (!java.nio.file.Files.isRegularFile(normalized)) {
            issues.add(new Issue(
                    IssueCode.POM_UNREADABLE, normalized.toString()));
            return null;
        }
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(
                    "http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature(
                    "http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature(
                    "http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            NodeList versions = factory.newDocumentBuilder()
                    .parse(normalized.toFile())
                    .getElementsByTagNameNS("*", "tamework.version");
            if (versions.getLength() != 1) {
                issues.add(new Issue(
                        IssueCode.POM_TAMEWORK_VERSION_MISSING,
                        normalized + " count=" + versions.getLength()));
                return null;
            }
            String version = versions.item(0).getTextContent().trim();
            if (!version.isEmpty()) return version;
            issues.add(new Issue(
                    IssueCode.POM_TAMEWORK_VERSION_MISSING,
                    normalized.toString()));
        } catch (Exception failure) {
            issues.add(new Issue(
                    IssueCode.POM_UNREADABLE,
                    normalized + ": " + failure.getClass().getSimpleName()));
        }
        return null;
    }

    private static void compareRanges(
            String manifestRange,
            String bridgeRange,
            List<Issue> issues) {
        if (manifestRange == null || bridgeRange == null) return;
        if (!normalizeRange(manifestRange).equals(normalizeRange(bridgeRange))) {
            issues.add(new Issue(
                    IssueCode.DEPENDENCY_RANGE_MISMATCH,
                    "manifest=" + manifestRange + ", bridge=" + bridgeRange));
        }
    }

    private static void compareTameworkVersions(
            Artifact tamework,
            String pomVersion,
            List<Issue> issues) {
        if (tamework == null || tamework.version() == null || pomVersion == null) {
            return;
        }
        if (!tamework.version().equals(pomVersion)) {
            issues.add(new Issue(
                    IssueCode.TAMEWORK_VERSION_MISMATCH,
                    "artifact=" + tamework.version() + ", pom=" + pomVersion));
        }
    }

    private static void verifySupportedVersion(
            Artifact tamework,
            String manifestRange,
            List<Issue> issues) {
        if (tamework == null || tamework.version() == null || manifestRange == null) {
            return;
        }
        try {
            if (!contains(manifestRange, tamework.version())) {
                issues.add(new Issue(
                        IssueCode.TAMEWORK_VERSION_OUT_OF_RANGE,
                        tamework.version() + " is outside " + manifestRange));
            }
        } catch (IllegalArgumentException failure) {
            issues.add(new Issue(
                    IssueCode.DEPENDENCY_RANGE_INVALID,
                    failure.getMessage()));
        }
    }

    private static boolean contains(String range, String version) {
        SemanticVersion candidate = semanticVersion(version);
        String[] clauses = normalizeRange(range).split(" ");
        if (clauses.length == 0) {
            throw new IllegalArgumentException("empty dependency range");
        }
        for (String clause : clauses) {
            Matcher matcher = RANGE_CLAUSE.matcher(clause);
            if (!matcher.matches()) {
                throw new IllegalArgumentException(
                        "unsupported dependency range clause: " + clause);
            }
            String operator = matcher.group(1) == null ? "=" : matcher.group(1);
            int comparison = candidate.compareTo(semanticVersion(matcher.group(2)));
            boolean accepted = switch (operator) {
                case ">=" -> comparison >= 0;
                case "<=" -> comparison <= 0;
                case ">" -> comparison > 0;
                case "<" -> comparison < 0;
                case "=" -> comparison == 0;
                default -> false;
            };
            if (!accepted) return false;
        }
        return true;
    }

    private static SemanticVersion semanticVersion(String value) {
        Matcher matcher = VERSION.matcher(value);
        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                    "unsupported semantic version: " + value);
        }
        return new SemanticVersion(
                Integer.parseInt(matcher.group(1)),
                Integer.parseInt(matcher.group(2)),
                Integer.parseInt(matcher.group(3)));
    }

    private static String normalizeRange(String range) {
        return range.trim().replaceAll("\\s+", " ");
    }

    private static String readText(ZipFile zip, String entryName)
            throws IOException {
        ZipEntry entry = zip.getEntry(entryName);
        if (entry == null) {
            throw new IOException("missing " + entryName);
        }
        try (InputStream input = zip.getInputStream(entry)) {
            return new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    private static String requiredDependencyRange(JsonObject manifest) {
        JsonElement dependencies = manifest.get("Dependencies");
        if (dependencies == null || !dependencies.isJsonObject()) return null;
        return jsonString(dependencies.getAsJsonObject(), TAMEWORK_DEPENDENCY);
    }

    private static String jsonString(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive()
                && value.getAsJsonPrimitive().isString()
                ? value.getAsString() : null;
    }
}
