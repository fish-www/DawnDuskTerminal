package com.dawnduskterminal.mixin;

import com.dawnduskterminal.DawnDuskTerminal;
import com.dawnduskterminal.registry.ModDimensions;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.vehicle.MinecartChest;
import net.minecraft.world.level.levelgen.structure.structures.MineshaftPieces;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * The chrono dimension re-enables the vanilla mineshaft (see {@code StructureExclusions}), but the
 * chest minecarts it spawns hard-code {@code minecraft:chests/abandoned_mineshaft}. In the chrono
 * dimension we swap that table for a dedicated one whose contents mirror the Twilight Forest
 * Hollow Hill chests ({@code twilightforest:hill_1/2/3}).
 */
@Mixin(MineshaftPieces.MineShaftCorridor.class)
public abstract class MineshaftChestLootMixin {
    private static final ResourceKey<LootTable> CHRONO_MINESHAFT = ResourceKey.create(
        Registries.LOOT_TABLE, DawnDuskTerminal.id("chests/chrono_mineshaft"));

    @Redirect(
        method = "createChest(Lnet/minecraft/world/level/WorldGenLevel;Lnet/minecraft/world/level/levelgen/structure/BoundingBox;Lnet/minecraft/util/RandomSource;IIILnet/minecraft/resources/ResourceKey;)Z",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/vehicle/MinecartChest;setLootTable(Lnet/minecraft/resources/ResourceKey;J)V"
        )
    )
    private void ddt$redirectMineshaftLoot(MinecartChest minecart, ResourceKey<LootTable> key, long seed) {
        if (ModDimensions.CHRONO.equals(minecart.level().dimension())) {
            key = CHRONO_MINESHAFT;
        }
        minecart.setLootTable(key, seed);
    }
}
