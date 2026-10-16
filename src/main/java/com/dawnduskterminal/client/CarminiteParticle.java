package com.dawnduskterminal.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/**
 * 砷铅铁三种粒子共用的实现
 *
 * <p>它们只差速度 阻力 重力 寿命 尺寸，所以共用一支而不是照抄三支。帧序由数据包里的
 * {@code particles/particle_carminite_*.json} 决定，这里只负责按寿命取帧
 *
 * <p>渲染层用 {@link ParticleRenderType#PARTICLE_SHEET_TRANSLUCENT}：贴图带 alpha，
 * 落到不透明层会把边缘画成黑框
 */
public final class CarminiteParticle extends TextureSheetParticle {
    private final SpriteSet sprites;

    private CarminiteParticle(ClientLevel level, double x, double y, double z,
                              double vx, double vy, double vz, SpriteSet sprites,
                              float scale, int lifetime, float friction, float gravity, boolean spin) {
        super(level, x, y, z, vx, vy, vz);
        this.sprites = sprites;
        this.lifetime = lifetime;
        this.friction = friction;
        this.gravity = gravity;
        this.quadSize = scale * (0.8F + this.random.nextFloat() * 0.4F);
        this.roll = spin ? this.random.nextFloat() * Mth.TWO_PI : 0.0F;
        this.setSprite(this.sprites.get(0, this.lifetime));
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
    }

    /** 飞行中的弹体，慢速下坠 */
    public static ParticleProvider<SimpleParticleType> ballProvider(SpriteSet sprites) {
        return (type, level, x, y, z, vx, vy, vz) ->
            new CarminiteParticle(level, x, y, z, vx, vy, vz, sprites, 0.35F, 8, 0.92F, 0.0F, false);
    }

    /** 拖尾，几乎不动，靠重力落下一点 */
    public static ParticleProvider<SimpleParticleType> traceProvider(SpriteSet sprites) {
        return (type, level, x, y, z, vx, vy, vz) ->
            new CarminiteParticle(level, x, y, z, vx * 0.1, vy * 0.1, vz * 0.1, sprites, 0.2F, 6, 0.85F, 0.02F, false);
    }

    /** 反应场，贴在原地铺开，每片的初始角度随机 */
    public static ParticleProvider<SimpleParticleType> effectProvider(SpriteSet sprites) {
        return (type, level, x, y, z, vx, vy, vz) ->
            new CarminiteParticle(level, x, y, z, vx * 0.3, vy * 0.3, vz * 0.3, sprites, 0.6F, 14, 0.9F, 0.0F, true);
    }

    @Override
    public void tick() {
        super.tick();
        // 按寿命在贴图表上走帧，帧数就是 json 里 textures 的个数
        this.setSprite(this.sprites.get(this.age, this.lifetime));
    }

    @Override
    public float getQuadSize(float scaleFactor) {
        // 出场放大，退场收缩
        float t = ((float) this.age + scaleFactor) / (float) this.lifetime;
        return this.quadSize * Mth.clamp(t * 4.0F, 0.0F, 1.0F) * Mth.clamp((1.0F - t) * 4.0F, 0.0F, 1.0F);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }
}
