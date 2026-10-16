package com.dawnduskterminal.entity;

import com.dawnduskterminal.registry.ModDamageTypes;
import com.dawnduskterminal.registry.ModEntities;
import com.dawnduskterminal.registry.ModItems;
import com.dawnduskterminal.registry.ModParticles;
import com.dawnduskterminal.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;

import java.util.List;

/**
 * 砷铅铁反应权杖发射的弹体
 *
 * <p>用 {@link AbstractArrow} 而不是自写一个弹射实体：重力 空气阻力 命中判定 穿透 归因都在父类里，
 * 这里只需要决定「命中之后做什么」
 *
 * <p>源 mod（MCreator 生成的 twilightextra）把反应场做成了一只没有 AI 的飞行生物，命中时生成它，
 * 它再每帧扫一遍周围实体然后自杀。这里不这么做：扫一遍范围就在命中那一帧完成，少一个实体类型、
 * 一套属性、一条渲染器和一整套生成/同步开销，行为一样
 */
public class CarminiteBallEntity extends AbstractArrow implements ItemSupplier {
    /** 反应场半径，单位方块 */
    private static final float FIELD_RADIUS = 3.0F;
    /** 反应场伤害，命中本体另算一次 */
    private static final float FIELD_DAMAGE = 6.0F;
    /** 弹体本身命中的伤害 */
    private static final float IMPACT_DAMAGE = 6.0F;

    /** 命中只能在服务端结算一次，父类可能同时报一次实体命中与一次方块命中 */
    private boolean detonated;

    public CarminiteBallEntity(EntityType<? extends CarminiteBallEntity> type, Level level) {
        super(type, level);
        this.pickup = Pickup.DISALLOWED;
    }

    public CarminiteBallEntity(Level level, LivingEntity owner, ItemStack firedFromWeapon) {
        super(ModEntities.CARMINITE_BALL.get(), owner, level, ItemStack.EMPTY, firedFromWeapon);
        this.pickup = Pickup.DISALLOWED;
        this.setSoundEvent(ModSounds.CARMINITE_FIELD.get());
    }

    /** 渲染器（{@code ThrownItemRenderer}）要从这里拿外观 */
    @Override
    public ItemStack getItem() {
        return new ItemStack(ModItems.CARMINITE_BALL.get());
    }

    @Override
    protected ItemStack getDefaultPickupItem() {
        return new ItemStack(ModItems.CARMINITE_BALL.get());
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            // 一条拖尾加一个本体。飞行粒子只画在客户端，服务端不必为此发包
            this.level().addParticle(ModParticles.CARMINITE_TRACE.get(), this.getX(), this.getY(), this.getZ(), 0.0D, 0.0D, 0.0D);
            this.level().addParticle(ModParticles.CARMINITE_BALL.get(), this.getX(), this.getY(), this.getZ(), 0.0D, 0.0D, 0.0D);
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        Entity hit = result.getEntity();
        if (!this.level().isClientSide) {
            hit.hurt(this.fieldDamageSource(), IMPACT_DAMAGE);
        }
        this.detonate();
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        this.detonate();
    }

    /** 范围伤害加一次粒子与音效，然后消失 */
    private void detonate() {
        if (this.detonated || this.level().isClientSide) {
            return;
        }
        this.detonated = true;
        Level level = this.level();
        DamageSource source = this.fieldDamageSource();
        AABB field = this.getBoundingBox().inflate(FIELD_RADIUS);
        // 先收集再结算：hurt 会在某些情况下把实体移出世界，边遍历边改列表不安全
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, field);
        for (LivingEntity target : targets) {
            if (target == this.getOwner() || !target.isAlive()) {
                continue;
            }
            target.hurt(source, FIELD_DAMAGE);
        }
        if (level instanceof ServerLevel server) {
            server.sendParticles(ModParticles.CARMINITE_EFFECT.get(),
                this.getX(), this.getY(), this.getZ(), 24, 1.2D, 1.2D, 1.2D, 0.02D);
        }
        BlockPos pos = this.blockPosition();
        level.playSound(null, pos, ModSounds.CARMINITE_FIELD.get(), SoundSource.NEUTRAL, 1.5F, 1.0F);
        this.discard();
    }

    private DamageSource fieldDamageSource() {
        return this.damageSources().source(ModDamageTypes.CARMINITE_FIELD, this.getOwner(), this);
    }
}
