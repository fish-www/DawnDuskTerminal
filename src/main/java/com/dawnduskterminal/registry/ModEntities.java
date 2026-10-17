package com.dawnduskterminal.registry;

import com.dawnduskterminal.DawnDuskTerminal;
import com.dawnduskterminal.entity.CarminiteBallEntity;
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

    // 碰撞箱与玩家同尺寸（0.6 x 1.8，眼高 1.62）：模型本体比这大得多（头发 袖子伸出去，
    // 量出来约 3.6 x 3.8），拿模型外廓做碰撞箱会让它庞大得不好打，所以按感觉对齐玩家
    public static final DeferredHolder<EntityType<?>, EntityType<TowerGhastEntity>> TOWER_GHAST =
        ENTITIES.register("tower_ghast", () -> EntityType.Builder
            .of(TowerGhastEntity::new, MobCategory.CREATURE)
            .sized(0.6F, 1.8F)
            .eyeHeight(1.62F)
            .clientTrackingRange(10)
            .updateInterval(3)
            .build("tower_ghast"));

    /** 权杖的弹体。updateInterval 1，跟踪范围小：它是一个很快就消失的飞行物 */
    public static final DeferredHolder<EntityType<?>, EntityType<CarminiteBallEntity>> CARMINITE_BALL =
        ENTITIES.register("carminite_ball", () -> EntityType.Builder
            .<CarminiteBallEntity>of(CarminiteBallEntity::new, MobCategory.MISC)
            .sized(0.5F, 0.5F)
            .clientTrackingRange(4)
            .updateInterval(1)
            .build("carminite_ball"));

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
