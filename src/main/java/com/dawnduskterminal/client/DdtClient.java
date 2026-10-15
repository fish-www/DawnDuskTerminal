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
        // 原版按方块逐个注册草色（BlockColors.createDefault 里的 GRASS_BLOCK、FERN 等），模组方块完全不
        // 上色，tintindex 0 的面会直接渲染成灰白贴图。这里用与原版相同的取色器，包含物品渲染器用的
        // level 为空时的兜底
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
        // 原版 ItemColors 会以 level 为空把方块物品的颜色转回方块取色器（ItemColors.createDefault），
        // 缺了这条注册，模组草方块在背包和手里就是灰的。物品渲染的 level 恒为空，所以这里落到默认草色
        event.register((stack, tintIndex) -> tintIndex == 0
            ? net.minecraft.world.level.GrassColor.getDefaultColor()
            : -1,
            com.dawnduskterminal.registry.ModTerrainBlocks.ROSY_GRASS_ITEM.get());
        // 树叶在背包和手里渲染时 level 为空，所以落到默认树叶色（与上面方块取色器用的 0x48B518 兜底一致）
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
     * 传送门流体的流动参数已经照抄了原版水（见 {@link com.dawnduskterminal.registry.ModFluids}），
     * 这里是同一套照抄的渲染侧
     *
     * <p>原版把 {@code Fluids.WATER} 与 {@code Fluids.FLOWING_WATER} 以 {@link RenderType#translucent()}
     * 放进 {@code ItemBlockRenderTypes.TYPE_BY_FLUID}（在 {@code ItemBlockRenderTypes} 的静态初始化里），
     * {@code SectionCompiler} 再查这张表决定流体表面属于哪个区块渲染层
     *
     * <p>不注册就会落到实体层，而实体层的着色器忽略 alpha 通道：{@code portal_fluid_still.png} 与
     * {@code portal_fluid_flow.png} 的 alpha 统一是 230/255，这正是它们看起来半透明的原因，落到实体层
     * 就会完全不透明
     *
     * <p>{@code setRenderLayer(Fluid, RenderType)} 只在客户端模组加载期（{@code ClientModLoader.isLoading()}）
     * 允许调用，所以这里是客户端 setup 监听，而不是更晚的客户端事件
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
