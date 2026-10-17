package com.dawnduskterminal.gametest;

import com.dawnduskterminal.client.TowerGhastBoneFilters;
import com.dawnduskterminal.entity.TowerGhastEntity;
import com.dawnduskterminal.entity.TowerGhastPhysics;
import com.dawnduskterminal.registry.ModEntities;
import com.dawnduskterminal.entity.TowerGhastPhysics.SecondOrderState;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimatableManager;

import java.util.List;

/**
 * 把 Java 版滤波器的阶跃响应钉在 {@code tools/ysm/physics_reference.py} 的表上
 *
 * <p>那张表是量度「把调用换成它的输入」这个近似代价用的（稳态增益 1.0），同时也是这一份实现的
 * 正确性基准：表里写 1.0/0.8/0.0 在 11 tick 到 90%，Java 版就必须也是 11
 *
 * <p>参数对取自 {@code tools/ysm/README.md} 里那张表，以及主模型实际用到的几组
 */
@GameTestHolder("dawnduskterminal")
@PrefixGameTestTemplate(false)
public final class TowerGhastPhysicsTests {
    /** 跑到收敛用的 tick 上限，与参考实现的 settle 一致 */
    private static final int SETTLE_TICKS = 200;

    @GameTest(template = "ddt_empty")
    public static void secondOrderMatchesTheReferenceTable(GameTestHelper helper) {
        assertSecondOrder(helper, 1.0, 0.8, 0.0, 11);
        assertSecondOrder(helper, 2.0, 0.4, 0.0, 4);
        assertSecondOrder(helper, 2.0, 0.4, 1.0, 3);
        assertSecondOrder(helper, 2.5, 0.3, 0.0, 3);
        assertSecondOrder(helper, 3.0, 0.3, 0.0, 3);
        assertSecondOrder(helper, 3.0, 0.3, 1.0, 2);
        helper.succeed();
    }

    @GameTest(template = "ddt_empty")
    public static void firstOrderMatchesTheReferenceTable(GameTestHelper helper) {
        assertFirstOrder(helper, 0.3, 13);
        assertFirstOrder(helper, 0.5, 22);
        helper.succeed();
    }

    /** 参数被夹到合法范围：频率 0 到 5，阻尼 0 到 1，超出范围不该把状态推成 NaN */
    @GameTest(template = "ddt_empty")
    public static void outOfRangeParametersAreClamped(GameTestHelper helper) {
        SecondOrderState state = SecondOrderState.ZERO;
        for (int i = 0; i < SETTLE_TICKS; i++) {
            state = TowerGhastPhysics.secondOrderStep(1.0, 40.0, 5.0, 0.0, state);
        }
        helper.assertTrue(Double.isFinite(state.value()), "clamped parameters must not produce NaN");
        helper.assertTrue(Math.abs(state.value() - 1.0) < 1.0E-3,
            "steady state gain must be 1, got " + state.value());
        helper.succeed();
    }

