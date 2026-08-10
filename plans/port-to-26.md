# Porting to Minecraft 26.x

Spike on **TrarnCore**, run 2026-08-01. TrarnCore is ported and building on branch
`mc-26.1.2`; the other six mods are untouched and `main` remains on 1.21.11.

## Why this is not a version bump

**Minecraft 26.x ships unobfuscated.** Mojang stopped publishing obfuscation maps:

| Version | `downloads` keys in the version manifest |
| --- | --- |
| 1.21.11 | client, **client_mappings**, server, **server_mappings** |
| 26.1, 26.1.2, 26.2 | client, server |

Three consequences, all verified:

- **Yarn is over for 26.x.** Zero builds on Fabric's Maven; last updated 2026-05-27.
- **Intermediary is `0.0.0`** — a no-op placeholder, because there is nothing left to map.
- **The jar carries Mojang's own names and package layout.** Every Yarn name in this repo is wrong.

Fabric itself is fine: Loader 0.19.3 and Fabric API `0.155.2+26.1.2` both support it.

## Target 26.1.2, not 26.2

| | protocol | world |
| --- | --- | --- |
| 26.1.2 | 775 | 4790 |
| 26.2 | 776 | 4903 |

Different protocol numbers mean a 26.2 client **cannot connect to a 26.1.2 server**. Fabric API
agrees independently: its 26.1.2 build covers `[26.1, 26.1.1, 26.1.2]` as one family, while the
26.2 build covers only `26.2`.

A later 26.1.2 → 26.2 bump is cheap by comparison — 275 classes removed, 432 added, out of ~9,700.

## The toolchain (solved)

This part is done — the spike reached `javac` with this configuration:

```gradle
plugins {
    // Loom split into remap/no-remap variants. Unobfuscated Minecraft needs no-remap.
    // Note the fully-qualified plugin id; 'fabric-loom-no-remap' alone does not resolve.
    id 'net.fabricmc.fabric-loom-no-remap' version '1.14.0-alpha.31'
}

loom {
    noIntermediateMappings()
    // Fabric API ships transitive access wideners that fail to apply against the
    // unobfuscated jar; ours needs rewriting in Mojang names regardless.
    enableTransitiveAccessWideners = false
}

dependencies {
    minecraft "com.mojang:minecraft:26.1.2"
    // No `mappings` line at all — there is nothing to map.
    implementation "net.fabricmc:fabric-loader:0.19.3"
    implementation "net.fabricmc.fabric-api:fabric-api:0.155.2+26.1.2"
}
```

Also required:

- **Java 21 → 25.** Minecraft 26.x declares `java-runtime-epsilon` (major 25). `25.0.3-tem` is
  already installed via SDKMAN. Update `options.release`, the toolchain block, and
  `"java": ">=25"` in every `fabric.mod.json`.
- **Gradle 9.5.1 → 9.7.0.** Loom 1.18 demands it; the no-remap variant works with it too.
- **`modImplementation` → `implementation`, `modCompileOnly` → `compileOnly`.** No-remap Loom does
  not define the `mod*` configurations, because nothing is remapped.
- **`"minecraft": ">=26.1 <26.2"`** in every `fabric.mod.json`.

**`include` (jar-in-jar) still works**, so the TrarnCore bundling architecture survives intact —
verified: RSwitch's jar contains `META-INF/jars/trarncore-1.1.0.jar`.

- **There is no `remapJar` task.** Nothing is remapped, so `jar` is the final artifact. Any build
  logic referencing `remapJar` (our `exportJar`) must point at `jar` instead.

## Spike result: TrarnCore is ported and building

Done on branch `mc-26.1.2`. It compiles clean against 26.1.2 and produces a valid jar
(`minecraft >=26.1 <26.2`, `java >=25`, access widener in the `official` namespace).

Everything below the toolchain section is what the port actually cost, and what it implies for
the remaining six mods.

### Additional toolchain findings from doing it

- **The access widener namespace must be `official`, not `named`.** With no intermediary there is
  no named namespace; Loom fails with `Expected official namespace for access widener entry`.
