package com.elytradev.architecture.common.shape;

import com.elytradev.architecture.client.render.model.OBJSONModel;
import com.elytradev.architecture.client.render.target.RenderTargetBase;
import com.elytradev.architecture.client.render.texture.ITexture;
import com.elytradev.architecture.client.render.texture.TextureBase;
import com.elytradev.architecture.common.ArchitectureMod;
import com.elytradev.architecture.common.helpers.Trans3;
import com.elytradev.architecture.common.helpers.Vector3;
import com.elytradev.architecture.common.tile.TileShape;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;

/**
 * The walrus. The body is textured by the material it is cut from, while the
 * eye, whiskers and tusks keep the source model's own artwork; that needs
 * texture slots of its own, which the generic Model kind cannot express.
 *
 * <p>The objson tags those faces above the two material slots, and the source
 * UVs point at the small windows of the stitched skin they were painted in.
 * The body's UVs are block-space and tiled, so the material repeats once per
 * block instead of stretching over the model's two-block length.</p>
 */
public class WalrusKind extends ShapeKind.Model {

    /** Slots the objson tags the eye and the whiskers/tusks with. */
    public static final int EYE_TEXTURE = 3;
    public static final int DETAIL_TEXTURE = 4;

    /**
     * Both slots sample the one square skin stitched in ClientProxy.onStitch;
     * the model's UVs pick the small windows. The source ships a 32x16 variant
     * of the same skin, but the block atlas rejects a non-square, non-animated
     * sprite, so that one is not shipped.
     */
    private static final ResourceLocation DETAIL_SPRITE =
            new ResourceLocation(ArchitectureMod.MOD_ID, "blocks/walrus2");

    public WalrusKind() {
        super("walrus", null);
    }

    @Override
    public void renderShape(TileShape te,
                            ITexture[] textures, RenderTargetBase target, Trans3 t,
                            boolean renderBase, boolean renderSecondary,
                            int baseColourMult, int secondaryColourMult) {
        ITexture[] all = new ITexture[DETAIL_TEXTURE + 1];
        System.arraycopy(textures, 0, all, 0, Math.min(textures.length, all.length));
        ITexture artwork = sprite(DETAIL_SPRITE);
        all[EYE_TEXTURE] = artwork;
        all[DETAIL_TEXTURE] = artwork;
        // Everything above slot 1 would take the secondary material's tint;
        // the eye and tusks are painted, not cut from a block, so they are
        // drawn at full brightness instead.
        ((OBJSONModel) this.getModel()).render(t, target, baseColourMult, secondaryColourMult,
                EYE_TEXTURE, all);
    }

    @Override
    public boolean orientOnPlacement(EntityPlayer player, TileShape shape, TileShape neighbourShape,
                                     EnumFacing otherFace, Vector3 hit) {
        // The model's head is at local +Z. Put the head where the player is so
        // the two-block body lies away from them; which face was clicked, and
        // whether they sneak, changes nothing.
        shape.setSide(0);
        shape.setTurn(turnForDirection(towardsPlayer(shape, player)));
        shape.setOffsetX(0);
        return true;
    }

    /** Cardinal direction from the shape's cell to the player, flat. */
    private static EnumFacing towardsPlayer(TileShape shape, EntityPlayer player) {
        BlockPos pos = shape.getPos();
        double dx = player.posX - (pos.getX() + 0.5);
        double dz = player.posZ - (pos.getZ() + 0.5);
        if (Math.abs(dx) < 1e-4 && Math.abs(dz) < 1e-4)
            // Standing inside the cell: whatever way they face, put the head there.
            return player.getHorizontalFacing().getOpposite();
        if (Math.abs(dx) > Math.abs(dz))
            return dx > 0 ? EnumFacing.EAST : EnumFacing.WEST;
        return dz > 0 ? EnumFacing.SOUTH : EnumFacing.NORTH;
    }

    /** Turn that maps the model's local +Z to {@code dir}. */
    private static int turnForDirection(EnumFacing dir) {
        switch (dir) {
            case EAST:
                return 1;
            case NORTH:
                return 2;
            case WEST:
                return 3;
            default:
                return 0;
        }
    }

    private static ITexture sprite(ResourceLocation location) {
        TextureAtlasSprite icon = Minecraft.getMinecraft().getTextureMapBlocks()
                .getAtlasSprite(location.toString());
        if (icon == null)
            icon = Minecraft.getMinecraft().getTextureMapBlocks().getMissingSprite();
        return TextureBase.fromSprite(icon);
    }

    @Override
    public boolean acceptsCladding() {
        return false;
    }
}
