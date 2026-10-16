package com.dawnduskterminal.client;

import com.dawnduskterminal.DawnDuskTerminal;
import com.dawnduskterminal.entity.TowerGhastEntity;
import com.dawnduskterminal.registry.ModEntities;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * 高塔恶魂的客户端渲染接线
 *
 * <p>三条资源路径在这里写死，没有沿用
 * {@link software.bernie.geckolib.model.DefaultedEntityGeoModel} 的命名约定 那个约定把每条路径都在
 * 实体 id 之上再拼一层 {@code subtype()}，而该类的 {@code subtype()} 返回字面量 {@code "entity"}，
 * 于是它实际会去找 {@code geo/entity/tower_ghast.geo.json}
 * {@code animations/entity/tower_ghast.animation.json} {@code textures/entity/tower_ghast.png}
 * 直接把路径写出来，代码里的路径就永远是 jar 里的那个文件，
 * 少掉一整类「模型能加载但不动」的排查
 *
 * <p>在 {@link DdtClient#init} 里显式注册，不用 {@code @EventBusSubscriber}，
 * 与本模组其它客户端监听器的接法一致 该监听器跑在 mod 事件总线上
 */
public final class TowerGhastRenderer {
    /** 源模型要求的缩放，加在渲染器上，而不是烘进几处共享的几何体里 */
    private static final float MODEL_SCALE = 0.7F;

    private TowerGhastRenderer() {}

    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.TOWER_GHAST.get(),
            context -> new GeoEntityRenderer<>(context, new TowerGhastModel())
                .withScale(MODEL_SCALE));
    }

    /**
     * 只用本模组发布的那几条路径的 Geo 模型，不依赖 GeckoLib 的命名约定
     *
     * <p>GeckoLib 把单参数的 {@code getModelResource(T)} / {@code getTextureResource(T)} 标为过时，
     * 推荐改用同时接收渲染器的双参数版本 但双参数版本的默认实现本身就是转调单参数版本，
     * 而单参数版本仍是抽象方法，绕不过去 所以这里只实现三个单参数方法，
     * 三条路径各自只有一处定义，双参数调用最终也会走到它们
     */
    private static final class TowerGhastModel extends GeoModel<TowerGhastEntity> {
        private static final ResourceLocation MODEL =
            DawnDuskTerminal.id("geo/tower_ghast.geo.json");
        private static final ResourceLocation TEXTURE =
            DawnDuskTerminal.id("textures/entity/tower_ghast.png");
        private static final ResourceLocation ANIMATIONS =
            DawnDuskTerminal.id("animations/tower_ghast.animation.json");

        @SuppressWarnings("deprecation")
        @Override
        public ResourceLocation getModelResource(TowerGhastEntity animatable) {
            return MODEL;
        }

        @SuppressWarnings("deprecation")
        @Override
        public ResourceLocation getTextureResource(TowerGhastEntity animatable) {
            return TEXTURE;
        }

        @Override
        public ResourceLocation getAnimationResource(TowerGhastEntity animatable) {
            return ANIMATIONS;
        }
    }
}
