package com.dawnduskterminal.client;

import com.dawnduskterminal.DawnDuskTerminal;
import com.dawnduskterminal.entity.TowerGhastEntity;
import com.dawnduskterminal.entity.TowerGhastPhysics;
import software.bernie.geckolib.animation.state.BoneSnapshot;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.stream.Collectors;

/**
 * 把 {@link TowerGhastBoneFilters} 的结果写回骨骼
 *
 * <h2>为什么必须是 {@code GeoModel.setCustomAnimations} 而不是渲染层</h2>
 *
 * GeckoLib 4.9.3 的管线是：
 *
 * <pre>
 * defaultRender
 *   preRender
 *   firePreRenderEvent
 *   preApplyRenderLayers      &lt;- BoneFilterGeoLayer 在这里
 *   actuallyRender
 *     handleAnimations        &lt;- tickAnimation 在这里把动画值写进骨骼
 *     renderRecursively       &lt;- 骨骼的矩阵在这里被读走
 * </pre>
 *
 * 也就是说 {@code BoneFilterGeoLayer} 跑在动画<strong>之前</strong>，它对骨骼的写入会被
 * {@code tickAnimation} 用「初始姿势 + 动画值」整条覆盖掉 —— 现象是层里的代码确实执行了
 * （日志能打出来）但屏幕上毫无变化
 *
 * <p>{@code setCustomAnimations} 是 {@code handleAnimations} 的最后一步，正好夹在
 * 「动画写完」与「矩阵读走」之间，官方注释也写着「Override to set custom animations (such as head
 * rotation, etc)」
 *
 * <h2>单位与符号</h2>
 *
 * 源动画里的表达式都是<strong>度</strong>，GeckoLib 在求值那条路径上自己做了两件事
 * （{@code AnimationController.getAnimationPointAtTick}）：常数以外的值先 {@code toRadians}，
 * 再把 X 与 Y 取反；Z 不取反。这里照做，于是本层写出的值与动画文件里同一个通道的值处于同一套单位
 *
 * <p>旋转是「初始姿势加偏移」，位置与缩放是绝对值，与 {@code AnimationProcessor} 里的应用方式一致
 */
public final class TowerGhastBonePose {

    private static final List<String> NAMED_BONES = List.of(
        "RightEyesBase", "LeftEyesBase",
        "Breast", "Carminite", "LeftGuard", "Bung",
        "FrontFragFlag", "BackFragFlag", "LeftFragFlag", "RightFragFlag",
        "AnteriorFragFlag", "AnteriorFragFlag2", "RearAnteriorFragFlag", "RearAnteriorFragFlag2",
        "FLongHair", "MLongHair", "ElytraLocator",
        "LeftHeadFrag", "RightHeadFrag");

    /** 发丝：{@code bone5} 到 {@code bone157} 这 38 根，父链都在 {@code Hair} 一系下面 */
    private record Strand(String bone, int axis, double perlinSign, double yawSign) {}

    /**
     * 发丝的通道表，从源动画的 {@code parallel0} 里抽出来的
     *
     * <p>它们的式子统一是 {@code ±10*ysm.perlin_noise(1492, query.life_time) ± ysm.second_order('头发角速度', …)}，
     * 只有落在哪个轴、两个符号是正是负不同。seed 全是 1492
     */
    private static final List<Strand> STRANDS = List.of(
        new Strand("bone10", 2, 1, 1),
        new Strand("bone11", 2, 1, 1),
        new Strand("bone127", 2, 1, 1),
        new Strand("bone128", 2, 1, 1),
        new Strand("bone13", 2, 1, 1),
        new Strand("bone131", 0, -1, -1),
        new Strand("bone15", 2, 1, 1),
        new Strand("bone151", 0, -1, -1),
        new Strand("bone154", 2, 1, 1),
        new Strand("bone155", 2, 1, 1),
        new Strand("bone157", 2, 1, 1),
        new Strand("bone17", 2, 1, 1),
        new Strand("bone18", 2, 1, 1),
        new Strand("bone19", 2, 1, 1),
        new Strand("bone20", 0, -1, -1),
        new Strand("bone21", 2, 1, 1),
        new Strand("bone22", 2, 1, 1),
        new Strand("bone23", 2, 1, 1),
        new Strand("bone24", 2, 1, 1),
        new Strand("bone25", 2, 1, 1),
        new Strand("bone34", 2, 1, 1),
        new Strand("bone37", 2, 1, 1),
        new Strand("bone38", 2, 1, 1),
        new Strand("bone39", 2, 1, 1),
        new Strand("bone40", 2, 1, 1),
        new Strand("bone5", 0, 1, 1),
        new Strand("bone6", 0, 1, 1),
        new Strand("bone7", 0, 1, 1),
        new Strand("bone76", 2, 1, 1),
        new Strand("bone8", 2, 1, 1),
        new Strand("bone82", 2, 1, 1),
        new Strand("bone86", 2, 1, 1),
        new Strand("bone87", 2, 1, 1),
        new Strand("bone88", 2, 1, 1),
        new Strand("bone9", 2, 1, 1),
        new Strand("bone91", 2, 1, 1),
        new Strand("bone92", 2, 1, 1),
        new Strand("bone93", 2, 1, 1));

