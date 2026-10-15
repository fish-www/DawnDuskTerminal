package com.dawnduskterminal.registry;

import com.dawnduskterminal.DawnDuskTerminal;
import com.dawnduskterminal.world.ChronoFreezeFeature;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModFeatures {
    public static final DeferredRegister<Feature<?>> FEATURES =
        DeferredRegister.create(Registries.FEATURE, DawnDuskTerminal.MOD_ID);

    /** Takes over {@code minecraft:freeze_top_layer}, which places vanilla snow and ice from code. */
    public static final DeferredHolder<Feature<?>, ChronoFreezeFeature> FREEZE_TOP_LAYER =
        FEATURES.register("freeze_top_layer", ChronoFreezeFeature::new);

    private ModFeatures() {}

    public static void register(IEventBus bus) {
        FEATURES.register(bus);
    }
}
