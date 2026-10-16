package com.dawnduskterminal.client;

import com.dawnduskterminal.entity.TowerGhastEntity;
import com.dawnduskterminal.entity.TowerGhastPhysics;
import com.dawnduskterminal.entity.TowerGhastPhysics.SecondOrderState;
import net.minecraft.util.Mth;

import java.util.HashMap;
import java.util.Map;

/**
 * 高塔恶魂那两支有状态滤波器的运行时缓存，逐 tick 推进一次
 *
 * <p>YSM 的 {@code second_order(name, ...)} 按名字记忆状态。同名第一次之外的调用在 YSM 里也不会再积分
 * 一次（滤波器的 dt 是「距上次积分的 tick 差」，同帧内为 0），所以这里每个名字每 tick 只算一次，
 * 与源动画里同一个名字出现在多条通道上的写法一致
 *
 * <h2>三处与 GeckoLib 内置 query 不一样的地方</h2>
 *
 * 这几处如果直接照 GeckoLib 的 {@code MolangQueries} 抄，数值会差一个量级，实测症状是
 * <strong>眼睛偏离头部很远、头发看不到动</strong>：
 *
 * <ul>
 *   <li><b>头部偏航要相对身体</b>。Bedrock（也就是 YSM 的 {@code ysm.head_yaw}）给的是<em>相对身体</em>的
 *       头部转角，而 GeckoLib 的 {@code query.head_y_rotation} 是 {@code getViewYRot()}，
 *       也就是<em>世界坐标</em>下的偏航。眼睛里那条 {@code head_yaw/180} 在相对值上最大约 0.25 格，
 *       在绝对值上能到 2 格 —— 眼睛直接被推出脸外
 *   <li><b>速度类 query 是每秒，不是每 tick</b>。源动画把 {@code q.ground_speed} 夹在 {@code 0..20}、
 *       把 {@code q.vertical_speed} 乘 {@code -3} 后夹在 {@code -10..120}、把 {@code q.yaw_speed}
 *       乘 {@code -0.02} 后夹在 {@code -60..60}，这些夹位都只有在秒为单位的量级上才够得着；
 *       GeckoLib 的 {@code query.ground_speed} 是 {@code horizontalDistance()}，每 tick 只有 0.1 上下，
 *       于是乘完系数只剩零点几度，看上去就是「不动」。这里统一乘 20 换成每秒
 *   <li><b>取当前值而不是上一刻的插值</b>。{@code getViewXRot(0.0F)} 在 partialTick 为 0 时返回的是
 *       {@code yHeadRotO} 一类的上一刻值，滤波器本来就每 tick 只走一步，这里直接用当前值
 * </ul>
 *
 * <p>源模型里 {@code v.bv}、{@code v.bg}、{@code v.hv}、{@code v.hg}、{@code v.sleevefix} 这些变量
 * 由外部自定义函数脚本喂值，而那套脚本不在这个模型包里，YSM 对未初始化变量取 0，所以这里也当 0
 */
public final class TowerGhastBoneFilters {
    // 每个滤波器的结果在数组里的下标
    public static final int EYE_YAW = 0;
    public static final int EYE_PITCH = 1;
    public static final int CHEST_VERTICAL = 2;
    public static final int CHEST_HORIZONTAL = 3;
    public static final int CHEST_YAW_SPEED = 4;
    public static final int HAIR_VERTICAL = 5;
    public static final int HAIR_HORIZONTAL = 6;
    public static final int HAIR_YAW_SPEED = 7;
    public static final int HAIR_PITCH = 8;
    public static final int HAIR_PITCH_CLAMPED = 9;
    public static final int BODY_ANGLE_FIX = 10;
    public static final int BODY_TURN = 11;
    /** 滤波器个数，也是结果数组的长度 */
    public static final int COUNT = 12;

    /** 模型没有写进包里的那几个变量，YSM 取 0 */
    private static final double UNASSIGNED_VARIABLE = 0.0;
    /** 每秒 = 每 tick 乘 20 */
    private static final double PER_SECOND = 20.0;

    private final Map<String, SecondOrderState> secondOrder = new HashMap<>();
    private final Map<String, Double> firstOrder = new HashMap<>();

    private final double[] current = new double[COUNT];
    private final double[] previous = new double[COUNT];
    private int lastTick = Integer.MIN_VALUE;

    /** 本刻的 {@code ysm.perlin_noise(1492, query.life_time)}，由渲染层喂 */
    private double perlin;

