package com.dawnduskterminal.client.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import org.jetbrains.annotations.Nullable;

/**
 * 「镶边塔木」的连接纹理模型，做法参考 Create 的 {@code create:andesite_casing}
 *
 * 包住普通的整方块模型，用 Create 的 {@code OMNIDIRECTIONAL} 索引公式把每个面的 UV 重映射进一张
 * 8x8 图集（标准的 47 格序列）
 *
 * <p>邻居连接在渲染时从世界读取（见
 * {@link #getModelData(BlockAndTintGetter, BlockPos, BlockState, ModelData)}），方块本身不存连接状
 * 态。物品用的是另一张普通的 {@code mounted_towerwood.png} 模型，不经过这里
 */
public class MountedTowerWoodModel extends BakedModelWrapper<BakedModel> {
    /** Create 的 OMNIDIRECTIONAL 图集大小：8x8 栅格 */
    public static final int SHEET_SIZE = 8;

    private static final ModelProperty<int[]> CT_INDICES = new ModelProperty<>();

    // DefaultVertexFormat.BLOCK：每个顶点 8 个 int（3 位置 + 1 颜色 + 2 UV + 1 光照 + 1 法线）
    private static final int VERTEX_STRIDE = 8;
    private static final int U_OFFSET = 4;
    private static final int V_OFFSET = 5;

    /** 面序号（0 到 5）对应的面内 [右, 上] 方向，与 Create 的 buildContext 一致 */
    private static final Direction[][] HV = {
        { Direction.EAST, Direction.SOUTH },   // DOWN
        { Direction.EAST, Direction.NORTH },   // UP
        { Direction.WEST, Direction.UP },      // NORTH
        { Direction.EAST, Direction.UP },      // SOUTH
        { Direction.SOUTH, Direction.UP },     // WEST
        { Direction.NORTH, Direction.UP },     // EAST
    };

    public MountedTowerWoodModel(BakedModel original) {
        super(original);
    }

    @Override
    public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData modelData) {
        int[] indices = new int[6];
        Arrays.fill(indices, -1);
        Block block = state.getBlock();
        for (Direction face : Direction.values()) {
            indices[face.ordinal()] = ctmIndex(level, pos, block, face);
        }
        return ModelData.of(CT_INDICES, indices);
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand,
            ModelData data, @Nullable RenderType renderType) {
        List<BakedQuad> quads = super.getQuads(state, side, rand, data, renderType);
        int[] indices = data.get(CT_INDICES);
        if (indices == null || quads.isEmpty()) {
            return quads;
        }

        List<BakedQuad> out = new ArrayList<>(quads.size());
        for (BakedQuad quad : quads) {
            int index = indices[quad.getDirection().ordinal()];
            if (index < 0) {
                out.add(quad);
            } else {
                out.add(remap(quad, index));
            }
        }
        return out;
    }

    private static BakedQuad remap(BakedQuad quad, int index) {
        int col = index % SHEET_SIZE;
        int row = index / SHEET_SIZE;
        var sprite = quad.getSprite();
        float u0 = sprite.getU0();
        float u1 = sprite.getU1();
        float v0 = sprite.getV0();
        float v1 = sprite.getV1();
        int[] vertices = quad.getVertices().clone();
        for (int vertex = 0; vertex < 4; vertex++) {
            int offset = vertex * VERTEX_STRIDE;
            // 顶点 UV 是贴图在整个图集里的区间（u0 到 u1）。先换算成贴图内 0 到 16，重映射到目标格，
            // 再换算回图集坐标
            float u = Float.intBitsToFloat(vertices[offset + U_OFFSET]);
            float v = Float.intBitsToFloat(vertices[offset + V_OFFSET]);
            float u16 = (u - u0) / (u1 - u0) * 16f;
            float v16 = (v - v0) / (v1 - v0) * 16f;
            float tileU = (u16 + col * 16f) / SHEET_SIZE;
            float tileV = (v16 + row * 16f) / SHEET_SIZE;
            vertices[offset + U_OFFSET] = Float.floatToRawIntBits(u0 + tileU / 16f * (u1 - u0));
            vertices[offset + V_OFFSET] = Float.floatToRawIntBits(v0 + tileV / 16f * (v1 - v0));
        }
        return new BakedQuad(vertices, quad.getTintIndex(), quad.getDirection(), quad.getSprite(), quad.isShade());
    }

    /** Create 的 {@code OMNIDIRECTIONAL.getTextureIndex}，一比一移植 */
    private static int ctmIndex(BlockAndTintGetter level, BlockPos pos, Block block, Direction face) {
        int side = sideOf(face);
        Direction h = HV[side][0];
        Direction v = HV[side][1];

        boolean up = connects(level, pos, block, v);
        boolean down = connects(level, pos, block, v.getOpposite());
        boolean left = connects(level, pos, block, h.getOpposite());
        boolean right = connects(level, pos, block, h);

        boolean topLeft = up && left && connects(level, pos, block, h.getOpposite(), v);
        boolean topRight = up && right && connects(level, pos, block, h, v);
        boolean bottomLeft = down && left && connects(level, pos, block, h.getOpposite(), v.getOpposite());
        boolean bottomRight = down && right && connects(level, pos, block, h, v.getOpposite());

        int tileX = 0;
        int tileY = 0;
        int borders = (!up ? 1 : 0) + (!down ? 1 : 0) + (!left ? 1 : 0) + (!right ? 1 : 0);

        if (up) {
            tileX++;
        }
        if (down) {
            tileX += 2;
        }
        if (left) {
            tileY++;
        }
        if (right) {
            tileY += 2;
        }

        if (borders == 0) {
            if (topRight) {
                tileX++;
            }
            if (topLeft) {
                tileX += 2;
            }
            if (bottomRight) {
                tileY += 2;
            }
            if (bottomLeft) {
                tileY++;
            }
        }

        if (borders == 1) {
            if (!right && (topLeft || bottomLeft)) {
                tileY = 4;
                tileX = -1 + (bottomLeft ? 1 : 0) + (topLeft ? 2 : 0);
            }
            if (!left && (topRight || bottomRight)) {
                tileY = 5;
                tileX = -1 + (bottomRight ? 1 : 0) + (topRight ? 2 : 0);
            }
            if (!down && (topLeft || topRight)) {
                tileY = 6;
                tileX = -1 + (topLeft ? 1 : 0) + (topRight ? 2 : 0);
            }
            if (!up && (bottomLeft || bottomRight)) {
                tileY = 7;
                tileX = -1 + (bottomLeft ? 1 : 0) + (bottomRight ? 2 : 0);
            }
        }

        if (borders == 2) {
            if ((up && left && topLeft) || (down && left && bottomLeft) || (up && right && topRight)
                || (down && right && bottomRight)) {
                tileX += 3;
            }
        }

        return tileX + 8 * tileY;
    }

    private static int sideOf(Direction face) {
        return switch (face) {
            case DOWN -> 0;
            case UP -> 1;
            case NORTH -> 2;
            case SOUTH -> 3;
            case WEST -> 4;
            case EAST -> 5;
        };
    }

    private static boolean connects(BlockAndTintGetter level, BlockPos pos, Block block, Direction d) {
        return level.getBlockState(pos.relative(d)).is(block);
    }

    private static boolean connects(BlockAndTintGetter level, BlockPos pos, Block block, Direction d1, Direction d2) {
        int dx = d1.getStepX() + d2.getStepX();
        int dy = d1.getStepY() + d2.getStepY();
        int dz = d1.getStepZ() + d2.getStepZ();
        return level.getBlockState(pos.offset(dx, dy, dz)).is(block);
    }
}
