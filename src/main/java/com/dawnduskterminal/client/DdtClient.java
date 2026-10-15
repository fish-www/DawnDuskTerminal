package com.dawnduskterminal.client;

import com.dawnduskterminal.DawnDuskTerminal;
import com.dawnduskterminal.config.DdtConfig;
import com.dawnduskterminal.client.model.MountedTowerWoodModel;
import com.dawnduskterminal.registry.ModDimensions;
import com.dawnduskterminal.registry.ModFluids;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.common.NeoForge;

public final class DdtClient {
    private DdtClient() {}

    public static void init(IEventBus modBus) {
        modBus.addListener(DdtClient::onClientSetup);
        modBus.addListener(DdtClient::registerDimensionEffects);
        modBus.addListener(DdtClient::registerFluidExtensions);
        modBus.addListener(DdtClient::registerBlockColors);
        modBus.addListener(DdtClient::registerItemColors);
        modBus.addListener(DdtClient::onModifyBakingResult);
        NeoForge.EVENT_BUS.addListener(DdtClient::onRenderLevelStage);
        NeoForge.EVENT_BUS.addListener(ShaderGuard::onClientLogin);
    }

    private static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
        wrapCtm(event, "mounted_towerwood");
        wrapCtm(event, "encased_towerbeam");
    }

    private static void wrapCtm(ModelEvent.ModifyBakingResult event, String name) {
        ModelResourceLocation blockLoc = new ModelResourceLocation(DawnDuskTerminal.id(name), "");
        BakedModel original = event.getModels().get(blockLoc);
        if (original != null) {
            event.getModels().put(blockLoc, new MountedTowerWoodModel(original));
        }
    }

    private static void registerBlockColors(net.neoforged.neoforge.client.event.RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tintIndex) -> {
            if (tintIndex != 0) {
                return -1;
            }
            if (level != null && pos != null) {
                return net.minecraft.client.renderer.BiomeColors.getAverageFoliageColor(level, pos);
            }
            return 0x48B518;
        }, com.dawnduskterminal.registry.ModTerrainBlocks.DAWN_LEAVES.get(),
            com.dawnduskterminal.registry.ModTerrainBlocks.DUSK_LEAVES.get(),
            com.dawnduskterminal.registry.ModTerrainBlocks.CHRONO_LEAVES.get(),
            com.dawnduskterminal.registry.ModTerrainBlocks.SKYGATE_PINE_LEAVES.get());
        event.register((state, level, pos, tintIndex) -> tintIndex == 0 ? 0x9C7A50 : -1,
            com.dawnduskterminal.registry.ModTerrainBlocks.WITHERED_DAWN_LEAVES.get(),
            com.dawnduskterminal.registry.ModTerrainBlocks.WITHERED_DUSK_LEAVES.get(),
            com.dawnduskterminal.registry.ModTerrainBlocks.WITHERED_CHRONO_LEAVES.get());
        // Vanilla registers grass tinting per block (BlockColors.createDefault -> Blocks.GRASS_BLOCK, FERN, ...); a
        // modded block gets no tint at all, so its tintindex 0 faces render as the raw grey texture. Same provider
        // as vanilla, including the null-level fallback used by the item renderer.
        event.register((state, level, pos, tintIndex) -> {
            if (tintIndex != 0) {
                return -1;
            }
            return level != null && pos != null
                ? net.minecraft.client.renderer.BiomeColors.getAverageGrassColor(level, pos)
                : net.minecraft.world.level.GrassColor.getDefaultColor();
        }, com.dawnduskterminal.registry.ModTerrainBlocks.ROSY_GRASS.get());
    }

    private static void registerItemColors(net.neoforged.neoforge.client.event.RegisterColorHandlersEvent.Item event) {
        // Vanilla ItemColors delegates block-item tints back to the block colours with a null level
        // (ItemColors.createDefault), which is the registration that makes a modded grass block grey in the
        // inventory / in hand. Item rendering always has a null level, so this resolves to the default grass colour.
        event.register((stack, tintIndex) -> tintIndex == 0
            ? net.minecraft.world.level.GrassColor.getDefaultColor()
            : -1,
            com.dawnduskterminal.registry.ModTerrainBlocks.ROSY_GRASS_ITEM.get());
        // Leaves render with a null level in the inventory / in hand, so this resolves to the default foliage colour
        // (the same 0x48B518 fallback the block colour provider uses at line 39 above).
        event.register((stack, tintIndex) -> tintIndex == 0 ? 0x48B518 : -1,
            com.dawnduskterminal.registry.ModTerrainBlocks.DAWN_LEAVES_ITEM.get(),
            com.dawnduskterminal.registry.ModTerrainBlocks.DUSK_LEAVES_ITEM.get(),
            com.dawnduskterminal.registry.ModTerrainBlocks.CHRONO_LEAVES_ITEM.get(),
            com.dawnduskterminal.registry.ModTerrainBlocks.SKYGATE_PINE_LEAVES_ITEM.get());
    }

    private static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) {
            return;
        }
        if (!DdtConfig.customSky()) {
            return;
        }
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || !level.dimension().equals(ModDimensions.CHRONO)) {
            return;
        }
        if (!ShaderGuard.shaderModLoaded()) {
            return;
        }
        ChronoSkyRenderer.render(level, event.getPartialTick().getGameTimeDeltaPartialTick(false),
            event.getModelViewMatrix(), event.getCamera(), event.getProjectionMatrix(), null);
    }

    /**
     * The portal fluid's own flow properties already mirror vanilla water (see
     * {@link com.dawnduskterminal.registry.ModFluids}), so this is the render half of the same mirroring:
     * vanilla puts {@code Fluids.WATER} and {@code Fluids.FLOWING_WATER} into
     * {@code ItemBlockRenderTypes.TYPE_BY_FLUID} as {@link RenderType#translucent()}
     * ({@code ItemBlockRenderTypes} static init), and {@code SectionCompiler} asks that map which chunk layer a
     * fluid surface belongs to.
     *
     * <p>Without it the fluid lands in the solid layer, and the solid layer's shader ignores the alpha channel:
     * {@code portal_fluid_still.png} / {@code portal_fluid_flow.png} are a uniform alpha of 230/255, which is
     * exactly what makes them look semi transparent, so they would render fully opaque.
     *
     * <p>{@code setRenderLayer(Fluid, RenderType)} refuses to run outside client mod loading
     * ({@code ClientModLoader.isLoading()}), which is why this is a client setup listener rather than a
     * client-side event somewhere later.
     */
    private static void onClientSetup(FMLClientSetupEvent event) {
        ItemBlockRenderTypes.setRenderLayer(ModFluids.PORTAL_FLUID.get(), RenderType.translucent());
        ItemBlockRenderTypes.setRenderLayer(ModFluids.PORTAL_FLOWING.get(), RenderType.translucent());
    }

    private static void registerDimensionEffects(RegisterDimensionSpecialEffectsEvent event) {
        event.register(DawnDuskTerminal.id("chrono"), new ChronoDimensionEffects());
    }

    private static void registerFluidExtensions(RegisterClientExtensionsEvent event) {
        event.registerFluidType(PortalFluidExtensions.INSTANCE, ModFluids.PORTAL_FLUID_TYPE.get());
        event.registerFluidType(ScorchingTwilightFluidExtensions.INSTANCE,
            com.dawnduskterminal.registry.ModScorchingTwilight.TYPE.get());
    }
}
