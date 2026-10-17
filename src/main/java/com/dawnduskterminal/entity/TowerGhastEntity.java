package com.dawnduskterminal.entity;

import com.dawnduskterminal.registry.ModSounds;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * 高塔恶魂：由 GeckoLib 驱动，模型 贴图 动画都来自为 Yes Steve Model 制作的那套 Bedrock 文件
 *
 * <h2>动画</h2>
 * 动画文件是 YSM 文件，写的是 YSM 的 Molang 方言，即 Bedrock Molang 加一套 YSM 标准库，
 * 而 GeckoLib 两者都不实现 本模组用 {@code tools/ysm/ysm_to_geckolib.py} 转换，
 * 对照表见脚本与 {@code tools/ysm/README.md}，这里只说结论：
 *
 * <ul>
 *   <li>一级：YSM 文档里写明与某个 GeckoLib query 等值的名字，逐个改名，例如
 *       {@code ysm.head_yaw} == {@code query.head_y_rotation}
 *   <li>二级：{@code ysm.first_order} / {@code ysm.second_order} 是按名字记忆的递归滤波器，
 *       YSM 每帧积分一次 GeckoLib 的表达式是无状态的，存不下这个状态，所以直接取滤波器的输入信号
 *       两者稳态增益都是 1（已用参考实现验证），因此稳态精确，只丢掉几个 tick 的滞后，
 *       具体是 2 到 11 个 tick 视参数而定，见 {@code tools/ysm/README.md}
 *   <li>三级：{@code ysm.bone_rot(name)}（上一帧的骨骼变换）、{@code ysm.perlin_noise}、
 *       粒子与音效发射，在无状态环境下没有等价物，一律替换为 0
 * </ul>
 *
 * 这样文件里每个动画片段都能编译 这一点很关键：GeckoLib 逐个烘焙片段，
 * 遇到抛异常的片段会<em>跳过</em>，之前因此丢了 9 个片段
 * （walk jump swim fly elytra_fly attacked death parallel0 pre_parallel0），
 * 结果就是这只生物定格在一个姿势上
 *
 * <h2>分层</h2>
 * YSM 同时播放一个主动画加最多八个 {@code pre_parallel} 与八个 {@code parallel} 片段，
 * 全部循环，并且按<strong>加法</strong>混合旋转角（YSM 的并行动画）
 *
 * <p>GeckoLib <strong>不是加法</strong>：它的官方注释写着「多个控制器同时播放的动画会互相覆盖同一根骨骼」，
 * 实现上也是逐个控制器 {@code bone.setRotX(动画值 + 初始姿势)}（{@code AnimationProcessor.tickAnimation}），
 * 控制器按注册顺序遍历，后面注册的覆盖前面注册的
 * （{@code ControllerRegistrar.build()} 返回 {@code Object2ObjectArrayMap}，迭代顺序就是插入顺序）
 *
 * <p>所以并行层与主动画在同一个通道上不能共存，而 GeckoLib 的注册顺序就是我们唯一的旋钮。
 * 但它又<strong>不能把并行层删掉</strong>：对没有任何控制器写的骨骼，GeckoLib 只做一次很慢的回位插值
 * （{@code getBoneResetTime}，默认 5 秒），于是 {@code TowerGhastBonePose} 那种叠加写入会一帧一帧
 * 积下去（实测症状是布条的每个关节都在疯狂旋转）。所以并行层必须留着当那个「每帧重置」的人
 *
 * <p>据此的注册顺序（见 {@link #registerControllers}）：物理层在前，主动画在中间，换形开关在后
 *
 * <ul>
 *   <li>{@code parallel0} / {@code pre_parallel0} / {@code pre_parallel1} 先注册：它们每帧把布条、
 *       袖口、胸口的骨骼摆回「初始姿势 + 自己的值」，给下一层的叠加写入一个干净的底
 *   <li>主动画随后注册：它在布条与袖口上的关键帧因此能赢过上面那些常量 0 ——
 *       待机时袖口的 {@code 19.96+3*math.sin(120*life_time-120)} 不再被 {@code 0.5*clamp(-0.02*yaw_speed,…)}
 *       换成 0
 *   <li>{@code pre_parallel2} 最后注册：它是换形开关（{@code v.roaming.type} 把没选中的形态缩到 0）
 *       与脖子。YSM 那边缩放是<strong>相乘</strong>语义，所以开关必须压过主动画 ——
 *       {@code death} 会把 {@code Experiment115} 从 0 放大到 1，相乘后人类形态下仍然是隐藏的
 * </ul>
 *
 * <p>被主动画盖掉的那部分并行旋转（滤波器、{@code ysm.bone_rot} 的递次滞后、伯林噪声）由
 * {@code client/TowerGhastBonePose} 在动画写完之后<strong>叠加</strong>写回，两者合起来才是 YSM 的加法混合。
 * 剩下的差距是并行层里那些「速度驱动」的旋转项在主动画同通道上被盖掉的部分（袖口衬里在
 * {@code run} 里那几度），因为 GeckoLib 没有加法混合可以用
 *
 * <p>其余 {@code parallelN} / {@code pre_parallelN} 片段在 YSM 的滤波器被去掉之后逐字节恒定，
 * 为它们跑控制器只是在花时间写一个主动画本来就不碰的固定姿势
 */
public class TowerGhastEntity extends PathfinderMob implements GeoEntity {
    /**
     * 模型在屏幕上用的缩放，取自源 {@code ysm.json} 里声明的 {@code height_scale} / {@code width_scale}
     *
     * <p>只给渲染器用：碰撞箱与玩家同尺寸（见 {@code ModEntities}），不跟这个模型外廓对齐
     */
    public static final float MODEL_SCALE = 0.7F;

    /** 站着不动时的主动画 */
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    /** 行走时的主动画 */
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
    /** 快于散步时的主动画 */
    private static final RawAnimation RUN = RawAnimation.begin().thenLoop("run");
    /**
     * 受击的主动画，模型包自带，0.458 秒一次
     *
     * <p>YSM 的宿主状态机是 {@code hurtTime > 0} 时播它（OpenYSM 的 {@code AnimationRegister}），
     * 我们照抄这个条件：不接的话被打时模型一点反应都没有
     */
    private static final RawAnimation ATTACKED = RawAnimation.begin().thenPlay("attacked");
    /**
     * 死亡的主动画，模型包自带，1 秒并停在最后一帧
     *
     * <p>同样照抄 YSM 的条件 {@code isDeadOrDying()}。不接的话 GeckoLib 会用它自己那套
     * 「绕 Z 转 90 度」的通用倒地（{@code GeoEntityRenderer.applyRotations}），
     * 既不是这套模型想要的演出，也会和渲染器上被覆写成 0 的 {@code getDeathMaxRotation} 打架
     */
    private static final RawAnimation DEATH = RawAnimation.begin().thenPlayAndHold("death");

    /** 控制器名 GeckoLib 按名字区分控制器，所以每一层都要有自己的名字 */
    private static final String LYR_MAIN = "main";
    private static final String LYR_PHYSICS = "parallel0";
    private static final String LYR_LOCAL = "pre_parallel0";
    private static final String LYR_OFFSET = "pre_parallel1";
    private static final String LYR_CONFIG = "pre_parallel2";

    /**
     * 每个实体实例一个缓存，不能用 {@code SingletonAnimatableInstanceCache}，
     * 那个是给方块实体与物品用的，那些可动画对象每种类型只有一个
     */
    private final AnimatableInstanceCache animatableCache = GeckoLibUtil.createInstanceCache(this);

    public TowerGhastEntity(EntityType<? extends TowerGhastEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
            .add(Attributes.MAX_HEALTH, 20.0D)
            .add(Attributes.MOVEMENT_SPEED, 0.23D)
            .add(Attributes.FOLLOW_RANGE, 16.0D)
            .add(Attributes.STEP_HEIGHT, 0.6D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        // AvoidEntityGoal 的泛型上限是 PathfinderMob，而伤害只可能来自玩家，所以只值得躲玩家
        this.goalSelector.addGoal(1, new AvoidEntityGoal<>(this, Player.class, 6.0F, 1.0D, 1.2D));
        this.goalSelector.addGoal(2, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(4, new RandomLookAroundGoal(this));
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // 顺序就是优先级（GeckoLib 后注册的覆盖先注册的），见类注释的「分层」
        // 物理层先注册：它们负责每帧把骨骼摆回「初始姿势 + 自己的值」，
        // 否则 TowerGhastBonePose 的叠加写入会一帧一帧积起来
        controllers.add(loopLayer(LYR_PHYSICS, "parallel0"));
        controllers.add(loopLayer(LYR_LOCAL, "pre_parallel0"));
        controllers.add(loopLayer(LYR_OFFSET, "pre_parallel1"));

        // 主层：一次一个片段，由实体状态决定 5 tick 过渡，与 YSM 主动画之间的淡入淡出一致
        // 它在布条与袖口上的关键帧要赢过物理层那些常量 0
        controllers.add(new AnimationController<>(this, LYR_MAIN, 5,
            state -> state.setAndContinue(mainAnimation(this, state.isMoving()))));

        // 换形开关（形态缩放）与脖子最后注册：缩放要压过 death 之类主动画的关键帧
        controllers.add(loopLayer(LYR_CONFIG, "pre_parallel2"));
    }

    /**
     * 主动画的选择，抽成静态方法是为了能直接测：<strong>顺序就是优先级</strong>
     *
     * <p>死亡与受击都要压过移动，与 YSM 的状态机一致（它把死亡放在 {@code Priority.HIGHEST}、
     * 受击放在 2，都高于基础移动）
     */
    public static RawAnimation mainAnimation(TowerGhastEntity entity, boolean moving) {
        if (entity.isDeadOrDying()) {
            return DEATH;
        }
        if (entity.hurtTime > 0) {
            return ATTACKED;
        }
        if (!moving) {
            return IDLE;
        }
        // YSM 普通移动用 walk，冲刺用 run，这里能拿到的信号只有实体自身水平速度，
        // 就用它来代替那个判断
        return entity.getDeltaMovement().horizontalDistance() > 0.12D ? RUN : WALK;
    }

    /** 平行层控制器：只要实体活着就一直循环它的片段 */
    private AnimationController<TowerGhastEntity> loopLayer(String controllerName, String clipName) {
        RawAnimation animation = RawAnimation.begin().thenLoop(clipName);
        return new AnimationController<>(this, controllerName, 0, state -> state.setAndContinue(animation));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.animatableCache;
    }

    /**
     * 待机与受击用模型包自带的配音
     *
     * <p>模型包的 {@code sounds/} 里有 7 条待机（{@code id1-7}）与 5 条受击（{@code bad1-5}），
     * 变体交给 {@code sounds.json} 的同一个事件随机挑，所以这里只报事件名
     *
     * <p>死亡没有对应配音（包里那个 {@code attack.ogg} 是攻击用的，而这只生物没有攻击行为），
     * 所以死亡仍用原版恶魂的
     *
     * <p>不再覆写 {@code getVoicePitch}：那是为了让原版恶魂音效听起来低一点，换成真正的语音之后
     * 半速播放只会把台词拉长
     */
    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.TOWER_GHAST_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.TOWER_GHAST_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.GHAST_DEATH;
    }

    /** 像命名过的生物一样留在世界里，免得用来测试的对象悄悄消失 */
    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }
}
