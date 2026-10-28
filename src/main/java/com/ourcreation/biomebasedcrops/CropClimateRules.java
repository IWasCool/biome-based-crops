package com.ourcreation.biomebasedcrops;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.resources.ResourceLocation;

public final class CropClimateRules {
    private static volatile CompiledRules compiledRules;

    private CropClimateRules() {
    }

    public static CropProfile profileFor(ResourceLocation blockId, ResourceLocation itemId) {
        CompiledRules rules = compiledRules;
        if (rules == null) {
            synchronized (CropClimateRules.class) {
                rules = compiledRules;
                if (rules == null) {
                    rules = Config.SPEC.isLoaded()
                            ? new CompiledRules(Config.CROP_CLIMATE_RULES.get(), Config.DEFAULT_CROP_CLIMATES.get())
                            : new CompiledRules(Config.CROP_CLIMATE_RULES.getDefault(), Config.DEFAULT_CROP_CLIMATES.getDefault());
                    compiledRules = rules;
                }
            }
        }
        return rules.profileFor(blockId, itemId);
    }

    /** Swapping the snapshot also prevents an in-flight lookup from repopulating a stale cache. */
    public static synchronized void clearCache() {
        compiledRules = null;
    }

    private static EnumSet<CropClimate> parseClimates(String raw) {
        EnumSet<CropClimate> climates = EnumSet.noneOf(CropClimate.class);
        for (String part : raw.split(",")) {
            CropClimate.byId(part).ifPresent(climates::add);
        }

        if (climates.isEmpty()) {
            climates.add(CropClimate.NEUTRAL);
            climates.add(CropClimate.WARM);
        }

        return climates;
    }

    private static String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    static final class CompiledRules {
        private final List<Rule> rules;
        private final CropProfile fallback;
        private final Map<PlantIds, CropProfile> profiles = new ConcurrentHashMap<>();

        CompiledRules(List<? extends String> rawRules, String defaultClimates) {
            List<Rule> compiled = new ArrayList<>();
            for (String rawRule : rawRules) {
                String[] parts = rawRule.split("=", 2);
                if (parts.length == 2 && !parts[0].isBlank()) {
                    compiled.add(new Rule(normalize(parts[0]), new CropProfile(parseClimates(parts[1]))));
                }
            }
            // Stable sorting preserves the first configured rule when token lengths tie.
            compiled.sort(Comparator.comparingInt((Rule rule) -> rule.token().length()).reversed());
            rules = List.copyOf(compiled);
            fallback = new CropProfile(parseClimates(defaultClimates));
        }

        CropProfile profileFor(ResourceLocation blockId, ResourceLocation itemId) {
            return profiles.computeIfAbsent(new PlantIds(blockId, itemId), ids -> {
                for (Rule rule : rules) {
                    if (rule.matches(ids.blockId()) || rule.matches(ids.itemId())) {
                        return rule.profile();
                    }
                }
                return fallback;
            });
        }
    }

    private record Rule(String token, CropProfile profile) {
        private boolean matches(ResourceLocation id) {
            // Unqualified crop names must not accidentally match a mod's namespace.
            return (token.indexOf(':') >= 0 ? id.toString() : id.getPath()).contains(token);
        }
    }

    private record PlantIds(ResourceLocation blockId, ResourceLocation itemId) {
    }
}
