package com.dawnduskterminal.mixin;

import com.dawnduskterminal.config.DdtConfig;
import com.dawnduskterminal.registry.ModDimensions;
import com.dawnduskterminal.world.ChronoLineState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;

/**
 * 让原版天空渲染器在这个维度里算出来的 timeOfDay 跟着玩家到晨昏线的距离走
 *
 * <p>{@code LevelRenderer.renderSky} 的天空穹顶、日落光晕、日月、星空，还有
 * {@code ClientLevel.getSkyColor}、{@code getSkyDarken}、{@code getStarBrightness}，全都只读
 * {@code getTimeOfDay}。覆盖这一个方法，原版那一整套渲染就等于自绘天空，不用自己画
 *
 * <p>原版里太阳仰角是 {@code 90° - 360° * timeOfDay}，代进
 * {@code 0.25 - param * sun_angle_max / 360} 正好得到 {@code param * sun_angle_max}，与自绘天空时期的
 * 太阳高度一致。{@code param} 为 0 时恰好落在地平线，原版的日落光晕也就出现在晨昏线上
 *
 * <p>{@code ClientLevel} 自己没有声明这个方法（来自 {@code LevelTimeAccess} 的默认方法），所以这里加的
 * 是新方法，落到目标类上就是一个覆盖，其他维度走回原版公式
 */
@Mixin(ClientLevel.class)
public abstract class ClientLevelTimeOfDayMixin {
    public float getTimeOfDay(float partialTick) {
        ClientLevel level = (ClientLevel) (Object) this;
        if (!level.dimension().equals(ModDimensions.CHRONO)) {
            return level.dimensionType().timeOfDay(level.dayTime());
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return 0.25F;
        }
        float param = (float) ChronoLineState.clientLine().param(player.getX(), player.getZ());
        return 0.25F - param * (float) DdtConfig.sunAngleMax() / 360.0F;
    }
}
