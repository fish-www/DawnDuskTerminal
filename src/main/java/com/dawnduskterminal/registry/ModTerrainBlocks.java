package com.dawnduskterminal.registry;

import com.dawnduskterminal.DawnDuskTerminal;
import com.dawnduskterminal.world.CanopyFrostLayerBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HalfTransparentBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.SnowyDirtBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;
import java.util.Optional;

public final class ModTerrainBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(DawnDuskTerminal.MOD_ID);

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(DawnDuskTerminal.MOD_ID);

    public static final DeferredBlock<SnowyDirtBlock> ROSY_GRASS = BLOCKS.registerBlock(
        "rosy_grass", SnowyDirtBlock::new, grassBlock());

    public static final DeferredBlock<Block> ROSY_DIRT = BLOCKS.registerSimpleBlock(
        "rosy_dirt", dirt());

    public static final DeferredBlock<Block> CANOPY_STONE = BLOCKS.registerSimpleBlock(
        "canopy_stone", BlockBehaviour.Properties.of()
            .mapColor(MapColor.STONE)
            .requiresCorrectToolForDrops()
            .strength(2.5F, 9.0F)
            .sound(SoundType.STONE));

    public static final DeferredBlock<Block> OCHRE = BLOCKS.registerSimpleBlock(
        "ochre_block", BlockBehaviour.Properties.of()
            .mapColor(MapColor.DIRT)
            .requiresCorrectToolForDrops()
            .strength(2.5F, 9.0F)
            .sound(SoundType.STONE));

    public static final DeferredBlock<Block> GREENSCHIST = BLOCKS.registerSimpleBlock(
        "greenschist", BlockBehaviour.Properties.of()
            .mapColor(MapColor.QUARTZ)
            .requiresCorrectToolForDrops()
            .strength(2.5F, 9.0F)
            .sound(SoundType.STONE));

    public static final DeferredBlock<Block> ECLOGITE = BLOCKS.registerSimpleBlock(
        "eclogite", BlockBehaviour.Properties.of()
            .mapColor(MapColor.STONE)
            .requiresCorrectToolForDrops()
            .strength(5.0F, 9.0F)
            .sound(SoundType.STONE));

    public static final DeferredBlock<Block> CHARNOCKITE = BLOCKS.registerSimpleBlock(
        "charnockite", BlockBehaviour.Properties.of()
            .mapColor(MapColor.DEEPSLATE)
            .requiresCorrectToolForDrops()
            .strength(5.0F, 9.0F)
            .sound(SoundType.STONE));

    public static final DeferredBlock<Block> TWILIGHT_STONE = BLOCKS.registerSimpleBlock(
        "twilight_stone", BlockBehaviour.Properties.of()
            .mapColor(MapColor.TERRACOTTA_GRAY)
            .requiresCorrectToolForDrops()
            .strength(2.5F, 9.0F)
            .sound(SoundType.STONE));

    public static final DeferredBlock<Block> ROAD_SAND = BLOCKS.registerSimpleBlock(
        "road_sand", copied(Blocks.SAND));

    public static final DeferredBlock<Block> UNCONSCIOUS_SAND = BLOCKS.registerSimpleBlock(
        "unconscious_sand", copied(Blocks.RED_SAND));

    public static final DeferredBlock<Block> SILTSTONE = BLOCKS.registerSimpleBlock(
        "siltstone", BlockBehaviour.Properties.of()
            .mapColor(MapColor.TERRACOTTA_WHITE)
            .requiresCorrectToolForDrops()
            .strength(1.5F, 9.0F)
            .sound(SoundType.STONE));

    // HalfTransparentBlock, i.e. the class vanilla ice and vanilla glass both descend from
    // (IceBlock extends HalfTransparentBlock, TransparentBlock extends HalfTransparentBlock, and glass is a
    // TransparentBlock). It exists for exactly one reason: Block.shouldRenderFace asks
    // `state.skipRendering(neighbourState, side)` first, and HalfTransparentBlock answers "yes, skip" when the
    // neighbour is the very same block - the internal faces between two adjacent translucent cubes.
    //
    // A plain Block cannot get that culling from anywhere else: the voxel-shape branch further down
    // Block.shouldRenderFace is only reached when the neighbour `canOcclude()`, and a translucent full cube has
    // to be noOcclusion (see the note on FRAGILE_CANOPY below), so every face between two adjacent canopy
    // blocks would be drawn and the interior of a canopy volume would show as a ghostly lattice.
    public static final DeferredBlock<HalfTransparentBlock> FROZEN_CANOPY = BLOCKS.registerBlock(
        "frozen_canopy", HalfTransparentBlock::new, copied(Blocks.ICE));

    // Its texture (fragile_canopy.png, a uniform alpha of 204/255) and its block model
    // (models/block/fragile_canopy.json, "render_type": "minecraft:translucent") put the block in the
    // translucent chunk layer, so it has to be noOcclusion like the other translucent full cubes in vanilla
    // (glass, ice, honey, slime): an occluding block culls the faces of its neighbours, and a face that is not
    // drawn is a hole you can see straight through the translucent block.
    public static final DeferredBlock<HalfTransparentBlock> FRAGILE_CANOPY = BLOCKS.registerBlock(
        "fragile_canopy", HalfTransparentBlock::new, BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_LIGHT_BLUE)
            .strength(0.4F)
            .sound(SoundType.GLASS)
            .noOcclusion());

    // Not SnowLayerBlock itself: vanilla snow layers melt themselves away whenever the block light at their
    // position is above 11, and this dimension's oceans/aquifers/springs are portal_fluid at light level 15,
    // so every layer near water would evaporate. CanopyFrostLayerBlock drops only that melt.
    public static final DeferredBlock<CanopyFrostLayerBlock> CANOPY_FROST = BLOCKS.registerBlock(
        "canopy_frost", CanopyFrostLayerBlock::new, snowLayer());

    public static final DeferredBlock<Block> CANOPY_FROST_BLOCK = BLOCKS.registerSimpleBlock(
        "canopy_frost_block", BlockBehaviour.Properties.of()
            .mapColor(MapColor.SNOW)
            .strength(0.5F, 3.0F)
            .sound(SoundType.SNOW));

    public static final DeferredBlock<Block> CHAOS_STONE = BLOCKS.registerSimpleBlock(
        "chaos_stone", BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_BLACK)
            .requiresCorrectToolForDrops()
            .strength(25.0F, 1200.0F)
            .sound(SoundType.STONE));

    public static final DeferredBlock<Block> BLAZING_BLOCK = BLOCKS.registerSimpleBlock(
        "blazing_block", BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_ORANGE)
            .requiresCorrectToolForDrops()
            .strength(3.0F, 9.0F)
            .lightLevel(state -> 15)
            .sound(SoundType.BASALT));

    public static final DeferredItem<BlockItem> ROSY_GRASS_ITEM = ITEMS.registerSimpleBlockItem(ROSY_GRASS);
    public static final DeferredItem<BlockItem> ROSY_DIRT_ITEM = ITEMS.registerSimpleBlockItem(ROSY_DIRT);
    public static final DeferredItem<BlockItem> CANOPY_STONE_ITEM = ITEMS.registerSimpleBlockItem(CANOPY_STONE);
    public static final DeferredItem<BlockItem> OCHRE_ITEM = ITEMS.registerSimpleBlockItem(OCHRE);
    public static final DeferredItem<BlockItem> GREENSCHIST_ITEM = ITEMS.registerSimpleBlockItem(GREENSCHIST);
    public static final DeferredItem<BlockItem> ECLOGITE_ITEM = ITEMS.registerSimpleBlockItem(ECLOGITE);
    public static final DeferredItem<BlockItem> CHARNOCKITE_ITEM = ITEMS.registerSimpleBlockItem(CHARNOCKITE);
    public static final DeferredItem<BlockItem> TWILIGHT_STONE_ITEM = ITEMS.registerSimpleBlockItem(TWILIGHT_STONE);
    public static final DeferredItem<BlockItem> ROAD_SAND_ITEM = ITEMS.registerSimpleBlockItem(ROAD_SAND);
    public static final DeferredItem<BlockItem> UNCONSCIOUS_SAND_ITEM = ITEMS.registerSimpleBlockItem(UNCONSCIOUS_SAND);
    public static final DeferredItem<BlockItem> SILTSTONE_ITEM = ITEMS.registerSimpleBlockItem(SILTSTONE);
    public static final DeferredItem<BlockItem> FROZEN_CANOPY_ITEM = ITEMS.registerSimpleBlockItem(FROZEN_CANOPY);
    public static final DeferredItem<BlockItem> FRAGILE_CANOPY_ITEM = ITEMS.registerSimpleBlockItem(FRAGILE_CANOPY);
    public static final DeferredItem<BlockItem> CANOPY_FROST_ITEM = ITEMS.registerSimpleBlockItem(CANOPY_FROST);
    public static final DeferredItem<BlockItem> CANOPY_FROST_BLOCK_ITEM =
        ITEMS.registerSimpleBlockItem(CANOPY_FROST_BLOCK);
    public static final DeferredItem<BlockItem> CHAOS_STONE_ITEM = ITEMS.register(
        "chaos_stone",
        () -> new BlockItem(CHAOS_STONE.get(), new Item.Properties()) {
            @Override
            public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
                super.appendHoverText(stack, context, tooltip, flag);
                tooltip.add(Component.translatable("item.dawnduskterminal.chaos_stone.lore").withStyle(ChatFormatting.GRAY));
            }
        });
    public static final DeferredItem<BlockItem> BLAZING_BLOCK_ITEM = ITEMS.registerSimpleBlockItem(BLAZING_BLOCK);

    // Twilight Forest towerwood trim. A solid full cube whose connected-texture frame is drawn by
    // MountedTowerWoodModel (client-side), so the block itself is a plain Block. Hardness 3, blast
    // resistance 9, hand-mineable (drops itself), mineable/axe-tagged for faster axe breaking.
    public static final DeferredBlock<Block> MOUNTED_TOWERWOOD = BLOCKS.registerSimpleBlock(
        "mounted_towerwood", BlockBehaviour.Properties.of()
            .mapColor(MapColor.WOOD)
            .strength(3.0F, 9.0F)
            .sound(SoundType.WOOD));

    public static final DeferredItem<BlockItem> MOUNTED_TOWERWOOD_ITEM =
        ITEMS.registerSimpleBlockItem(MOUNTED_TOWERWOOD);

    // Twilight Forest tower beam. Same 47-tile connected-texture setup as mounted_towerwood.
    public static final DeferredBlock<Block> ENCASED_TOWERBEAM = BLOCKS.registerSimpleBlock(
        "encased_towerbeam", BlockBehaviour.Properties.of()
            .mapColor(MapColor.WOOD)
            .strength(3.0F, 9.0F)
            .sound(SoundType.WOOD));

    public static final DeferredItem<BlockItem> ENCASED_TOWERBEAM_ITEM =
        ITEMS.registerSimpleBlockItem(ENCASED_TOWERBEAM);

    public static final DeferredBlock<Block> SCARLET_MOLYBDENUM_ORE = BLOCKS.registerSimpleBlock(
        "scarlet_molybdenum_ore", BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_RED)
            .requiresCorrectToolForDrops()
            .strength(2.5F, 9.0F)
            .sound(SoundType.STONE));

    public static final DeferredItem<BlockItem> SCARLET_MOLYBDENUM_ORE_ITEM =
        ITEMS.registerSimpleBlockItem(SCARLET_MOLYBDENUM_ORE);

    public static final DeferredBlock<net.minecraft.world.level.block.RotatedPillarBlock> DAWN_LOG =
        BLOCKS.registerBlock("dawn_log", net.minecraft.world.level.block.RotatedPillarBlock::new, log(MapColor.WOOD));
    public static final DeferredBlock<net.minecraft.world.level.block.RotatedPillarBlock> DUSK_LOG =
        BLOCKS.registerBlock("dusk_log", net.minecraft.world.level.block.RotatedPillarBlock::new, log(MapColor.WOOD));
    public static final DeferredBlock<net.minecraft.world.level.block.RotatedPillarBlock> CHRONO_LOG =
        BLOCKS.registerBlock("chrono_log", net.minecraft.world.level.block.RotatedPillarBlock::new, log(MapColor.WOOD));

    public static final DeferredBlock<com.dawnduskterminal.world.WitheredLeavesBlock> WITHERED_DAWN_LEAVES =
        BLOCKS.registerBlock("withered_dawn_leaves", com.dawnduskterminal.world.WitheredLeavesBlock::new,
            witheredLeaves());
    public static final DeferredBlock<com.dawnduskterminal.world.WitheredLeavesBlock> WITHERED_DUSK_LEAVES =
        BLOCKS.registerBlock("withered_dusk_leaves", com.dawnduskterminal.world.WitheredLeavesBlock::new,
            witheredLeaves());
    public static final DeferredBlock<com.dawnduskterminal.world.WitheredLeavesBlock> WITHERED_CHRONO_LEAVES =
        BLOCKS.registerBlock("withered_chrono_leaves", com.dawnduskterminal.world.WitheredLeavesBlock::new,
            witheredLeaves());

    public static final DeferredBlock<com.dawnduskterminal.world.ChronoLeavesBlock> DAWN_LEAVES =
        BLOCKS.registerBlock("dawn_leaves",
            properties -> new com.dawnduskterminal.world.ChronoLeavesBlock(properties, () -> WITHERED_DAWN_LEAVES.get()),
            leaves());
    public static final DeferredBlock<com.dawnduskterminal.world.ChronoLeavesBlock> DUSK_LEAVES =
        BLOCKS.registerBlock("dusk_leaves",
            properties -> new com.dawnduskterminal.world.ChronoLeavesBlock(properties, () -> WITHERED_DUSK_LEAVES.get()),
            leaves());
    public static final DeferredBlock<com.dawnduskterminal.world.ChronoLeavesBlock> CHRONO_LEAVES =
        BLOCKS.registerBlock("chrono_leaves",
            properties -> new com.dawnduskterminal.world.ChronoLeavesBlock(properties, () -> WITHERED_CHRONO_LEAVES.get()),
            leaves());

    public static final DeferredBlock<RotatedPillarBlock> SKYGATE_PINE_LOG =
        BLOCKS.registerBlock("skygate_pine_log", RotatedPillarBlock::new, log(MapColor.COLOR_GREEN));

    public static final DeferredBlock<LeavesBlock> SKYGATE_PINE_LEAVES =
        BLOCKS.registerBlock("skygate_pine_leaves", LeavesBlock::new, leaves());

    public static final DeferredBlock<SaplingBlock> SKYGATE_PINE_SAPLING = BLOCKS.registerBlock(
        "skygate_pine_sapling",
        properties -> new SaplingBlock(new TreeGrower(
            "dawnduskterminal:skygate_pine",
            Optional.empty(),
            Optional.of(ResourceKey.create(Registries.CONFIGURED_FEATURE, DawnDuskTerminal.id("tree/skygate_pine"))),
            Optional.empty()
        ), properties),
        sapling());

    public static final DeferredItem<BlockItem> DAWN_LOG_ITEM = ITEMS.registerSimpleBlockItem(DAWN_LOG);
    public static final DeferredItem<BlockItem> DUSK_LOG_ITEM = ITEMS.registerSimpleBlockItem(DUSK_LOG);
    public static final DeferredItem<BlockItem> CHRONO_LOG_ITEM = ITEMS.registerSimpleBlockItem(CHRONO_LOG);
    public static final DeferredItem<BlockItem> DAWN_LEAVES_ITEM = ITEMS.registerSimpleBlockItem(DAWN_LEAVES);
    public static final DeferredItem<BlockItem> DUSK_LEAVES_ITEM = ITEMS.registerSimpleBlockItem(DUSK_LEAVES);
    public static final DeferredItem<BlockItem> CHRONO_LEAVES_ITEM = ITEMS.registerSimpleBlockItem(CHRONO_LEAVES);
    public static final DeferredItem<BlockItem> WITHERED_DAWN_LEAVES_ITEM =
        ITEMS.registerSimpleBlockItem(WITHERED_DAWN_LEAVES);
    public static final DeferredItem<BlockItem> WITHERED_DUSK_LEAVES_ITEM =
        ITEMS.registerSimpleBlockItem(WITHERED_DUSK_LEAVES);
    public static final DeferredItem<BlockItem> WITHERED_CHRONO_LEAVES_ITEM =
        ITEMS.registerSimpleBlockItem(WITHERED_CHRONO_LEAVES);
    public static final DeferredItem<BlockItem> SKYGATE_PINE_LOG_ITEM =
        ITEMS.registerSimpleBlockItem(SKYGATE_PINE_LOG);
    public static final DeferredItem<BlockItem> SKYGATE_PINE_LEAVES_ITEM =
        ITEMS.registerSimpleBlockItem(SKYGATE_PINE_LEAVES);
    public static final DeferredItem<BlockItem> SKYGATE_PINE_SAPLING_ITEM =
        ITEMS.registerSimpleBlockItem(SKYGATE_PINE_SAPLING);

    private ModTerrainBlocks() {}


    /**
     * Reuses the exact {@link BlockBehaviour.Properties} of a vanilla block, so the chrono block
     * behaves like the block it replaces inside the dimension.
     *
     * <p>Note that {@code ofFullCopy} also carries the source's loot-table reference. For a source
     * whose builder never called {@code noLootTable()}/{@code dropsLike(...)} that reference is
     * {@code null}, so the copy falls back to this block's own
     * {@code data/dawnduskterminal/loot_table/blocks/<id>.json} - which this mod ships alongside.
     */
    private static BlockBehaviour.Properties copied(BlockBehaviour source) {
        return BlockBehaviour.Properties.ofFullCopy(source);
    }

    private static BlockBehaviour.Properties stone(MapColor color) {
        return BlockBehaviour.Properties.of()
            .mapColor(color)
            .requiresCorrectToolForDrops()
            .strength(1.5F, 6.0F);
    }

    private static BlockBehaviour.Properties log(MapColor color) {
        return BlockBehaviour.Properties.of()
            .mapColor(color)
            .strength(2.0F)
            .sound(SoundType.WOOD);
    }

    private static BlockBehaviour.Properties leaves() {
        return BlockBehaviour.Properties.of()
            .mapColor(MapColor.PLANT)
            .strength(0.2F)
            .randomTicks()
            .sound(SoundType.GRASS)
            .noOcclusion()
            .ignitedByLava()
            .pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY);
    }

    private static BlockBehaviour.Properties sapling() {
        return BlockBehaviour.Properties.of()
            .mapColor(MapColor.PLANT)
            .noCollission()
            .randomTicks()
            .instabreak()
            .sound(SoundType.GRASS)
            .pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY);
    }

    private static BlockBehaviour.Properties witheredLeaves() {
        return BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_BROWN)
            .strength(0.2F)
            .randomTicks()
            .sound(SoundType.GRASS)
            .noOcclusion()
            .ignitedByLava()
            .pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY);
    }

    // Vanilla Blocks.GRASS_BLOCK properties, copied verbatim from 1.21.1.
    // Behaviour comes from SnowyDirtBlock (the class vanilla PODZOL uses): snowy state + placement/update shape,
    // with none of the vanilla-block hardcoding that vanilla's own GrassBlock/SpreadingSnowyDirtBlock carries
    // (it decays into Blocks.DIRT and spreads Blocks.GRASS_BLOCK).
    private static BlockBehaviour.Properties grassBlock() {
        return BlockBehaviour.Properties.of()
            .mapColor(MapColor.GRASS)
            .randomTicks()
            .strength(0.6F)
            .sound(SoundType.GRASS);
    }

    // Vanilla Blocks.DIRT properties, copied verbatim from 1.21.1.
    private static BlockBehaviour.Properties dirt() {
        return BlockBehaviour.Properties.of()
            .mapColor(MapColor.DIRT)
            .strength(0.5F)
            .sound(SoundType.GRAVEL);
    }

    // Vanilla Blocks.SNOW (net.minecraft.world.level.block.SnowLayerBlock) properties, copied verbatim from 1.21.1.
    private static BlockBehaviour.Properties snowLayer() {
        return BlockBehaviour.Properties.of()
            .mapColor(MapColor.SNOW)
            .replaceable()
            .forceSolidOff()
            .randomTicks()
            .strength(0.1F)
            .requiresCorrectToolForDrops()
            .sound(SoundType.SNOW)
            .isViewBlocking((state, level, pos) -> state.getValue(SnowLayerBlock.LAYERS) >= 8)
            .pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY);
    }

    // Vanilla Blocks.SNOW_BLOCK properties, copied verbatim from 1.21.1.
    private static BlockBehaviour.Properties snowBlock() {
        return BlockBehaviour.Properties.of()
            .mapColor(MapColor.SNOW)
            .requiresCorrectToolForDrops()
            .strength(0.2F)
            .sound(SoundType.SNOW);
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
    }
}