    private static final Map<String, Strand> STRAND_BY_BONE = STRANDS.stream()
        .collect(Collectors.toUnmodifiableMap(Strand::bone, strand -> strand));

    private static final List<String> BONES;
    static {
        List<String> all = new ArrayList<>(NAMED_BONES);
        STRANDS.forEach(strand -> all.add(strand.bone()));
        BONES = List.copyOf(all);
    }

    /** 源动画里发丝用的 perlin seed，38 根全是它 */
    private static final int PERLIN_SEED = 1492;
    /** 源式子是 {@code 10*ysm.perlin_noise(...)}，10 度是振幅 */
    private static final double PERLIN_AMPLITUDE = 10.0;

    /** 每个实体一份滤波器状态。弱键，实体卸载后自己消失 */
    private final Map<TowerGhastEntity, TowerGhastBoneFilters> states = new WeakHashMap<>();

    /** 缺骨骼只报一次，别每帧刷屏 */
    private final List<String> reportedMissing = new ArrayList<>();

    /**
     * 每渲染帧调一次，紧跟在动画写入骨骼之后
     *
     * @param partialTick 用于两刻之间插值，滤波器本身每 tick 只走一步
     */
    public void apply(TowerGhastEntity entity, GeoModel<TowerGhastEntity> model, float partialTick) {
        TowerGhastBoneFilters filters = this.states.computeIfAbsent(entity, unused -> new TowerGhastBoneFilters());
        // query.life_time 是秒，与 GeckoLib 的 MolangQueries 一致
        double perlin = TowerGhastPhysics.perlinNoise(PERLIN_SEED, entity.tickCount / 20.0);
        filters.advanceIfNewTick(entity, perlin);

        List<String> missing = null;
        for (String name : BONES) {
            GeoBone bone = model.getBone(name).orElse(null);
            if (bone == null) {
                if (!this.reportedMissing.contains(name)) {
                    if (missing == null) {
                        missing = new ArrayList<>();
                    }
                    missing.add(name);
                }
                continue;
            }
            applyBone(bone, entity, partialTick, filters, perlin);
        }
        if (missing != null) {
            this.reportedMissing.addAll(missing);
            DawnDuskTerminal.LOGGER.warn(
                "DawnDuskTerminal 高塔恶魂的次级运动有 {} 根骨骼不在模型里，对应通道不会生效：{}",
                missing.size(), missing);
        }
    }