    /**
     * 每个游戏刻推进一次；同一刻重复调用直接返回
     *
     * <p>{@code perlinNoise} 由客户端渲染层算好传进来（见 {@code TowerGhastBoneLayer}），
     * 这样这个类的单元测试不需要碰 LWJGL 的本地库
     *
     * <p>{@code partialTick} 不参与滤波器的积分：YSM 也是每 tick 积分一次，介于两刻之间的渲染帧靠
     * {@link #interpolate} 插值
     */
    public void advanceIfNewTick(TowerGhastEntity entity, double perlinNoise) {
        if (entity.tickCount == this.lastTick) {
            return;
        }
        this.lastTick = entity.tickCount;
        this.perlin = perlinNoise;
        System.arraycopy(this.current, 0, this.previous, 0, COUNT);

        // 相对身体的头部转角，见类注释
        double headYaw = Mth.wrapDegrees(entity.yHeadRot - entity.yBodyRot);
        double headPitch = entity.getXRot();
        // 每 tick 的速度乘 20 换成每秒，见类注释
        double verticalSpeed = entity.getDeltaMovement().y * PER_SECOND;
        double groundSpeed = entity.getDeltaMovement().horizontalDistance() * PER_SECOND;
        double yawSpeed = Mth.wrapDegrees(entity.getYRot() - entity.yRotO) * PER_SECOND;

        this.current[EYE_YAW] = second("眼睛yaw", headYaw / 180.0, 1.0, 0.8, 0.0);
        this.current[EYE_PITCH] = second("眼睛pitch", headPitch / 360.0, 1.0, 0.8, 0.0);
        this.current[CHEST_VERTICAL] = second("胸部垂直",
            clamp(verticalSpeed + UNASSIGNED_VARIABLE, -30.0, 30.0), 3.0, 0.3, 0.0);
        this.current[CHEST_HORIZONTAL] = second("胸部水平",
            clamp(groundSpeed + UNASSIGNED_VARIABLE, 0.0, 20.0), 2.0, 0.5, 1.0);
        this.current[CHEST_YAW_SPEED] = second("胸部角速度",
            clamp(-0.01 * yawSpeed, -30.0, 30.0), 3.0, 0.3, 0.0);
        this.current[HAIR_VERTICAL] = second("头发垂直",
            clamp(-3.0 * verticalSpeed + UNASSIGNED_VARIABLE, -10.0, 120.0), 2.0, 0.6, 0.0);
        this.current[HAIR_HORIZONTAL] = second("头发水平",
            clamp(groundSpeed + UNASSIGNED_VARIABLE, 0.0, 20.0), 2.0, 0.6, 0.0);
        this.current[HAIR_YAW_SPEED] = second("头发角速度",
            clamp(-0.02 * yawSpeed, -60.0, 60.0), 2.0, 0.6, 0.0);
        this.current[HAIR_PITCH] = second("抬头头发", headPitch, 2.0, 0.6, 0.0);
        this.current[HAIR_PITCH_CLAMPED] = second("抬头头发防穿模",
            Math.max(headPitch, -40.0), 2.0, 0.6, 0.0);
        // 源式子是 second_order('转体', first_order('身体角度修正', eye_target_y_rotation + head_x_rotation, 0.3), ...)
        // eye_target_y_rotation 是 YSM 独有的 query，GeckoLib 没有，取 0 之后输入就是 head_x_rotation
        this.current[BODY_ANGLE_FIX] = first("身体角度修正", headPitch, 0.3);
        this.current[BODY_TURN] = second("转体", this.current[BODY_ANGLE_FIX], 2.5, 0.3, 0.0);
    }

    /** 两刻之间插值，避免 20 Hz 的滤波器在更高帧率下抖动 */
    public double interpolate(int index, float partialTick) {
        return this.previous[index] + (this.current[index] - this.previous[index]) * partialTick;
    }

    private double second(String name, double input, double frequency, double coefficient, double response) {
        SecondOrderState state = TowerGhastPhysics.secondOrderStep(input, frequency, coefficient, response,
            this.secondOrder.getOrDefault(name, SecondOrderState.ZERO));
        this.secondOrder.put(name, state);
        return state.value();
    }

    private double first(String name, double input, double response) {
        double value = TowerGhastPhysics.firstOrderStep(input, response,
            this.firstOrder.getOrDefault(name, 0.0));
        this.firstOrder.put(name, value);
        return value;
    }

    private static double clamp(double value, double min, double max) {
        return TowerGhastPhysics.clamp(value, min, max);
    }
}

