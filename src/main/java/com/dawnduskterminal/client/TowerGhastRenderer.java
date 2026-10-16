package com.dawnduskterminal.client;

import com.dawnduskterminal.DawnDuskTerminal;
import com.dawnduskterminal.entity.TowerGhastEntity;
import com.dawnduskterminal.registry.ModEntities;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import software.bernie.geckolib.animation.AnimationState;
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
        event.registerEntityRenderer(ModEntities.TOWER_GHAST.get(), TowerGhastEntityRenderer::new);
    }

    /**
     * 多出来的唯一一件事是把 GeckoLib 自带的倒地关掉
     *
     * <p>{@code GeoEntityRenderer.applyRotations} 在 {@code deathTime > 0} 时会把整个姿势绕 Z 转到
     * {@link #getDeathMaxRotation} 度，默认 90。这套模型包自带 {@code death} 片段（1 秒，停在最后一帧），
     * 两者同时生效就是「先被扳倒、再按自己的演出躺下」，看着像抽搐
     */
    private static final class TowerGhastEntityRenderer extends GeoEntityRenderer<TowerGhastEntity> {
        private TowerGhastEntityRenderer(EntityRendererProvider.Context context) {
            super(context, new TowerGhastModel());
            withScale(MODEL_SCALE);
        }

        @Override
        protected float getDeathMaxRotation(TowerGhastEntity animatable) {
            return 0.0F;
        }
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
        /**
         * 有状态的那两层（parallel0 与 pre_parallel0）在这里写回
         *
         * <p>必须挂在这里而不是渲染层：{@code setCustomAnimations} 是 {@code handleAnimations} 的最后
         * 一步，夹在「动画写入骨骼」与「骨骼矩阵被读走」之间，见 {@link TowerGhastBonePose} 的类注释
         */
        @Override
        public void setCustomAnimations(TowerGhastEntity entity, long instanceId,
                                        AnimationState<TowerGhastEntity> animationState) {
            this.bonePose.apply(entity, this, animationState.getPartialTick());
        }

        private final TowerGhastBonePose bonePose = new TowerGhastBonePose();

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
