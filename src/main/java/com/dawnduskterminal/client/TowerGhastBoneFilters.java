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
 *   <li><b>YSM 的 head query 命名与 GeckoLib 相反</b>。维基写的是 {@code ysm.head_yaw} 与
 *       {@code query.head_x_rotation} 相同、{@code ysm.head_pitch} 与 {@code query.head_y_rotation} 相同，
 *       所以源动画里的 {@code query.head_x_rotation} 是<strong>偏航</strong>。
 *       {@code 转体} 那条链的输入就是它（不是俯仰），见 {@link #advanceIfNewTick}
 * </ul>
 *
 * <p>除了这 12 支按实体状态驱动的滤波器，这个类还持有 <strong>64 条链条滤波器的状态</strong>
 * （{@code ysm.bone_rot} 的父带子递次滞后，见 {@link #stepChain}）
 *
 * <p>源模型里 {@code v.bv}、{@code v.bg}、{@code v.hv}、{@code v.hg} 这些变量
 * 由外部自定义函数脚本喂值，而那套脚本不在这个模型包里，YSM 对未初始化变量取 0，所以这里也当 0
 * （{@code v.sleevefix} 与 {@code v.roaming.shoe} 不属此类：它们由片段自己的 timeline 赋值，
 * 转换时已搬进 {@code molang} 通道，见 {@code tools/ysm/README.md}）
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

    /** 第一刻要把累积的世界朝向对到实体的实际朝向上 */
    private boolean started;

    private final Map<String, SecondOrderState> secondOrder = new HashMap<>();
    private final Map<String, Double> firstOrder = new HashMap<>();

    /** 链条（{@code ysm.bone_rot} 的递次滞后）的滤波状态，同样按名字记忆 */
    private final Map<String, SecondOrderState> chainOrder = new HashMap<>();
    /** 链条结果的两刻缓存，交给渲染帧插值 */
    private final Map<String, double[]> chainValues = new HashMap<>();

    private final double[] current = new double[COUNT];
    private final double[] previous = new double[COUNT];
    private int lastTick = Integer.MIN_VALUE;

    /** 累积的世界朝向（度），用来代替 GeckoLib 没有的 {@code query.eye_target_y_rotation} */
    private double worldYaw;

    /** 本刻的 {@code ysm.perlin_noise(1492, query.life_time)}，由渲染层喂 */
    private double perlin;

    /**
     * 每个游戏刻推进一次；同一刻重复调用直接返回 false
     *
     * <p>{@code perlinNoise} 由客户端渲染层算好传进来（见 {@code TowerGhastBonePose}），
     * 这样这个类的单元测试不需要碰 LWJGL 的本地库
     *
     * <p>{@code partialTick} 不参与滤波器的积分：YSM 也是每 tick 积分一次，介于两刻之间的渲染帧靠
     * {@link #interpolate} 插值
     *
     * @return 这一回真的推进了一刻（调用方要在这一刻接着走链条，见 {@link #stepChain}）
     */
    public boolean advanceIfNewTick(TowerGhastEntity entity, double perlinNoise) {
        if (entity.tickCount == this.lastTick) {
            return false;
        }
        this.lastTick = entity.tickCount;
        this.perlin = perlinNoise;
        if (!this.started) {
            this.started = true;
            // 世界朝向从<strong>上一刻</strong>的朝向起步，再加上这一步的增量就等于当前的 yRot：
            // 从 getYRot() 起步会把第一帧的增量算两次
            this.worldYaw = entity.yRotO;
        }
        // wrapDegrees(yRot - yRotO) 是「这一步转了多少」，累加得到<strong>连续</strong>的世界朝向：
        // 直接读 yRot 会在 ±180 处跳 360 度，而滤波器的滞后会把那一跳放大成布条抽一下
        this.worldYaw += Mth.wrapDegrees(entity.getYRot() - entity.yRotO);
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
        // 源式子是 second_order('转体', first_order('身体角度修正', eye_target_y_rotation + query.head_x_rotation, 0.3), ...)
        // 而 YSM 的 query.head_x_rotation 是<strong>偏航</strong>（YSM 的 head query 与 GeckoLib 命名相反，
        // 见 tools/ysm/README.md），用俯仰当输入等于让整条转体链死掉：
        // 恶魂待机时靠 LookAtPlayer 一直在改头部偏航，俯仰几乎不变
        // eye_target_y_rotation（视角朝向）GeckoLib 没有，用累积的世界朝向加相对头部偏航代替，
        // 两者之和就是「头的世界偏航」，也就是源式子在源侧算出来的量
        this.current[BODY_ANGLE_FIX] = first("身体角度修正", this.worldYaw + headYaw, 0.3);
        this.current[BODY_TURN] = second("转体", this.current[BODY_ANGLE_FIX], 2.5, 0.3, 0.0);
        return true;
    }

    /**
     * 链条走一 tick：{@code 值 = sign * F(name, 输入) + parentGain * 输入}
     *
     * <p>{@code parallel0} 与 {@code pre_parallel0} 里用 {@code ysm.bone_rot('父骨骼')} 做
     * 「父带子」递次滞后的 64 条通道就是它（源式子有 {@code F(x)-x}、{@code -F(x)+0.8x}、{@code F(x)}
     * 三种形状，都落在这一条通式里）
     *
     * <p>输入由调用方从父骨骼上取（{@code TowerGhastBonePose.offsetDegrees}），也就是 YSM 说的
     * 「上一帧的骨骼旋转」。链条是一棵严格的父→子树，没有自引用，所以读回自己写出去的值不会发散
     *
     * <p>{@code filterInput} 喂给滤波器，{@code subtractInput} 是式子第二项用的父骨骼值
     * （64 条通道里只有一条的两个轴不同）
     *
     * <p>每 tick 只调用一次：filter 的状态按名字记忆，重复调用会多积分一步
     */
    public void stepChain(String name, double filterInput, double subtractInput, double frequency,
                          double coefficient, double sign, double parentGain) {
        SecondOrderState state = TowerGhastPhysics.secondOrderStep(filterInput, frequency, coefficient, 0.0,
            this.chainOrder.getOrDefault(name, SecondOrderState.ZERO));
        this.chainOrder.put(name, state);
        double[] values = this.chainValues.computeIfAbsent(name, unused -> new double[2]);
        values[0] = values[1];
        values[1] = sign * state.value() + parentGain * subtractInput;
    }

    /** 链条结果在两刻之间插值，没走过就直接是 0 */
    public double interpolateChain(String name, float partialTick) {
        double[] values = this.chainValues.get(name);
        if (values == null) {
            return 0.0;
        }
        return values[0] + (values[1] - values[0]) * partialTick;
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

