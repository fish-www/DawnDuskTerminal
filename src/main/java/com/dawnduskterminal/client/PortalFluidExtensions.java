package com.dawnduskterminal.client;

import com.dawnduskterminal.DawnDuskTerminal;
import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import org.joml.Vector3f;

public final class PortalFluidExtensions implements IClientFluidTypeExtensions {
    public static final PortalFluidExtensions INSTANCE = new PortalFluidExtensions();

    public static final ResourceLocation STILL = DawnDuskTerminal.id("block/portal_fluid_still");
    public static final ResourceLocation FLOWING = DawnDuskTerminal.id("block/portal_fluid_flow");

    /** Vanilla underwater screen texture, for the "water-like" submersion overlay. */
    private static final ResourceLocation UNDERWATER = ResourceLocation.withDefaultNamespace("textures/misc/underwater.png");

    /** The submersion fog colour {@code 0x7F159E}. */
    private static final Vector3f SUBMERSION_FOG = new Vector3f(127.0F / 255.0F, 21.0F / 255.0F, 158.0F / 255.0F);

    private PortalFluidExtensions() {}

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
    public ResourceLocation getRenderOverlayTexture(Minecraft mc) {
        return UNDERWATER;
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
        // Water-like fog density, slightly denser (-8 .. 64 blocks, cylinder).
        RenderSystem.setShaderFogStart(-8.0F);
        RenderSystem.setShaderFogEnd(64.0F);
        RenderSystem.setShaderFogShape(FogShape.CYLINDER);
    }
}
