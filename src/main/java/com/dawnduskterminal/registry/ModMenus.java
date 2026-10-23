package com.dawnduskterminal.registry;

import com.dawnduskterminal.DawnDuskTerminal;
import com.dawnduskterminal.inventory.Experiment051Menu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 菜单类型。目前只有试验物品 051 那一个界面
 *
 * <p>用 {@link IMenuTypeExtension#create} 而不是原版的 {@code new MenuType(...)}：界面要靠方块坐标把方块实体找回来，
 * 坐标是服务端在开界面时额外发过来的一段数据，原版的工厂函数拿不到
 */
public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
        DeferredRegister.create(Registries.MENU, DawnDuskTerminal.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<Experiment051Menu>> EXPERIMENT_051 =
        MENUS.register("experiment_051", () -> IMenuTypeExtension.create(Experiment051Menu::fromNetwork));

    private ModMenus() {}

    public static void register(IEventBus bus) {
        MENUS.register(bus);
    }
}
