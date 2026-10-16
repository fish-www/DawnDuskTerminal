package com.dawnduskterminal.gametest;

import com.dawnduskterminal.client.TowerGhastBoneFilters;
import com.dawnduskterminal.entity.TowerGhastEntity;
import com.dawnduskterminal.entity.TowerGhastPhysics;
import com.dawnduskterminal.registry.ModEntities;
import com.dawnduskterminal.entity.TowerGhastPhysics.SecondOrderState;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

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
        ghast.yBodyRot = 180.0F;
        ghast.yHeadRot = 225.0F;
        ghast.yRotO = ghast.getYRot();
        // 水平 1 格/tick，换算是每秒 20，正好顶到「头发水平」夹取的上界
        ghast.setDeltaMovement(0.0, 0.0, 1.0);
        for (int i = 0; i < SETTLE_TICKS; i++) {
            ghast.tickCount++;
            filters.advanceIfNewTick(ghast, 0.0);
        }

        double eyeYaw = filters.interpolate(TowerGhastBoneFilters.EYE_YAW, 1.0F);
        helper.assertTrue(Math.abs(eyeYaw - 0.25) < 1.0E-2,
            "眼睛yaw must use the head rotation relative to the body (0.25), got " + eyeYaw);

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