- **Fabric API renamed its own APIs to match Mojang.** `fabric-key-binding-api-v1` became
  `fabric-key-mapping-api-v1`, and `KeyBindingHelper.registerKeyBinding` became
  `KeyMappingHelper.registerKeyMapping`. Expect the same for other modules as each is touched.
- **Soft dependencies need 26.x builds**: ModMenu `18.0.0`, Cloth Config `26.1.154+fabric`. The old
  ones are compiled against intermediary names and fail with `class file for net.minecraft.class_437
  not found`, which is a confusing way of saying "this dependency is for a different Minecraft".

### Renames that actually came up

```
MinecraftClient            -> Minecraft                 (net.minecraft.client)
Text                       -> Component                 (net.minecraft.network.chat)
Formatting                 -> ChatFormatting            (net.minecraft)
Identifier                 -> Identifier                (net.minecraft.resources)
Identifier.of              -> Identifier.fromNamespaceAndPath
KeyBinding                 -> KeyMapping                (net.minecraft.client)
InputUtil                  -> InputConstants            (com.mojang.blaze3d.platform)
KeyBinding.wasPressed      -> KeyMapping.consumeClick
TextRenderer               -> Font                      (net.minecraft.client.gui)
  .getWidth                ->   .width
  .draw                    ->   .drawInBatch
  TextLayerType            ->   Font.DisplayMode
MatrixStack                -> PoseStack                 (com.mojang.blaze3d.vertex)
  .push/.pop               ->   .pushPose/.popPose
  .peek().getPositionMatrix->   .last().pose()
  .multiply                ->   .mulPose
VertexConsumerProvider     -> MultiBufferSource         (net.minecraft.client.renderer)
VertexConsumer             -> (com.mojang.blaze3d.vertex)
  .vertex/.color           ->   .addVertex/.setColor
  .normal/.lineWidth       ->   .setNormal/.setLineWidth
Box                        -> AABB                      (net.minecraft.world.phys)
  .expand/.offset          ->   .inflate/.move
VoxelShape.getBoundingBox  -> .bounds()
BlockView                  -> BlockGetter               (net.minecraft.world.level)
BlockState.getOutlineShape -> .getShape
Camera.getRotation         -> .rotation()
ChatHud.addMessage         -> ChatComponent.addClientSystemMessage
MinecraftClient.inGameHud  -> Minecraft.gui
Style.withUnderline        -> .withUnderlined
MutableText.styled         -> MutableComponent.withStyle
Text.formatted             -> Component.withStyle
RenderLayer                -> RenderType                (net.minecraft.client.renderer.rendertype)
RenderLayers               -> RenderTypes
RenderLayer.of             -> RenderType.create
RenderSetup.Builder.build  -> RenderSetupBuilder.createRenderSetup
  .translucent()           ->   .sortOnUpload()
Defines                    -> ShaderDefines             (net.minecraft.client.renderer)
DepthTestFunction          -> CompareOp                 (com.mojang.blaze3d.platform)
```

**One false friend worth knowing about:** `RenderLayer` still exists in 26.x, as
`net/minecraft/client/renderer/entity/layers/RenderLayer` — an entity feature layer, completely
unrelated. A careless rename lands on it and produces nonsense.

### The render pipeline needed real work, not renaming

Depth and colour state are now grouped into records:

```java
// 1.21.11
.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
.withDepthWrite(false)
.withColorWrite(source.isWriteColor(), source.isWriteAlpha())
source.getBlendFunction().ifPresentOrElse(builder::withBlend, builder::withoutBlend);

// 26.1.2 — simpler, as it happens
.withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
.withColorTargetState(source.getColorTargetState())   // blend + write mask together
```

## The remaining six mods

TrarnCore was 1,345 lines and took ~200 errors to zero. Remaining: 58 files, ~9,600 lines.

The translation table above covers most of it, and the renames are highly repetitive — the
non-GUI mods should be substantially mechanical now.

