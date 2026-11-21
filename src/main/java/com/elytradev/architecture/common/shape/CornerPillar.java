package com.elytradev.architecture.common.shape;

import com.elytradev.architecture.common.helpers.Trans3;
import com.elytradev.architecture.common.helpers.Vector3;
import com.elytradev.architecture.common.tile.TileShape;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumFacing;

/**
 * A quarter column: half a block cut in half again, 0.5 x 1 x 0.5, with its long
 * axis following the face you place it against. On a floor or a ceiling it
 * stands upright; on a wall it lies along the wall's normal, sticking out of it.
 *
 * <p>Sneaking lays the piece down on the face it is placed against. The corner
 * aimed at then picks which of that face's edges the long axis runs along:
 * north-west aims it eastwards across the face, north-east aims it southwards,
 * and so on clockwise, while the quarter stays against the face that was picked.
 * The result is worked out in the face's own frame, so a floor, a ceiling and
 * every wall behave alike. Placing against another corner pillar copies that
 * one's direction so a run stays straight.</p>
 *
 * <p>The model's base corner is north-west and turns run counter-clockwise, so
 * north-west is turn 0, south-west 1, south-east 2 and north-east 3, measured in
 * the shape's own frame after its side is applied.</p>
 */
public class CornerPillar extends ShapeKind.Model {

    public CornerPillar() {
        super("corner_pillar", null);
    }

    @Override
    public boolean orientOnPlacement(EntityPlayer player, TileShape shape, TileShape neighbourShape,
                                     EnumFacing face, Vector3 hit) {
        if (!player.isSneaking() && neighbourShape != null
                && neighbourShape.getShape() == EnumShape.CORNER_PILLAR) {
            shape.setSide(neighbourShape.getSide());
            shape.setTurn(neighbourShape.getTurn());
            // The paintbrush's turn and mirror ride along with the piece itself,
            // so a run decorated with it stays consistent as it is extended.
            shape.setMirror(neighbourShape.getMirror());
            return true;
        }
        // The long axis follows the face, exactly as the other shapes orient:
        // upright on a floor or ceiling, along the wall's normal on a wall.
        int side = face.getAxis().isVertical() ? 0 : face.getOpposite().ordinal();
        Vector3 h = Trans3.sideTurn(side, 0).ip(hit);
        int cx = h.x >= 0 ? 1 : -1;
        int cz = h.z >= 0 ? 1 : -1;
        int turn = turnForCorner(cx, cz);
        if (player.isSneaking()) {
            // Lying down, both the edge and the seating are fixed per aim, so
            // they are read from a table instead of being recomputed. The aim is
            // taken in the face's own frame, where local +Y is the outward normal
            // and +X and +Z run across the face, so the same aim gives the same
            // piece on any face.
            Vector3 local = Trans3.sideTurn(face.getOpposite().ordinal(), 0).ip(hit);
            int packed = LYING[face.ordinal()][turnForCorner(
                    local.x >= 0 ? 1 : -1, local.z >= 0 ? 1 : -1)];
            side = packed >> 2;
            turn = packed & 3;
        }
        shape.setSide(side);
        shape.setTurn(turn);
        return true;
    }

    /**
     * The lying pose for each face and aim, packed as {@code side * 4 + turn} and
     * indexed by {@link EnumFacing#ordinal()} then by {@link #turnForCorner}:
     * north-west, south-west, south-east, north-east.
     *
     * <p>Not guessed: each row is the floor case -- the one orientation that
     * behaved correctly -- rotated into that face's basis, the same basis normal
     * placement measures the aim in. So the long axis always runs along the edge
     * beside the corner aimed at, and the quarter always sits against the face
     * that was picked, on every face. Regenerate and re-verify with
     * {@code Work\architecturecraft-shapes\.scratch\corner_pillar_orient.py}.</p>
     */
    private static final int[][] LYING = {
            {23, 8, 19, 12},  // DOWN
            {21, 14, 17, 10}, // UP
            {16, 2, 22, 4},   // NORTH
            {20, 0, 18, 6},   // SOUTH
            {12, 3, 10, 7},   // WEST
            {8, 1, 14, 5},    // EAST
    };

    /**
     * Corner index for an aim: 0 north-west, 1 south-west, 2 south-east and
     * 3 north-east, clockwise. In the face's frame +x is to the right and +z is
     * down the face.
     */
    private static int turnForCorner(int cx, int cz) {
        if (cx < 0)
            return cz < 0 ? 0 : 1;
        return cz > 0 ? 2 : 3;
    }
}