    /**
     * 滤波器缓存按 tick 推进、收敛到它的输入，输入的量纲与源动画一致
     *
     * <p>这条覆盖三件事：状态确实跨刻保留、速度类输入是<strong>每秒</strong>、
     * 头部偏航是<strong>相对身体</strong>。后两件是实机跑出来的两类症状
     * （眼睛被推出脸外、头发看不到动）的根因，所以各钉一条
     */
    @GameTest(template = "ddt_empty")
    public static void boneFiltersConvergeToTheirInput(GameTestHelper helper) {
        TowerGhastEntity ghast = new TowerGhastEntity(ModEntities.TOWER_GHAST.get(), helper.getLevel());
        TowerGhastBoneFilters filters = new TowerGhastBoneFilters();

        // 身体朝 180，头再转 45：相对转角是 45，眼睛偏航的输入是 45/180 = 0.25
        // yRot/yRotO 归零：转体那条链现在跟的是「头的世界偏航」，初始朝向是随机的
        ghast.setYRot(0.0F);
        ghast.yRotO = 0.0F;
        ghast.yBodyRot = 180.0F;
        ghast.yHeadRot = 225.0F;
        // 水平 1 格/tick，换算是每秒 20，正好顶到「头发水平」夹取的上界
        ghast.setDeltaMovement(0.0, 0.0, 1.0);
        for (int i = 0; i < SETTLE_TICKS; i++) {
            ghast.tickCount++;
            filters.advanceIfNewTick(ghast, 0.0);
        }

        double eyeYaw = filters.interpolate(TowerGhastBoneFilters.EYE_YAW, 1.0F);
        helper.assertTrue(Math.abs(eyeYaw - 0.25) < 1.0E-2,
            "眼睛yaw must use the head rotation relative to the body (0.25), got " + eyeYaw);

        // 转体那条链的输入是头的世界偏航（世界视角 + 相对头转），yRot 归零后才好断言
        double bodyAngleFix = filters.interpolate(TowerGhastBoneFilters.BODY_ANGLE_FIX, 1.0F);
        helper.assertTrue(Math.abs(bodyAngleFix - 45.0) < 1.0E-2,
            "身体角度修正 must follow the head's world yaw (0+45), got " + bodyAngleFix);

        double hairHorizontal = filters.interpolate(TowerGhastBoneFilters.HAIR_HORIZONTAL, 1.0F);
        helper.assertTrue(Math.abs(hairHorizontal - 20.0) < 1.0E-2,
            "头发水平 must use blocks per second (20), got " + hairHorizontal);

        // 同一刻重复调用不该再积分一次
        double before = filters.interpolate(TowerGhastBoneFilters.HAIR_HORIZONTAL, 1.0F);
        filters.advanceIfNewTick(ghast, 0.0);
        helper.assertTrue(before == filters.interpolate(TowerGhastBoneFilters.HAIR_HORIZONTAL, 1.0F),
            "advancing twice in one tick must be a no-op");

        // 竖直 1 格/tick -> 每秒 20 -> 乘 -3 得 -60，被夹到 -10
        ghast.setDeltaMovement(0.0, 1.0, 0.0);
        for (int i = 0; i < SETTLE_TICKS; i++) {
            ghast.tickCount++;
            filters.advanceIfNewTick(ghast, 0.0);
        }
        double hairVertical = filters.interpolate(TowerGhastBoneFilters.HAIR_VERTICAL, 1.0F);
        helper.assertTrue(Math.abs(hairVertical + 10.0) < 1.0E-2,
            "头发垂直 must clamp -3*vertical on -10, got " + hairVertical);

        // 世界偏航整体转 90，但相对转角不变时眼睛不该动
        ghast.yBodyRot = 270.0F;
        ghast.yHeadRot = 315.0F;
        for (int i = 0; i < SETTLE_TICKS; i++) {
            ghast.tickCount++;
            filters.advanceIfNewTick(ghast, 0.0);
        }
        double eyeYawAgain = filters.interpolate(TowerGhastBoneFilters.EYE_YAW, 1.0F);
        helper.assertTrue(Math.abs(eyeYawAgain - 0.25) < 1.0E-2,
            "turning the whole body must not move the eyes, got " + eyeYawAgain);
        helper.succeed();
    }

    /**
     * 身体整体转身要让转体那条链动起来
     *
     * <p>源动画的输入是 {@code query.eye_target_y_rotation + query.head_x_rotation}（视角朝向加相对头转），
     * 对一只生物来说就是头的世界偏航。GeckoLib 没有前者，这里用<strong>累积的世界朝向</strong>代替，
     * 所以「原地转身」也必须能被看见 —— 待机时布条会摆，靠的就是它
     */
    @GameTest(template = "ddt_empty")
    public static void bodyTurnMovesTheClothChain(GameTestHelper helper) {
        TowerGhastEntity ghast = new TowerGhastEntity(ModEntities.TOWER_GHAST.get(), helper.getLevel());
        TowerGhastBoneFilters filters = new TowerGhastBoneFilters();
        ghast.setYRot(0.0F);
        ghast.yRotO = 0.0F;
        ghast.yBodyRot = 0.0F;
        ghast.yHeadRot = 0.0F;

        // 每 tick 转 10 度，9 tick 转出 90 度（yRot 会环绕，所以要靠 wrapDegrees 逐步累积）
        for (int step = 0; step < 9; step++) {
            ghast.yRotO = ghast.getYRot();
            ghast.setYRot(ghast.getYRot() + 10.0F);
            ghast.yBodyRot = ghast.getYRot();
            ghast.yHeadRot = ghast.getYRot();
            ghast.tickCount++;
            filters.advanceIfNewTick(ghast, 0.0);
        }
        double turning = filters.interpolate(TowerGhastBoneFilters.BODY_ANGLE_FIX, 1.0F);
        helper.assertTrue(turning > 45.0,
            "转 90 度后转体那条链要跟着走（>45），got " + turning);

        for (int i = 0; i < SETTLE_TICKS; i++) {
            ghast.yRotO = ghast.getYRot();
            ghast.tickCount++;
            filters.advanceIfNewTick(ghast, 0.0);
        }
        double settled = filters.interpolate(TowerGhastBoneFilters.BODY_ANGLE_FIX, 1.0F);
        helper.assertTrue(Math.abs(settled - 90.0) < 1.0E-2,
            "站住之后转体那条链要停在转过的角度（90），got " + settled);
        helper.succeed();
    }