**The GUI is the real remaining risk**, and it is worse than a rename:

- `Screen.render(DrawContext, int, int, float)` **no longer exists**. It is now
  `extractRenderState(GuiGraphicsExtractor, int, int, float)` — Minecraft moved to a retained-mode
  model where screens extract render state rather than drawing immediately.
- `DrawContext` has no direct successor; `GuiGraphicsExtractor` is a different thing.
- `addDrawableChild` -> `addRenderableWidget`, `close()` -> `onClose()`, `textRenderer` -> `font`,
  `client` -> `minecraft`, `keyPressed(KeyInput)` -> `keyPressed(KeyEvent)`.

Affected: ContainerUtil's `SearchScreen`, TrustUI's `TrustScreen` and both list widgets, ClaimViz's
`MapScreen`, and all six Cloth config screens. Those are rewrites against an unfamiliar model, not
translations. Budget accordingly — and expect Cloth Config's own API to have moved with it.

## Original scope estimate (superseded above)

TrarnCore alone — the smallest project, 1,345 lines — produces **200 javac errors**. Across all
seven projects: 58 files, 10,937 lines, 272 imports spanning 41 packages.

Eleven Yarn packages TrarnCore alone references no longer exist:

```
net.minecraft.block                net.minecraft.client.util
net.minecraft.client.font          net.minecraft.client.util.math
net.minecraft.client.gl            net.minecraft.text
net.minecraft.client.gui.screen    net.minecraft.util.math
net.minecraft.client.option        net.minecraft.util.shape
net.minecraft.client.render
```

Sample of the renames (names **and** packages moved):

```
MinecraftClient                 -> Minecraft
ClientWorld                     -> ClientLevel        (client.world -> client.multiplayer)
Text                            -> Component          (text -> network.chat)
net.minecraft.util.Identifier   -> net.minecraft.resources.Identifier
net.minecraft.item.ItemStack    -> net.minecraft.world.item.ItemStack
net.minecraft.util.math.BlockPos -> net.minecraft.core.BlockPos
```

The saving grace: the distinct-symbol count is small and highly repetitive — ~19 Minecraft types
in TrarnCore, ~90 across the whole repo. Build the translation table once and most of the work is
mechanical.

### The parts that are not mechanical

- **`render.Layers`.** `RenderLayer`, `Defines`, `DepthTestFunction`, `VertexConsumerProvider` all
  need re-verifying — `RenderType` did not resolve at the path a straight rename would predict.
  The no-depth pipeline cloning is the single riskiest piece in the repo and should be ported
  first, since everything visual depends on it.
- **The access widener.** `trarncore.accesswidener` names `RenderLayer.of` in Yarn terms and must
  be rewritten — or dropped, if the unobfuscated jar makes it unnecessary.
- **GUI.** `DrawContext` has no obvious `GuiGraphics` successor in the 26.1.2 jar. Every screen
  (`SearchScreen`, `TrustScreen`, both list widgets, the config screens) needs checking against
  reality, not assumption.
- **A year of Minecraft.** 1.21.11 → 26.1.2 is a lot of upstream change beyond renaming. Expect
  genuine API drift on top of the translation.

## What our prep actually bought

- **No mixins** — worth a great deal. Mixins against changed internals would be far worse.
- **Render code quarantined in `TrarnCore/render/`** — one place to fix, not six.
- **`versions.properties`** — the pin bump really is one file.

None of it helps with a mappings-ecosystem change. The design anticipated "Yarn names shift",
not "Yarn ends".

## Suggested order

1. `TrarnCore/render/Layers` — riskiest, and everything visual depends on it.
2. The rest of TrarnCore (chat, config, input, util are nearly rename-only).
3. RSwitch — smallest consumer, proves the library port end to end.
4. SimDistance, EasyPortalLinker — render-heavy but small.
5. ContainerUtil, TrustUI, ClaimViz — the big screens, last.

Keep 1.21.11 on a branch until 26.1.2 is fully working; the server is the only thing forcing the
move and there is no way to support both from one source tree.
