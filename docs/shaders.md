# Shader support

For maintainers. This is where the shader handling lives in this mod, and what
an Iris-based shader mod has to expose for shapes to take part. Nothing here is
needed to use the mod.

## The problem

A shape is drawn by the mod as ordinary geometry folded into the chunk being
rebuilt. The chunk builder labels that geometry with the block state of the
shape itself, and a shape's state carries only one thing a pack reads: the light
it emits. The material is invisible to the pack, so a wedge cut from a GregTech
lamp emits nothing, and a stained glass shape does not tint what passes through
it.

Two features follow, and they are separate pieces of work:

- **block ids.** A pack decides which blocks are light sources, which are glass,
  which stop light, by block id from its `block.properties`. A shape reports its
  own id, so none of those rules apply to it.
- **the second layer.** A material modelled as a base plus an emissive overlay
  (GregTech's bloom) draws only the base as a shape, so active casings look
  inactive. That is `client/render/MaterialOverlay.java`, no shader mod
  cooperation needed, and not described further here.

The rest of this file is the first one.

## Where the code lives

| File | What it holds |
| --- | --- |
| `compat/demonica/ShaderMaterialIds.java` | the whole integration: reflective resolve, per-material id caches, the stamp |
| `client/render/CustomBlockDispatcher.java` | the call site, in `customRenderBlockToWorld`, before the shape's quads are written |
| `client/render/ShaderCompat.java` | "is a shader pack running", OptiFine and `IrisApi`, cached for two seconds |
| `client/render/target/RenderTargetWorld.java` | `shaderPackInUse` forces vertex light to full brightness so a pack is not double-darkened |
| `common/ArchitectureConfig.java` | the `Shader material ids` switch and its system property |
| `client/ClientConfigEvents.java` | clears both caches when the switch changes |

The call chain, once per shape, on the chunk rebuild thread:

```text
CustomBlockDispatcher.customRenderBlockToWorld
  ShaderMaterialIds.stamp(buffer, pos, tile.getBaseBlockState())
    isActive()                 -> ShaderProviderHolder.isActive()
    choiceFor(material)        -> per-block cache, or per-state for translucent
      packId(block, meta)      -> provider.getBlockStateId(block, meta)   -1 = unmapped
    newContext(x & 15, y & 15, z & 15, id, (short) 0, (byte) 0)
    withBlockStateId(id)
    setActiveContext(buffer, ctx) -> BufferBuilderExtension.demonica$setActiveQuadContext
```

Details that matter if you touch it: the answer is cached per block for opaque
materials and per state for translucent ones, because glass carries its colour
in the metadata; a lamp is resolved by walking its states and keeping the first
bloom state the provider maps, since a pack usually maps only a few of them;
anything the provider reports as `<= 0` is cached as "no overlay" and never
retried.

## The three questions a shader mod has to answer

| Question | Who answers it today |
| --- | --- |
| Is a pack running? | `net.irisshaders.iris.api.v0.IrisApi.isShaderPackInUse()`, in every modern Iris build. `ShaderCompat` also checks OptiFine's `Config.isShaders()`. The stamping path uses Demonica's `ShaderProviderHolder.isActive()`. |
| What id does the pack give this block and metadata? | Iris builds the map from the pack's `block.properties`: `net.coderbot.iris.block_rendering.BlockRenderingSettings.INSTANCE.getBlockStateId(block, meta)`, returning `-1` when the pack has no entry. Every Iris build already has this, under that legacy package name. |
| How does the geometry about to be written claim that id? | Nothing in Iris does this for third-party geometry. Demonica adds it: a context attached to the buffer. |

Only the third is missing from a general Iris port, and it is the whole reason
this feature is Demonica-only in 0.5.

## What another Iris-based mod must expose

Three things, by any names, with these semantics:

```java
boolean isPackRunning();
int blockIdFor(Block block, int meta);   // > 0 mapped, <= 0 not in the pack
void setQuadContext(BufferBuilder buffer, int localX, int localY, int localZ,
                    int id, short renderType, byte lightValue);
```

Two ways to land it:

1. **Ship the names Demonica uses.** `com.demonica.celeritas.api.shader` with
   `ShaderProviderHolder.isActive/getProvider`,
   `ShaderProvider.getBlockStateId(Block, int)`, `vertex.VanillaQuadContext`
   with a `(int, int, int, int, short, byte)` constructor and
   `withBlockStateId(int)`, and
   `vertex.BufferBuilderExtension.demonica$setActiveQuadContext`. Our
   reflection finds it with no change in this repo. The cost is adopting
   another mod's package for a fork.
2. **Use your own names, adapt here.** The whole adapter is the four class-name
   constants at the top of `ShaderMaterialIds`, the two method names in
   `resolve()`, and the context construction in `stamp()`. Nothing else in the
   mod knows Demonica exists.

The context carries position and light as well as the id because a
Celeritas/Embeddium style chunk encoder consumes all three where the vertex is
written. A fork without that encoder can reduce it to one int on the buffer and
shrink the call site with it.

## Why not OptiFine, or plain Iris

Neither reads a per-block id from geometry a mod appends to a chunk. Their chunk
builders label the section with the section's block state, and there is no
vertex-level hook to disagree with it. So a shape cannot claim a different block
without a change inside the shader mod itself. Demonica is the one 1.12 shader
setup that has the hook, which is why it is the only target here.

What does apply everywhere, OptiFine included, is
`RenderTargetWorld.shaderPackInUse`: vertex light is forced to full brightness
while a pack runs, so the pack's own lighting is not darkened twice.

## Failure and switches

- No Demonica: one failed `Class.forName`, `isActive` null, no stamping, and no
  log line. Shapes look the same as before the feature.
- Demonica renames the API: same silent path. If the call itself throws,
  `stamp` logs once, "Shader material ids: could not set the block context,
  shapes keep their own id".
- A material the pack does not map: `-1`, not stamped, the shape keeps its id.
- `-Darchitecturecraft.shaderIds.mode=state`: skips the provider and stamps the
  material's `Block.getStateId` instead, for a pack that keys by state rather
  than by its block map.
- `Shader material ids` off in the config: `enabled()` is false and no work is
  done at all.

All of this is client side. A server never loads the class.

## How to check a change

1. Shaders on, a pack that maps GregTech's lamps. Place a lamp shape and the
   lamp block side by side in a dark room: both should light it.
2. A stained glass shape in a window with light behind it: what comes through
   should take the colour, not stay white.
3. Turn `Shader material ids` off and press F3+T: the lamp shape stops emitting,
   the block keeps glowing.
4. No shader pack at all: identical with the switch either way, and nothing new
   in the log.
