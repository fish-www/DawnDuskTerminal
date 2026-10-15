package com.dawnduskterminal.client;

import com.dawnduskterminal.DawnDuskTerminal;
import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import org.joml.Vector3f;

public final class ScorchingTwilightFluidExtensions implements IClientFluidTypeExtensions {
    public static final ScorchingTwilightFluidExtensions INSTANCE = new ScorchingTwilightFluidExtensions();

    public static final ResourceLocation STILL = DawnDuskTerminal.id("block/enthu_fluid_still");
    public static final ResourceLocation FLOWING = DawnDuskTerminal.id("block/enthu_fluid_flow");

    /** The submersion fog colour {@code 0xFFF0FF}. */
    private static final Vector3f SUBMERSION_FOG = new Vector3f(1.0F, 240.0F / 255.0F, 1.0F);

    private ScorchingTwilightFluidExtensions() {}

    @Override
    public ResourceLocation getStillTexture() {
        return STILL;
    }

    @Override
    public ResourceLocation getFlowingTexture() {
        return FLOWING;
    }

    @Override
    public int getTintColor() {
        return 0xFFFFFFFF;
    }

    @Override
    public Vector3f modifyFogColor(Camera camera, float partialTick, ClientLevel level, int renderDistance,
                                   float darkenWorldAmount, Vector3f fluidFogColor) {
        // Return a fresh instance: ClientHooks.getFogColor mutates the returned vector in place.
        return new Vector3f(SUBMERSION_FOG);
    }

    @Override
    public void modifyFogRender(Camera camera, FogRenderer.FogMode mode, float renderDistance, float partialTick,
                                float nearDistance, float farDistance, FogShape shape) {
        // Lava-like fog density: 0.25 .. 1.0 blocks.
        RenderSystem.setShaderFogStart(0.25F);
        RenderSystem.setShaderFogEnd(1.0F);
        RenderSystem.setShaderFogShape(FogShape.SPHERE);
    }
}
