# Seed3D GLB sizes (optimized for nightkitchen / Spatial SDK)

Pipeline per file:
1. `gltf-transform weld`
2. `gltf-transform simplify --ratio 0.06 --error 0.05` (target ~30k tris from ~500k)
3. `gltf-transform resize --width 1024 --height 1024`
4. `gltf-transform optimize --simplify false --texture-compress false --compress draco --texture-size 1024`

Kept PNG PBR maps (baseColor + metallicRoughness) at 1024²; Draco mesh compression.
Avoided WebP so Spatial loaders only need `KHR_draco_mesh_compression`.

| file | original | optimized | faces (opt) | notes |
|---|---:|---:|---:|---|
| wok.glb | 33.81 MB | 2.22 MB | 30,000 | in 15–40k face band |
| rice-bowl.glb | 26.43 MB | 1.36 MB | 44,384 | simplify floor ~44k (grainy topology); still <<8MB |
| egg.glb | 30.26 MB | 2.33 MB | 30,000 | in 15–40k face band |
| spatula.glb | 26.02 MB | 2.07 MB | 29,996 | in 15–40k face band |
| scallion.glb | 23.72 MB | 2.04 MB | 29,981 | in 15–40k face band |
| plate.glb | 18.57 MB | 0.72 MB | 29,998 | in 15–40k face band |

**Totals:** 158.81 MB → 10.74 MB (93.2% smaller)

Runtime copy in this repo: Draco was decoded out (`KHR_draco_mesh_compression` is not a documented Spatial SDK 6.1.9 feature, and the extension was required).

Cold-start pass after the emulator ANR on the 30k/1024 files. `gltf-transform simplify` then `resize` to 512². Rice grains are separate shells, so meshopt stopped near 31k faces; that file was vertex-clustered to 10k. No Draco.

| file | packed | faces | maps |
|---|---:|---:|---|
| wok.glb | 880 KB | 9,900 | 512 PNG |
| rice-bowl.glb | 550 KB | 10,000 | 512 PNG |
| egg.glb | 839 KB | 9,900 | 512 PNG |
| scallion.glb | 1.3 MB | 12,591 | 512 PNG |
| spatula.glb | 918 KB | 9,897 | 512 PNG |
| plate.glb | 554 KB | 9,899 | 512 PNG |

All six files ≤20 MB hard cap; all also under 8 MB soft target.
Priority trio (wok / rice-bowl / egg) ready for import.

Second cold-start pass. Triangle shells and 512² resolution are unchanged. Both PBR maps are JPEG quality 86 (4:4:4) instead of PNG. `loadSuspend` keeps the main looper inside `loadFromAssetsAsync` for the whole decode, and PNG inflate was most of the bytes in the priority trio.

| file | packed | faces | maps |
|---|---:|---:|---|
| wok.glb | 401 KB | 9,900 | 512 JPEG |
| rice-bowl.glb | 280 KB | 10,000 | 512 JPEG |
| egg.glb | 344 KB | 9,900 | 512 JPEG |
| scallion.glb | 793 KB | 12,591 | 512 JPEG |
| spatula.glb | 446 KB | 9,897 | 512 JPEG |
| plate.glb | 413 KB | 9,899 | 512 JPEG |

Third pass, after the emulator still showed 「没有响应」 while the staggered 10k-face set was streaming. `gltf-transform weld` then `simplify --ratio 0.40 --error 0.02`. Texture resolution stays 512² JPEG. Scallion did not collapse (topology floor near 11k), so that file is unchanged and is not in the cold-start set.

| file | packed | faces | maps |
|---|---:|---:|---|
| wok.glb | 222 KB | 3,960 | 512 JPEG |
| rice-bowl.glb | 152 KB | 3,996 | 512 JPEG |
| egg.glb | 177 KB | 3,960 | 512 JPEG |
| spatula.glb | 258 KB | 3,957 | 512 JPEG |
| plate.glb | 218 KB | 3,959 | 512 JPEG |
| scallion.glb | 793 KB | 12,591 | 512 JPEG |

Fourth pass. A 4k-face 512² wok still decoded in 1926 ms, and the not-responding dialog was up with only that mesh. Maps are now 256² JPEG. The wok was simplified again (`--ratio 0.45 --error 0.05`) to 1,782 triangles. Cold start loads none of these.

| file | packed | faces | maps |
|---|---:|---:|---|
| wok.glb | 97 KB | 1,782 | 256 JPEG |
| rice-bowl.glb | 101 KB | 3,996 | 256 JPEG |
| egg.glb | 129 KB | 3,960 | 256 JPEG |
| spatula.glb | 182 KB | 3,957 | 256 JPEG |
| plate.glb | 184 KB | 3,959 | 256 JPEG |
| scallion.glb | 693 KB | 12,591 | 256 JPEG |

