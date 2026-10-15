package com.dawnduskterminal.gametest;

import com.dawnduskterminal.registry.ModBlocks;
import com.dawnduskterminal.registry.ModTerrainBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Pins the face culling of the two translucent canopy blocks and of the portal fluid.
 *
 * <p>Two adjacent translucent blocks must not draw the face between them. That culling comes from
 * {@link net.minecraft.world.level.block.state.BlockState#skipRendering}, which
 * {@link Block#shouldRenderFace} consults before anything else ({@code ModelBlockRenderer} asks exactly this
 * question while meshing a chunk). It is the only mechanism available to a translucent block, because the
 * voxel-shape branch further down {@code shouldRenderFace} is guarded by {@code neighbour.canOcclude()}, and a
 * translucent full cube has to be noOcclusion. Vanilla glass and ice answer it through
 * {@code HalfTransparentBlock}; that is why the canopy blocks are registered as that class instead of a plain
 * {@link Block}, and these tests are what keeps it that way.
 *
 * <p>The fluid half is the same idea one level down: {@code LiquidBlockRenderer} asks
 * {@code BlockState#shouldHideAdjacentFluidFace} for every fluid face, whose default hides it when the
 * neighbouring block carries the same fluid.
 */
@GameTestHolder("dawnduskterminal")
@PrefixGameTestTemplate(false)
public final class TranslucentFaceCullingTests {
    private static final BlockPos A = new BlockPos(1, 2, 1);
    private static final BlockPos B = new BlockPos(2, 2, 1);
    private static final Direction A_TO_B = Direction.EAST;

    /** The exact question the chunk mesher asks for the face of A that points at B. */
    private static boolean faceOfARendered(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos a = helper.absolutePos(A);
        BlockPos b = helper.absolutePos(B);
        return Block.shouldRenderFace(level.getBlockState(a), level, a, A_TO_B, b);
    }

    @GameTest(template = "ddt_empty")
    public static void translucentCanopyCullsTheFaceBetweenTwoOfItself(GameTestHelper helper) {
        for (Block canopy : new Block[] {
                ModTerrainBlocks.FROZEN_CANOPY.get(), ModTerrainBlocks.FRAGILE_CANOPY.get()}) {
            helper.setBlock(A, canopy);
            helper.setBlock(B, canopy);
            helper.assertFalse(faceOfARendered(helper),
                "two adjacent " + canopy.getName().getString()
                    + " must cull the face they share, otherwise the inside of a canopy volume shows as a lattice");
        }
        helper.succeed();
    }

    @GameTest(template = "ddt_empty")
    public static void cullingAgreesWithTheVanillaReference(GameTestHelper helper) {
        // Vanilla ice is the same HalfTransparentBlock shape as frozen_canopy.
        helper.setBlock(A, Blocks.ICE);
        helper.setBlock(B, Blocks.ICE);
        helper.assertFalse(faceOfARendered(helper), "vanilla ice must cull the face shared with another ice");

        // Control: if an opaque pair did not cull, the assertion above would prove nothing.
        helper.setBlock(A, Blocks.STONE);
        helper.setBlock(B, Blocks.STONE);
        helper.assertFalse(faceOfARendered(helper), "two stones must cull the face between them");

        // The opaque face pointing into the translucent block has to stay, or the wall behind it shows a hole.
        helper.setBlock(A, Blocks.STONE);
        helper.setBlock(B, ModTerrainBlocks.FROZEN_CANOPY.get());
        helper.assertTrue(faceOfARendered(helper),
            "the stone face pointing into a translucent canopy block must be drawn");
        helper.succeed();
    }

    @GameTest(template = "ddt_empty")
    public static void differentBlocksAreNotCulledAgainstEachOther(GameTestHelper helper) {
        helper.setBlock(A, ModTerrainBlocks.FROZEN_CANOPY.get());
        helper.setBlock(B, ModTerrainBlocks.FRAGILE_CANOPY.get());
        helper.assertTrue(faceOfARendered(helper),
            "the two canopy blocks are different blocks; vanilla culls a block only against itself, so this face stays");
        helper.succeed();
    }

    @GameTest(template = "ddt_empty")
    public static void portalFluidCullsTheFaceBetweenTwoOfItself(GameTestHelper helper) {
        // Same placement as FluidReactionTests: the plain default state, read back in the same tick.
        helper.setBlock(A, ModBlocks.PORTAL_FLUID.get());
        helper.setBlock(B, ModBlocks.PORTAL_FLUID.get());
        ServerLevel level = helper.getLevel();
        BlockPos a = helper.absolutePos(A);
        BlockPos b = helper.absolutePos(B);
        // WEST is the face of B that touches A. The method answers true when that fluid face must NOT be
        // rendered, and A carries the same fluid, so the two blocks cull the surface between them like water.
        helper.assertTrue(
            level.getBlockState(b).shouldHideAdjacentFluidFace(A_TO_B.getOpposite(), level.getFluidState(a)),
            "two portal fluid blocks must hide the fluid surface between them, exactly like water does");
        // Leave nothing behind that would keep ticking (and therefore keep the test world dirty) afterwards.
        helper.setBlock(A, Blocks.AIR);
        helper.setBlock(B, Blocks.AIR);
        helper.succeed();
    }
}
