package com.dawnduskterminal.structure;

import com.dawnduskterminal.DawnDuskTerminal;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Single source of truth for which structures the chrono dimension may place.
 *
 * <p>{@code StructureExclusionMixin} suppresses every structure whose id is <em>not</em> in this
 * allow-list, so this one table is the whole exclusion policy: an entry here is allowed, anything
 * else is excluded. The previous {@code EXCLUDED_TF} / {@code EXCLUDED_VANILLA} blacklists and the
 * {@code WHITELIST} were three overlapping structures with contradictory semantics, now collapsed
 * into this single allow-list. The names are only used by the startup validation log.
 */
public final class StructureExclusions {
    private static final Map<String, ResourceLocation> ALLOWED = new LinkedHashMap<>();

    static {
        ALLOWED.put("废弃传送门", ResourceLocation.withDefaultNamespace("ruined_portal"));
        ALLOWED.put("废弃传送门 沙漠", ResourceLocation.withDefaultNamespace("ruined_portal_desert"));
        ALLOWED.put("废弃传送门 丛林", ResourceLocation.withDefaultNamespace("ruined_portal_jungle"));
        ALLOWED.put("废弃传送门 山地", ResourceLocation.withDefaultNamespace("ruined_portal_mountain"));
        ALLOWED.put("废弃传送门 海洋", ResourceLocation.withDefaultNamespace("ruined_portal_ocean"));
        ALLOWED.put("废弃传送门 沼泽", ResourceLocation.withDefaultNamespace("ruined_portal_swamp"));
        ALLOWED.put("沉船", ResourceLocation.withDefaultNamespace("shipwreck"));
        ALLOWED.put("搁浅沉船", ResourceLocation.withDefaultNamespace("shipwreck_beached"));
        ALLOWED.put("掠夺者前哨站", ResourceLocation.withDefaultNamespace("pillager_outpost"));
        ALLOWED.put("沙漠神殿", ResourceLocation.withDefaultNamespace("desert_pyramid"));
        ALLOWED.put("雪屋", ResourceLocation.withDefaultNamespace("igloo"));
        ALLOWED.put("废弃矿井", ResourceLocation.withDefaultNamespace("mineshaft"));
        ALLOWED.put("废弃矿井 平顶山", ResourceLocation.withDefaultNamespace("mineshaft_mesa"));
        ALLOWED.put("树洞", ResourceLocation.parse("twilightforest:hollow_tree"));
        ALLOWED.put("倒下的空心木头", ResourceLocation.parse("twilightforest:fallen_trunk"));
        ALLOWED.put("蘑菇城堡", ResourceLocation.parse("twilightforest:mushroom_tower"));
        ALLOWED.put("营地", ResourceLocation.parse("twilightforest:camp"));
        ALLOWED.put("树篱迷宫", ResourceLocation.parse("twilightforest:hedge_maze"));
        ALLOWED.put("空心矿山", ResourceLocation.parse("twilightforest:small_hollow_hill"));
    }

    private static final Set<ResourceLocation> ALLOWED_IDS = Set.copyOf(ALLOWED.values());

    private static final Set<ResourceLocation> LOGGED = ConcurrentHashMap.newKeySet();

    private StructureExclusions() {}

    public static void validate(Registry<Structure> structures) {
        int missing = 0;
        for (Map.Entry<String, ResourceLocation> entry : ALLOWED.entrySet()) {
            if (!structures.containsKey(entry.getValue())) {
                DawnDuskTerminal.LOGGER.warn("DawnDuskTerminal allowed structure {} {} is not registered",
                    entry.getKey(), entry.getValue());
                missing++;
            }
        }
        DawnDuskTerminal.LOGGER.info("DawnDuskTerminal structure allow-list has {} entries, {} unresolvable",
            ALLOWED.size(), missing);
    }

    public static boolean isAllowed(ResourceLocation id) {
        return ALLOWED_IDS.contains(id);
    }

    public static boolean isExcluded(ResourceLocation id) {
        return !ALLOWED_IDS.contains(id);
    }

    public static void logSuppressed(ResourceLocation id) {
        if (LOGGED.add(id)) {
            DawnDuskTerminal.LOGGER.info("DawnDuskTerminal structure {} is suppressed in the chrono dimension", id);
        }
    }
}
