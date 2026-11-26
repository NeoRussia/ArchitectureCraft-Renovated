package com.elytradev.architecture.common.shape;

import com.elytradev.architecture.common.helpers.Trans3;
import com.elytradev.architecture.common.helpers.Vector3;
import com.elytradev.architecture.common.tile.TileShape;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumFacing;

/**
 * A full block whose corner quarter — a 0.5 x 1 x 0.5 column, as with the
 * Corner Pillar — is the secondary (cladding) material. The remaining three
 * quarters are the block it was cut from.
 *
 * <p>Placement is exactly the Corner Pillar's: the column's long axis follows
 * the face you place it against (upright on a floor or ceiling, along the wall's
 * normal on a wall), the aimed corner picks which of that face's edges it runs
 * along, sneaking lays it on the picked face, and a piece placed against another
 * of these copies that one's direction so a run stays straight.</p>
 */
public class CladdingCornerPillar extends ShapeKind.Model {

    public CladdingCornerPillar() {
        super("cladding_corner_pillar", null);
    }

    @Override
    public boolean orientOnPlacement(EntityPlayer player, TileShape shape, TileShape neighbourShape,
                                     EnumFacing face, Vector3 hit) {
        if (!player.isSneaking() && neighbourShape != null
                && neighbourShape.getShape() == EnumShape.CLADDING_CORNER_PILLAR) {
            shape.setSide(neighbourShape.getSide());
            shape.setTurn(neighbourShape.getTurn());
            shape.setMirror(neighbourShape.getMirror());
            return true;
        }
        // The long axis follows the face, exactly as the Corner Pillar does.
        int side = face.getAxis().isVertical() ? 0 : face.getOpposite().ordinal();
        Vector3 h = Trans3.sideTurn(side, 0).ip(hit);
        int cx = h.x >= 0 ? 1 : -1;
        int cz = h.z >= 0 ? 1 : -1;
        int turn = turnForCorner(cx, cz);
        if (player.isSneaking()) {
            // Lying down: both the edge and the seating are fixed per aim, read
            // from the same table the Corner Pillar uses.
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

    /** Same table as {@link CornerPillar}, packed as {@code side * 4 + turn}. */
    private static final int[][] LYING = {
            {23, 8, 19, 12},  // DOWN
            {21, 14, 17, 10}, // UP
            {16, 2, 22, 4},   // NORTH
            {20, 0, 18, 6},   // SOUTH
            {12, 3, 10, 7},   // WEST
            {8, 1, 14, 5},    // EAST
    };

    /** Corner index for an aim: 0 north-west, 1 south-west, 2 south-east, 3 north-east. */
    private static int turnForCorner(int cx, int cz) {
        if (cx < 0)
            return cz < 0 ? 0 : 1;
        return cz > 0 ? 2 : 3;
    }

    @Override
    public boolean secondaryDefaultsToBase() {
        // Until a cladding is applied the quarter is just more of the block.
        return true;
    }
}