    /**
     * 控制器的注册顺序就是优先级（GeckoLib 后注册的覆盖先注册的），这三条不能换
     *
     * <p>物理层提供「每帧把骨骼摆回初始姿势 + 自己的值」那个底，主动画在它之后才能赢过常量 0，
     * 换形开关（缩放）又要在主动画之后 —— 详情见 {@code TowerGhastEntity} 类注释的「分层」
     */
    @GameTest(template = "ddt_empty")
    public static void controllerOrderIsThePriorityOrder(GameTestHelper helper) {
        TowerGhastEntity ghast = new TowerGhastEntity(ModEntities.TOWER_GHAST.get(), helper.getLevel());
        AnimatableManager.ControllerRegistrar registrar =
            new AnimatableManager.ControllerRegistrar(new ObjectArrayList<>());
        ghast.registerControllers(registrar);

        List<String> names = registrar.controllers().stream().map(AnimationController::getName).toList();
        helper.assertTrue(names.indexOf("parallel0") < names.indexOf("main"),
            "物理层要在主动画之前（每帧重置的底），实际顺序：" + names);
        helper.assertTrue(names.indexOf("main") < names.indexOf("pre_parallel2"),
            "换形开关要在主动画之后（缩放压过关键帧），实际顺序：" + names);
        helper.assertTrue(names.getLast().equals("pre_parallel2"),
            "最后注册的应该是 pre_parallel2，实际顺序：" + names);
        helper.succeed();
    }

