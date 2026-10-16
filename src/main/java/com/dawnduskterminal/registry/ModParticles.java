package com.dawnduskterminal.registry;

import com.dawnduskterminal.DawnDuskTerminal;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 砷铅铁反应权杖用到的三种粒子
 *
 * <p>贴图表与帧序在 {@code assets/dawnduskterminal/particles/particle_carminite_*.json}，
 * 客户端 provider 在 {@link com.dawnduskterminal.client.CarminiteParticle}
 */
public final class ModParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES =
        DeferredRegister.create(Registries.PARTICLE_TYPE, DawnDuskTerminal.MOD_ID);

    /** 飞行中的弹体本体 */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> CARMINITE_BALL =
        register("particle_carminite_ball");

    /** 弹体拖尾 */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> CARMINITE_TRACE =
        register("particle_carminite_trace");

    /** 命中后炸开的反应场 */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> CARMINITE_EFFECT =
        register("particle_carminite_effect");

    private ModParticles() {}

    private static DeferredHolder<ParticleType<?>, SimpleParticleType> register(String name) {
        // overrideLimiter 为 false，粒子照常受视距限制
        return PARTICLE_TYPES.register(name, () -> new SimpleParticleType(false));
    }

    public static void register(IEventBus bus) {
        PARTICLE_TYPES.register(bus);
    }
}
