# Pulsar RGB light

For maintainers. [Pulsar](https://github.com/Sumire-Labs/Pulsar) is a
Cleanroom RGB/scalar light engine. It is optional: nothing here is needed to run
the mod, and no dependency is added.

## What shapes get

- **The light on their faces.** Pulsar rewrites each vertex's colour with the
  RGB light at its position, after `BlockRendererDispatcher.renderBlock`. A
  shape never reaches that hook, so `RenderTargetWorld` gives the vertices it
  writes the same treatment: `RgbClient.tintAbgr` per vertex, then
  `RgbAmbientFloor.preserve` with the lightmap pair, so a full-bright face keeps
  its material colour.
- **Their own emission.** A shape of a lamp or other glowing material emits that
  material's colour at the shape's own `LIGHT` level. A shape that does not glow
  emits nothing.
- **Their transparency.** Light passing through a shape takes the absorption of
  its material, so a stained-glass shape tints light like the glass. The stronger
  of the base and secondary material wins per channel.
- **Held and dropped items** light at the stack's level, in white: Pulsar's
  held-item path reads only the block and metadata from the stack, not the
  material.

## How it is wired

| File | What it holds |
| --- | --- |
| `compat/pulsar/PulsarRgb.java` | the client tint, equivalent to Pulsar's block-renderer hook |
| `compat/pulsar/PulsarShapeLight.java` | positional emission and transmittance from the tile entity's materials, and the resample request |
| `core/PulsarShapeCoremod.java`, `core/PulsarShapeTransformer.java` | adds Pulsar's positional interfaces to `BlockShape` at class load, only when Pulsar is present |
| `client/render/target/RenderTargetWorld.java` | calls the tint for the vertices it has just written |
| `common/tile/TileShape.java` | asks Pulsar to resample the cell when a shape changes material |

The interfaces are added by a coremod rather than implemented directly, so the
shape block never references Pulsar when Pulsar is absent. The transformer uses
the ASM Forge already provides, returns the class untouched when Pulsar's API is
not on the class path, and any failure also returns it untouched. Every Pulsar
call is reflective and guarded in one place per class.

## Pulsar side

Everything a shape emits or filters comes from Pulsar's own data, so the
material's entry in its palette applies:

- `config/pulsar-colors.cfg` - source colours.
- `config/pulsar-filters.cfg` - per-channel absorption, `15` blocks a channel.
  Stained glass and stained glass panes are filtered out of the box; add other
  blocks here, e.g. `minecraft:stained_glass[color=red] = 0, 15, 15`.
- `config/pulsar.cfg` - `lightingMode=RGB` enables colour; `SCALAR` ignores the
  colour and this integration is inert.

## Limitations

- The in-hand light is white, for the reason above.
- Emission and filtering are per cell, not per face: a shape has two materials,
  so the brighter emission and the stronger absorption are used.
- When Pulsar's shader runtime is active it tints inside the pack itself and the
  client tint is skipped, which is what Pulsar's own block path does.
