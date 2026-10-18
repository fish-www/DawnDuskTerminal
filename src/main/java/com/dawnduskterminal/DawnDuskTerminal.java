package com.dawnduskterminal;

import com.dawnduskterminal.client.DdtClient;
import com.dawnduskterminal.config.DdtConfig;
import com.dawnduskterminal.network.ModNetwork;
import com.dawnduskterminal.registry.ModAttachments;
import com.dawnduskterminal.registry.ModArmorMaterials;
import com.dawnduskterminal.registry.ModBiomeSources;
import com.dawnduskterminal.registry.ModBlocks;
import com.dawnduskterminal.registry.ModChronoOres;
import com.dawnduskterminal.registry.ModCreativeTabs;
import com.dawnduskterminal.registry.ModEffects;
import com.dawnduskterminal.registry.ModEntities;
import com.dawnduskterminal.registry.ModEquipment;
import com.dawnduskterminal.registry.ModFeatures;
import com.dawnduskterminal.registry.ModFluids;
import com.dawnduskterminal.advancement.ModCriteria;
import com.dawnduskterminal.command.DdtCommands;
import com.dawnduskterminal.registry.ModIngredients;
import com.dawnduskterminal.registry.ModItems;
import com.dawnduskterminal.registry.ModParticles;
import com.dawnduskterminal.registry.ModScorchingTwilight;
import com.dawnduskterminal.registry.ModSounds;
import com.dawnduskterminal.registry.ModTerrainBlocks;
import com.dawnduskterminal.registry.ModTrunkPlacers;
import com.dawnduskterminal.server.DdtServerEvents;
import com.dawnduskterminal.world.FluidReactions;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(DawnDuskTerminal.MOD_ID)
public final class DawnDuskTerminal {
    public static final String MOD_ID = "dawnduskterminal";
    public static final Logger LOGGER = LoggerFactory.getLogger("DawnDuskTerminal");

    public DawnDuskTerminal(IEventBus modBus, ModContainer container) {
        ModFluids.register(modBus);
        ModScorchingTwilight.register(modBus);
        ModBlocks.register(modBus);
        ModTerrainBlocks.register(modBus);
        ModChronoOres.register(modBus);
        ModItems.register(modBus);
        ModArmorMaterials.register(modBus);
        ModEquipment.register(modBus);
        ModParticles.register(modBus);
        ModIngredients.register(modBus);
        ModEffects.register(modBus);
        ModEntities.register(modBus);
        ModAttachments.register(modBus);
        ModSounds.register(modBus);
        ModCriteria.register(modBus);
        ModBiomeSources.register(modBus);
        ModFeatures.register(modBus);
        ModTrunkPlacers.register(modBus);
        ModCreativeTabs.register(modBus);

        modBus.addListener(ModNetwork::registerPayloads);
        // 两件都要挂：Loading 是启动时配置刚生效那一刻，Reloading 是玩家改完配置重载
        modBus.addListener((ModConfigEvent.Loading event) -> DdtConfig.refreshCache());
        modBus.addListener((ModConfigEvent.Reloading event) -> DdtConfig.refreshCache());
        modBus.addListener((FMLCommonSetupEvent event) -> FluidReactions.register());

        NeoForge.EVENT_BUS.addListener(DdtServerEvents::onAnvilUpdate);
        NeoForge.EVENT_BUS.addListener(DdtServerEvents::onServerStarted);
        NeoForge.EVENT_BUS.addListener(DdtServerEvents::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(DdtServerEvents::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(DdtServerEvents::onLivingDeath);
        NeoForge.EVENT_BUS.addListener(DdtServerEvents::onSpawnPlacement);
        NeoForge.EVENT_BUS.addListener(DdtServerEvents::onRightClickItem);
        NeoForge.EVENT_BUS.addListener(DdtCommands::register);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            DdtClient.init(modBus);
        }

        container.registerConfig(ModConfig.Type.COMMON, DdtConfig.SPEC);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
