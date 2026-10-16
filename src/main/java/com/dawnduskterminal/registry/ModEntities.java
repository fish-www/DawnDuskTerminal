package com.dawnduskterminal.registry;

import com.dawnduskterminal.DawnDuskTerminal;
import com.dawnduskterminal.entity.TowerGhastEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
        DeferredRegister.create(Registries.ENTITY_TYPE, DawnDuskTerminal.MOD_ID);

    // 碰撞箱按发布的几何体量出来，不是估的：712 个立方体纵向跨 88.0 单位、横向 62.3 单位，
    // GeckoLib 按每方块 16 单位换算 Bedrock 单位 渲染器另外还套了源 ysm.json 里声明的 0.7 缩放，
    // 所以屏幕上的怪物约 3.85 方块高，碰撞箱对齐的是这个数，而不是未缩放的 5.5
    public static final DeferredHolder<EntityType<?>, EntityType<TowerGhastEntity>> TOWER_GHAST =
        ENTITIES.register("tower_ghast", () -> EntityType.Builder
            .of(TowerGhastEntity::new, MobCategory.CREATURE)
            .sized(1.6F, 3.5F)
            .eyeHeight(2.9F)
            .clientTrackingRange(10)
            .updateInterval(3)
            .build("tower_ghast"));

    private ModEntities() {}

    public static void register(IEventBus bus) {
        ENTITIES.register(bus);
        // 属性也注册在 mod 总线上，但走的是专门的事件而不是注册表：
        // EntityAttributeCreationEvent 触发时 EntityType 已经挂好，正好是它需要的时机
        bus.addListener(ModEntities::onEntityAttributeCreation);
    }

    private static void onEntityAttributeCreation(EntityAttributeCreationEvent event) {
        event.put(TOWER_GHAST.get(), TowerGhastEntity.createAttributes().build());
    }
}