    /**
     * 链条（{@code ysm.bone_rot} 的父带子递次滞后）会先滞后再收敛到源式子的稳态
     *
     * <p>源动画里 64 条通道只有三种形状，这里各钉一条：{@code F(x)-x} 稳态是 0、
     * {@code -F(x)+0.8x} 稳态是 {@code -0.2x}、{@code F(x)} 稳态是 {@code x}
     *
     * <p>第一条最要紧：它说明布条会自己回到父骨骼的姿势，而不是一直积累偏移
     */
    @GameTest(template = "ddt_empty")
    public static void chainStepsLagThenSettle(GameTestHelper helper) {
        TowerGhastBoneFilters filters = new TowerGhastBoneFilters();

        // F(x)-x：输入阶跃到 45 度，第一刻滤波器还是零状态，于是整条式子就是 -45
        filters.stepChain("测试F减x", 45.0, 45.0, 2.0, 0.4, 1.0, -1.0);
        double firstStep = filters.interpolateChain("测试F减x", 1.0F);
        helper.assertTrue(firstStep < -40.0,
            "F(x)-x 的第一步还完全跟在父骨骼后面（约 -45），got " + firstStep);
        // 滞后量必须往回流，而不是越积越大
        for (int i = 0; i < 2; i++) {
            filters.stepChain("测试F减x", 45.0, 45.0, 2.0, 0.4, 1.0, -1.0);
        }
        double thirdStep = filters.interpolateChain("测试F减x", 1.0F);
        helper.assertTrue(Math.abs(thirdStep) < Math.abs(firstStep) / 2.0,
            "F(x)-x 的滞后量要往回流，第 3 刻就该明显小于第 1 刻，got " + thirdStep);
        for (int i = 0; i < SETTLE_TICKS; i++) {
            filters.stepChain("测试F减x", 45.0, 45.0, 2.0, 0.4, 1.0, -1.0);
        }
        double settled = filters.interpolateChain("测试F减x", 1.0F);
        helper.assertTrue(Math.abs(settled) < 1.0E-2,
            "F(x)-x 的稳态是 0（回到父骨骼的姿势），got " + settled);

        // -F(x)+0.8x：稳态是 (0.8-1)*45 = -9
        for (int i = 0; i < SETTLE_TICKS; i++) {
            filters.stepChain("测试反号", 45.0, 45.0, 2.0, 0.5, -1.0, 0.8);
        }
        double reversed = filters.interpolateChain("测试反号", 1.0F);
        helper.assertTrue(Math.abs(reversed + 9.0) < 1.0E-2,
            "-F(x)+0.8x 的稳态是 -9，got " + reversed);

        // F(x)：稳态是输入本身
        for (int i = 0; i < SETTLE_TICKS; i++) {
            filters.stepChain("测试纯滞后", 45.0, 45.0, 2.0, 0.4, 1.0, 0.0);
        }
        double pure = filters.interpolateChain("测试纯滞后", 1.0F);
        helper.assertTrue(Math.abs(pure - 45.0) < 1.0E-2,
            "F(x) 的稳态是输入本身，got " + pure);

        // 没走过的名字读出来是 0，而不是 NaN
        helper.assertTrue(filters.interpolateChain("没走过", 1.0F) == 0.0,
            "没走过的链条要读作 0");

        // 链条的下一级靠 chainPose 读父骨上一 tick 带出去的量（GeckoLib 每帧把骨骼重置回关键帧）
        helper.assertTrue(filters.chainPose("FragFlag2", 0) == 0.0, "没写过的骨骼姿态要读作 0");
        filters.setChainPose("FragFlag2", 0, 12.5);
        helper.assertTrue(filters.chainPose("FragFlag2", 0) == 12.5, "写进去的链条量要能读回来");
        helper.assertTrue(filters.chainPose("FragFlag2", 1) == 0.0, "同一个骨骼的其它轴不受影响");

        // stepChain 的返回值就是它写出去的那个量，调用方直接拿它记 chainPose
        double returned = 0.0;
        for (int i = 0; i < SETTLE_TICKS; i++) {
            returned = filters.stepChain("测试返回值", 30.0, 30.0, 2.0, 0.4, 1.0, 0.0);
        }
        helper.assertTrue(Math.abs(returned - 30.0) < 1.0E-2, "stepChain 要返回它写出去的量（稳态 30），got " + returned);
        helper.succeed();
    }

    private static void assertSecondOrder(GameTestHelper helper, double frequency, double coefficient,
                                         double response, int expectedTicks) {
        SecondOrderState state = SecondOrderState.ZERO;
        int reached = -1;
        for (int tick = 1; tick <= SETTLE_TICKS; tick++) {
            state = TowerGhastPhysics.secondOrderStep(1.0, frequency, coefficient, response, state);
            if (reached < 0 && Math.abs(state.value()) >= 0.9) {
                reached = tick;
            }
        }
        helper.assertTrue(Math.abs(state.value() - 1.0) < 1.0E-3,
            "second_order(" + frequency + ", " + coefficient + ", " + response
                + ") steady state must be 1, got " + state.value());
        helper.assertTrue(reached == expectedTicks,
            "second_order(" + frequency + ", " + coefficient + ", " + response
                + ") must reach 90% at tick " + expectedTicks + ", got " + reached);
    }

    private static void assertFirstOrder(GameTestHelper helper, double response, int expectedTicks) {
        double state = 0.0;
        int reached = -1;
        for (int tick = 1; tick <= SETTLE_TICKS; tick++) {
            state = TowerGhastPhysics.firstOrderStep(1.0, response, state);
            if (reached < 0 && Math.abs(state) >= 0.9) {
                reached = tick;
            }
        }
        helper.assertTrue(Math.abs(state - 1.0) < 1.0E-3,
            "first_order(response=" + response + ") steady state must be 1, got " + state);
        helper.assertTrue(reached == expectedTicks,
            "first_order(response=" + response + ") must reach 90% at tick " + expectedTicks
                + ", got " + reached);
    }
}
