package com.dawnduskterminal.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Applies the Twilight Forest foliage (leaf) colour to the chrono dimension's ported biomes.
 *
 * <p>TF colours leaves with an ASM transformer that patches {@code BiomeColors.FOLIAGE_COLOR_RESOLVER}
 * and then looks the biome up in a map keyed by TF's own biome ids. The chrono copies live under
 * {@code dawnduskterminal:*}, so that lookup misses and the leaves fall back to the plain foliage
 * colour. This class re-implements the two TF foliage algorithms and is wired in by
 * {@link com.dawnduskterminal.mixin.BiomeColorsFoliageMixin}.
 */
public final class ChronoFoliageColors {
    private static final ResourceKey<Biome> JUNGLE = biomeKey("jungle");
    private static final ResourceKey<Biome> DARK_FOREST = biomeKey("dark_forest");

    private static final Map<Biome, ResourceKey<Biome>> KEY_CACHE = new ConcurrentHashMap<>();

    private ChronoFoliageColors() {}

    public static int resolve(int original, Biome biome, double x, double z) {
        ResourceKey<Biome> key = key(biome);
        if (JUNGLE.equals(key)) {
            return enchanted(original, (int) x, (int) z);
        }
        if (DARK_FOREST.equals(key)) {
            // TF's darkForestCenterFoliage always returns 0xE94E14: its branch condition
            // `(noise + 1) / 2 < -0.1` can never be true, so the noise never selects the other colour.
            return 0xE94E14;
        }
        return original;
    }

    private static ResourceKey<Biome> biomeKey(String name) {
        return ResourceKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath("dawnduskterminal", name));
    }

    private static ResourceKey<Biome> key(Biome biome) {
        ResourceKey<Biome> cached = KEY_CACHE.get(biome);
        if (cached != null) {
            return cached;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return null;
        }
        ResourceKey<Biome> key = minecraft.level.registryAccess()
            .registryOrThrow(Registries.BIOME)
            .getResourceKey(biome)
            .orElse(null);
        if (key != null) {
            KEY_CACHE.put(biome, key);
        }
        return key;
    }

    /** TF's {@code BiomeColorAlgorithms.enchanted}: a position-based blue gradient across the biome. */
    private static int enchanted(int original, int x, int z) {
        BlockPos center = nearestCenterXZ(x >> 4, z >> 4);
        int cx = center.getX();
        int cz = center.getZ();
        int dist = (int) Mth.sqrt((cx - x) * (cx - x) + (cz - z) * (cz - z));
        int color = dist * 16;
        color %= 512;
        if (color > 255) {
            color = 511 - color;
        }
        color = 255 - color;
        return (original & 0xFFFF00) + color;
    }

    /** TF's {@code LegacyLandmarkPlacements.getNearestCenterXZ}: a deterministic, seed-free grid centre. */
    private static BlockPos nearestCenterXZ(int chunkX, int chunkZ) {
        int regionX = (chunkX + 8) >> 4;
        int regionZ = (chunkZ + 8) >> 4;
        long seed = regionX * 3129871L ^ regionZ * 116129781L;
        seed = seed * seed * 42317861L + seed * 7L;
        int num0 = (int) (seed >> 12 & 3L);
        int num1 = (int) (seed >> 15 & 3L);
        int num2 = (int) (seed >> 18 & 3L);
        int num3 = (int) (seed >> 21 & 3L);
        int centerX = 8 + num0 - num1;
        int centerZ = 8 + num2 - num3;
        int ccz = regionZ >= 0
            ? (regionZ * 16 + centerZ - 8) * 16 + 8
            : (regionZ * 16 + (16 - centerZ) - 8) * 16 + 9;
        int ccx = regionX >= 0
            ? (regionX * 16 + centerX - 8) * 16 + 8
            : (regionX * 16 + (16 - centerX) - 8) * 16 + 9;
        return new BlockPos(ccx, 0, ccz);
    }
}