    private void applyBone(GeoBone bone, TowerGhastEntity entity, float partialTick,
                           TowerGhastBoneFilters filters, double perlin) {
        double eyeYaw = filters.interpolate(TowerGhastBoneFilters.EYE_YAW, partialTick);
        double eyePitch = filters.interpolate(TowerGhastBoneFilters.EYE_PITCH, partialTick);
        double chestVertical = filters.interpolate(TowerGhastBoneFilters.CHEST_VERTICAL, partialTick);
        double chestHorizontal = filters.interpolate(TowerGhastBoneFilters.CHEST_HORIZONTAL, partialTick);
        double chestYawSpeed = filters.interpolate(TowerGhastBoneFilters.CHEST_YAW_SPEED, partialTick);
        double hairVertical = filters.interpolate(TowerGhastBoneFilters.HAIR_VERTICAL, partialTick);
        double hairHorizontal = filters.interpolate(TowerGhastBoneFilters.HAIR_HORIZONTAL, partialTick);
        double hairYawSpeed = filters.interpolate(TowerGhastBoneFilters.HAIR_YAW_SPEED, partialTick);
        double hairPitch = filters.interpolate(TowerGhastBoneFilters.HAIR_PITCH, partialTick);
        double hairPitchClamped = filters.interpolate(TowerGhastBoneFilters.HAIR_PITCH_CLAMPED, partialTick);
        double bodyAngleFix = filters.interpolate(TowerGhastBoneFilters.BODY_ANGLE_FIX, partialTick);
        double bodyTurn = filters.interpolate(TowerGhastBoneFilters.BODY_TURN, partialTick);

        double chestSum = chestVertical + chestHorizontal;
        double turnLag = bodyTurn - bodyAngleFix;

        Strand strand = STRAND_BY_BONE.get(bone.getName());
        if (strand != null) {
            // 发丝只有这一条式子，符号与轴从表里取
            setRotation(bone, strand.axis(),
                strand.perlinSign() * PERLIN_AMPLITUDE * perlin + strand.yawSign() * hairYawSpeed);
            return;
        }

        switch (bone.getName()) {
            case "RightEyesBase", "LeftEyesBase" -> {
                bone.setPosX((float) eyeYaw);
                bone.setPosY((float) eyePitch);
            }
            case "Breast" -> {
                setRotation(bone, 0, chestSum);
                setRotation(bone, 1, chestYawSpeed);
                setScale(bone, 0, 1.0 - 0.002 * chestSum);
                setScale(bone, 1, 1.0 + 0.01 * chestSum);
                setScale(bone, 2, 1.0 - 0.008 * chestSum);
            }
            case "Carminite" -> {
                setRotation(bone, 0, chestSum);
                setRotation(bone, 2, 3.0 * chestYawSpeed);
            }
            case "LeftGuard" -> {
                setRotation(bone, 0, 3.0 * chestHorizontal - 3.0 * chestYawSpeed);
                setRotation(bone, 2, 3.0 * chestVertical);
            }
            case "Bung" -> {
                setRotation(bone, 0, chestVertical);
                setRotation(bone, 1, chestYawSpeed);
            }
            case "FrontFragFlag", "AnteriorFragFlag", "AnteriorFragFlag2" ->
                setRotation(bone, 2, 0.5 * turnLag);
            case "BackFragFlag", "RearAnteriorFragFlag", "RearAnteriorFragFlag2" ->
                setRotation(bone, 2, -0.5 * turnLag);
            case "LeftFragFlag", "RightFragFlag" ->
                setRotation(bone, 1, -0.3 * turnLag);
            case "FLongHair" -> setRotation(bone, 0, hairPitchClamped);
            case "MLongHair" -> {
                setRotation(bone, 0, hairVertical + hairHorizontal);
                setRotation(bone, 2, hairYawSpeed);
            }
            case "ElytraLocator" -> {
                setRotation(bone, 0, 0.6 * hairVertical + 0.6 * hairHorizontal);
                setRotation(bone, 2, hairYawSpeed);
            }
            case "LeftHeadFrag" -> {
                setRotation(bone, 0, -1.5 * hairVertical);
                setRotation(bone, 2, -0.9 * hairPitch + 1.5 * hairYawSpeed - 3.0 * hairHorizontal);
            }
            case "RightHeadFrag" -> {
                setRotation(bone, 0, -1.5 * hairVertical);
                setRotation(bone, 2, 0.9 * hairPitch + 1.5 * hairYawSpeed + 3.0 * hairHorizontal);
            }
            default -> {
            }
        }
    }

    /** 度，按 GeckoLib 对表达式的处理转成弧度：X 与 Y 取反，Z 不取反 */
    private static void setRotation(GeoBone bone, int axis, double degrees) {
        BoneSnapshot initial = bone.getInitialSnapshot();
        float radians = (float) Math.toRadians(degrees);
        switch (axis) {
            case 0 -> bone.setRotX(initial.getRotX() - radians);
            case 1 -> bone.setRotY(initial.getRotY() - radians);
            default -> bone.setRotZ(initial.getRotZ() + radians);
        }
    }

    private static void setScale(GeoBone bone, int axis, double value) {
        switch (axis) {
            case 0 -> bone.setScaleX((float) value);
            case 1 -> bone.setScaleY((float) value);
            default -> bone.setScaleZ((float) value);
        }
    }
}