## Round-2 Seed3D dish kits

The 32 optimized GLBs from the round-2 manifest replaced the hand-authored fillers. Source files required `KHR_draco_mesh_compression`. SDK 6.1.9 does not load that extension, so the copies in this folder were Draco-decoded with gltf-transform and nothing else: same triangles, same PNG maps. `stall-cloth.glb` is still the hand-authored backdrop and is not one of the 32.

Heaviest runtime copies before this pass: `plated-chow.glb` 19582408 bytes / 249162 triangles, `noodle-brick.glb` 13356188 / 138271, `chow-noodles.glb` 10133560 / 124716, plus `chow-cabbage.glb` 7788272 / 76768 and `noodle-veg.glb` 5361444 / 41265. Those five were the only files over 5 MB. 关东煮 still reuses Seed3D `egg.glb`. 泡面加料's third grab is `noodle-veg.glb`.

## Heavy-mesh pass (vertex cluster, 512 JPEG)

`gltf-transform simplify` does not collapse these Seed3D shells. With `--error 1` it still stopped near 4% (plated-chow 249162 → 238173 triangles) because the strands are disconnected. Draco stays out: SDK 6.1.9 cannot load `KHR_draco_mesh_compression`. Quantize stays out too: it requires `KHR_mesh_quantization`, which is not named in the SDK jars, and it rewrites accessor bounds to the integer grid. `fit()` uses the float bounds recorded in `DishKits`.

The five files were vertex-clustered on the decoded Seed3D mesh (grid weld, averaged positions, recomputed normals, original UVs), then `gltf-transform resize --width 512 --height 512` and `jpeg --formats png --quality 78`. No hand-authored replacement. Node scale stays `[2,2,2]`; `fit()` overwrites it and uses the accessor bounds below.

| file | before | after | tris before | tris after | maps |
|---|---:|---:|---:|---:|---|
| plated-chow.glb | 19582408 | 265328 | 249162 | 8493 | 512 JPEG |
| noodle-brick.glb | 13356188 | 357252 | 138271 | 9848 | 512 JPEG |
| chow-noodles.glb | 10133560 | 335500 | 124716 | 11387 | 512 JPEG |
| chow-cabbage.glb | 7788272 | 313064 | 76768 | 9127 | 512 JPEG |
| noodle-veg.glb | 5361444 | 275344 | 41265 | 5629 | 512 JPEG |

Accessor bounds after the cluster (the two that moved are written into `DishKits`; the other three matched the previous bounds):

| file | min | max |
|---|---|---|
| plated-chow.glb | -0.4967, -0.0975, -0.4546 | 0.4935, 0.0970, 0.4946 |
| noodle-brick.glb | -0.4947, -0.4946, -0.2399 | 0.4924, 0.4971, 0.2415 |
| chow-noodles.glb | -0.4964, -0.2619, -0.4974 | 0.4972, 0.2576, 0.4980 |
| chow-cabbage.glb | -0.4975, -0.1563, -0.4952 | 0.4957, 0.1524, 0.4984 |
| noodle-veg.glb | -0.4238, -0.4851, -0.4584 | 0.4224, 0.4962, 0.4608 |

Load strategy. `Entity.loadSuspend` always runs the native decode on the frame loop. `Entity.load` from a worker `runBlocking`s back onto that same call. Bytes are copied on `Dispatchers.IO` into `cacheDir/cookware` first, then `yieldMain()` so the decode is its own main-looper message, then `loadSuspend(File)`. The opening screen preloads only the 蛋炒饭 essentials (wok, rice bowl, egg, scallion, spatula, plate, bell, counter, lantern, drop ring). `BootSlot.DISH` is not in `deferredSteps`. The plated mesh is decoded when the phase is PLATE, SERVE, or REVIEW. Nameplates are the placeholder. A parented mesh scales from 4% to full over 400 ms. After the stall is built, the next dish's files are copied and not decoded. A dish change copies that kit (including the plated file) before decoding the grabs and the station.

## 自评草稿

界面视觉：稳 3★（冲 4）。五件重模从 5–19MB / 4万–25万面收到 300KB 上下 / 6k–11k 面，小于开摊路径里已有的葱（693KB / 12591 面），并且不在首屏解码。成品盘延后到盛盘。名牌占位，网格渐进出现。

创意 3。交互 3。功能 3。完成度 3。

冲 4 仍缺：没有在头显或模拟器里看过这批网格；灯笼、铜铃、金圈、木桌、布幔不是 Seed3D；原生解码仍是每个文件一条主线程消息；聚类相对交付的原网格是有损的。
