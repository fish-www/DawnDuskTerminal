package com.dawnduskterminal.registry;

import com.dawnduskterminal.DawnDuskTerminal;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.common.SoundActions;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * The portal fluid reuses {@code minecraft:water} for both its flow behaviour and (client side) its render
 * behaviour:
 *
 * <ul>
 *   <li><b>flow</b> - {@link #properties()} carries water's numbers verbatim
 *       ({@code tickRate 5}, {@code slopeFindDistance 4}, {@code levelDecreasePerBlock 1},
 *       {@code explosionResistance 100.0F}, see {@code WaterFluid} / {@code Fluids.waterProperties}), and
 *       neither fluid overrides {@code tick} any more. {@code FlowingFluid.tick} - the method that actually
 *       spreads a fluid - is therefore the inherited one, so the fluid spreads, falls and re-levels exactly
 *       like water. That matters because this fluid is the chrono dimension's {@code default_fluid}
 *       (oceans, aquifers, springs and lakes are all generated as portal fluid).</li>
 *   <li><b>rendering</b> - the still and flowing textures carry a real alpha channel
 *       ({@code portal_fluid_still.png} / {@code portal_fluid_flow.png} are a uniform alpha of 230/255),
 *       so the fluid has to be drawn in the translucent chunk layer. That registration lives in
 *       {@link com.dawnduskterminal.client.DdtClient} and mirrors vanilla's
 *       {@code ItemBlockRenderTypes} entries for {@code Fluids.WATER} / {@code Fluids.FLOWING_WATER}.</li>
 * </ul>
 */
public final class ModFluids {
    public static final DeferredRegister<FluidType> FLUID_TYPES =
        DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, DawnDuskTerminal.MOD_ID);
    public static final DeferredRegister<Fluid> FLUIDS =
        DeferredRegister.create(Registries.FLUID, DawnDuskTerminal.MOD_ID);

    public static final DeferredHolder<FluidType, FluidType> PORTAL_FLUID_TYPE = FLUID_TYPES.register(
        "portal_fluid",
        () -> new FluidType(FluidType.Properties.create()
            .descriptionId("fluid_type.dawnduskterminal.portal_fluid")
            .density(1600)
            .viscosity(1200)
            .lightLevel(15)
            .canSwim(true)
            .canDrown(false)
            .canPushEntity(true)
            .canConvertToSource(false)
            .canHydrate(false)
            .motionScale(0.014D)
            .supportsBoating(false)
            .rarity(Rarity.UNCOMMON)
            .sound(SoundActions.BUCKET_FILL, net.minecraft.sounds.SoundEvents.BUCKET_FILL)
            .sound(SoundActions.BUCKET_EMPTY, net.minecraft.sounds.SoundEvents.BUCKET_EMPTY)));

    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> PORTAL_FLUID =
        FLUIDS.register("portal_fluid", () -> new BaseFlowingFluid.Source(properties()));

    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> PORTAL_FLOWING =
        FLUIDS.register("flowing_portal_fluid", () -> new BaseFlowingFluid.Flowing(properties()));

    private ModFluids() {}

    /**
     * Vanilla water's flow properties, copied value for value:
     *
     * <pre>
     * FlowingFluid.Properties waterProperties = new FlowingFluid.Properties(FluidTypes.WATER, FLOWING_WATER, WATER)
     *     .block(Blocks.WATER)
     *     .bucket(Items.WATER_BUCKET)
     *     .tickRate(5)
     *     .slopeFindDistance(4)
     *     .levelDecreasePerBlock(1)
     *     .explosionResistance(100.0F);
     * </pre>
     *
     * <p>Not carried over from vanilla water on purpose:
     * <ul>
     *   <li>{@code canConvertToSource} - {@code WaterFluid} follows the water-source game rule, but the portal
     *       fluid's {@link FluidType} keeps {@code canConvertToSource(false)}, so two adjacent sources never
     *       spawn a third one (no infinite portal fluid).</li>
     *   <li>{@code canBeReplacedWith} - {@code BaseFlowingFluid}'s implementation compares the fluid to itself
     *       instead of testing {@code #minecraft:water}, so foreign water falling on top is not swallowed.</li>
     * </ul>
     */
    public static BaseFlowingFluid.Properties properties() {
        return new BaseFlowingFluid.Properties(PORTAL_FLUID_TYPE, PORTAL_FLUID, PORTAL_FLOWING)
            .block(ModBlocks.PORTAL_FLUID)
            .bucket(ModItems.PORTAL_BUCKET)
            .slopeFindDistance(4)
            .levelDecreasePerBlock(1)
            .tickRate(5)
            .explosionResistance(100.0F);
    }

    public static void register(IEventBus bus) {
        FLUID_TYPES.register(bus);
        FLUIDS.register(bus);
    }
}
