package org.ease.mvp.configuration;

import org.ease.mvp.support.Json;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ConfigurationFiles {
    public static final String DEFAULT_RESOURCE = "/config/default-deployment.json";
    public static final String OVERLAY_SCHEMA_VERSION = "ease-deployment-overlay/v1";

    private ConfigurationFiles() {
    }

    public static DeploymentConfiguration loadDefault() {
        return loadResource(DEFAULT_RESOURCE);
    }

    public static DeploymentConfiguration loadBundled(String reference) {
        if (reference == null || !reference.startsWith("bundled:")) {
            throw new IllegalArgumentException(
                    "Bundled configuration reference must start with 'bundled:'"
            );
        }
        String id = reference.substring("bundled:".length());
        if ("default".equals(id)) return loadDefault();
        if (!id.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,99}")) {
            throw new IllegalArgumentException("Invalid bundled configuration reference: " + reference);
        }
        return parse(readBundledText("/config/examples/" + id + ".json"));
    }

    public static String readBundledText(String resource) {
        try (InputStream input = ConfigurationFiles.class.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IllegalArgumentException("Bundled resource is missing: " + resource);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Cannot read bundled resource: " + resource, exception);
        }
    }

    public static DeploymentConfiguration load(Path path) {
        if (path == null) throw new IllegalArgumentException("Configuration path is required");
        try {
            return parse(Files.readString(path, StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new IllegalArgumentException(
                    "Cannot read configuration file '" + path + "': " + exception.getMessage(),
                    exception
            );
        }
    }

    public static void save(Path path, DeploymentConfiguration configuration) {
        if (path == null) throw new IllegalArgumentException("Configuration output path is required");
        if (configuration == null) throw new IllegalArgumentException("Configuration is required");
        try {
            Path parent = path.toAbsolutePath().getParent();
            if (parent != null) Files.createDirectories(parent);
            Files.writeString(path, ConfigurationCodec.write(configuration), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalArgumentException(
                    "Cannot save configuration file '" + path + "': " + exception.getMessage(),
                    exception
            );
        }
    }

    public static DeploymentConfiguration parse(String json) {
        Map<String, Object> root = ConfigurationCodec.object(Json.parse(json), "$");
        if (!OVERLAY_SCHEMA_VERSION.equals(root.get("schemaVersion"))) {
            return ConfigurationCodec.fromMap(root);
        }
        String baseReference = ConfigurationCodec.string(root, "extends", "$");
        if (!baseReference.startsWith("bundled:")) {
            throw new IllegalArgumentException(
                    "Deployment overlays may extend a bundled configuration only"
            );
        }
        Map<String, Object> patch = ConfigurationCodec.object(
                ConfigurationCodec.required(root, "patch", "$"),
                "$.patch"
        );
        Map<String, Object> merged = deepMerge(
                loadBundled(baseReference).toMap(),
                patch
        );
        return ConfigurationCodec.fromMap(merged);
    }

    private static DeploymentConfiguration loadResource(String resource) {
        return parse(readBundledText(resource));
    }

    private static Map<String, Object> deepMerge(
            Map<String, Object> base,
            Map<String, Object> patch
    ) {
        Map<String, Object> merged = new LinkedHashMap<>(base);
        for (Map.Entry<String, Object> entry : patch.entrySet()) {
            Object previous = merged.get(entry.getKey());
            Object replacement = entry.getValue();
            if (previous instanceof Map<?, ?> previousMap
                    && replacement instanceof Map<?, ?> replacementMap) {
                merged.put(
                        entry.getKey(),
                        deepMerge(
                                ConfigurationCodec.object(previousMap, "$base"),
                                ConfigurationCodec.object(replacementMap, "$patch")
                        )
                );
            } else if (previous instanceof List<?> previousList
                    && replacement instanceof List<?> replacementList
                    && hasIdentifiers(previousList)
                    && hasIdentifiers(replacementList)) {
                merged.put(entry.getKey(), mergeByIdentifier(previousList, replacementList));
            } else {
                merged.put(entry.getKey(), replacement);
            }
        }
        return merged;
    }

    private static boolean hasIdentifiers(List<?> values) {
        return !values.isEmpty() && values.stream().allMatch(
                value -> value instanceof Map<?, ?> map && map.get("id") != null
        );
    }

    private static List<Object> mergeByIdentifier(List<?> base, List<?> patch) {
        Map<String, Object> patches = new LinkedHashMap<>();
        for (Object item : patch) {
            Map<String, Object> map = ConfigurationCodec.object(item, "$patch[]");
            patches.put(String.valueOf(map.get("id")), map);
        }
        List<Object> merged = new ArrayList<>();
        for (Object item : base) {
            Map<String, Object> baseItem = ConfigurationCodec.object(item, "$base[]");
            String id = String.valueOf(baseItem.get("id"));
            Object patchItem = patches.remove(id);
            merged.add(patchItem instanceof Map<?, ?> patchMap
                    ? deepMerge(baseItem, ConfigurationCodec.object(patchMap, "$patch[" + id + "]"))
                    : baseItem);
        }
        merged.addAll(patches.values());
        return List.copyOf(merged);
    }
}
