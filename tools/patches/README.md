# tools/patches

Patches this project applies to Thaumaturge on top of `thaumaturge_commit`. Both fetch scripts
(`tools/fetch-thaumaturge.ps1`, `tools/fetch-thaumaturge.sh`) apply every `*.patch` in this directory,
in filename order, immediately after `git checkout --force <commit>` and before the data generator
runs. `git apply --check` decides first, and a patch that no longer applies aborts the fetch: a
silently unpatched jar is indistinguishable from a patched one.

A patch is text, so it can be versioned here. The jar built from it cannot — see `libs/README.md` for
the licence (§3.1 forbids redistributing the mod or any binary built from it).

## aspect-index-performance.patch

Six files, 195 insertions and 48 deletions, no public signature changed, no data file touched. It
targets the aspect index: the `Item -> AspectList` map Thaumaturge builds at startup, caches in
`config/thaumaturge/aspect_index.json`, and sends to every client.

Measured on this machine — 1867 index entries, 2416 recipes, logs under
`build/analysis/server-gate-logs`:

| path | before | after |
| --- | --- | --- |
| warm start, cache hit | 61–90 ms on `Server thread` | 92 ms on `Worker-Main-16` |
| cold start, rebuild and write | 165 ms on `Server thread` | 77 ms on `Worker-Main-12` |

Both readings sit *after* `Done (…)` has been printed, which is the point: the server thread no longer
spends them, and the server accepts connections while the index is still being built.

1. **Fingerprint without the sort.** `AspectIndexFile.fingerprint` built a `List<String>` with one
   line per item, per base-aspect declaration and per recipe — roughly 4600 strings, each produced by
   `ResourceLocation.toString()` — sorted it, and hashed it with SHA-256. It is now one allocation-free
   pass: FNV-1a over the namespace and path characters, with an order-independent sum/xor pair, so the
   result no longer depends on iteration order and the sort can go. Output is 32 hex characters. Base
   aspects are mixed in one entry at a time, which is canonical by construction and removed the
   per-item `ArrayList` and its sort as well.
2. **Off the server thread.** `AspectIndexEvents.buildAndBroadcast` ran fingerprint, cache load, index
   build and cache write inline, on whichever thread fired `ServerStartedEvent` or the item data-map
   reload. It now takes a `RecipeManager`/`RegistryAccess` snapshot on that thread, bumps a generation
   counter, and hands the work to `Util.backgroundExecutor()`; the result is published back through
   `server.execute`. A stale result — a second `/reload` overtook it, or the server stopped — is
   dropped by the generation check. Reads before the first publish still return `AspectList.EMPTY`,
   exactly as before, which is the documented contract of `AspectIndexAccess`.
3. **Encode once, not once per recipient.** `ClientboundAspectIndexPayload` encoded the whole index for
   every player it was sent to, so a three-player login encoded it three times and a post-reload
   broadcast did it again for everyone. `AspectIndex` now caches its encoded form on first use and the
   payload writes a length prefix plus those bytes. `TCPayloads.VERSION` moved from `"2"` to `"3"`
   because the wire format changed.
4. **Skip an identical index on the client.** `AspectIndexClientHandler` set the index and rebuilt
   Thaumaturge's JEI pages — `AspectJeiSync.rebuildAspectStackPages()` walks every JEI ingredient —
   unconditionally. It now compares first with `AspectIndex.contentEquals` and returns early when the
   client already holds the same content, which is the normal case when a player rejoins a server whose
   data packs have not changed.

`FORMAT_VERSION` moved from 3 to 4, so the existing cache is rebuilt once after this patch lands. The
fingerprint covers the format version, so that happens by itself: the old file is detected as stale and
rewritten in place.

### Why it matters at scale

The numbers above are not a bottleneck and this patch does not claim to fix a hang; the server was
never stuck on that line. The reason to fix it anyway is what happens when the numbers grow:

* The payload *is* the entire index. At roughly 100–150 bytes per entry that is 200–280 KB today and
  1–1.5 MB at ten thousand entries — per login, per player.
* `Varint21FrameDecoder.MAX_VARINT21_BYTES` is 3, so a length prefix needing a fourth byte throws
  `CorruptedFrameException("length wider than 21-bit")`. The hard ceiling for a single packet is
  therefore **2,097,151 bytes**, and one index packet reaches it somewhere around 14k–20k entries. Past
  that the sync does not get slow, it fails. Making the broadcast cost proportional to *changes*
  instead of *recipients* is the prerequisite for the chunked or incremental sync that has to come
  next.

### The one assumption worth knowing

The cached encoded form embeds aspect registry **ids**, not resource locations —
`AspectInstance.STREAM_CODEC` uses `ByteBufCodecs.holderRegistry` — so those bytes are only valid for
the registry order they were written against. Item entries are resource locations and are always safe.
The cache lives on the index instance and the index is rebuilt on every data-map reload, and the
datapack registry sync precedes the play phase, so every connection sees the same order. A future
change that reloads the aspect registry without rebuilding the index would invalidate the assumption.

## Upstream

The patch is written to be sendable as a pull request: no API change, no new dependency, no behaviour
change beyond the work moving to another thread and the packet staying the same size. Suggested text:

**Title:** Build, cache and send the aspect index off the server thread, and only when it changed

**Body:**

> The aspect index is built from every item and recipe on the server, cached in
> `config/thaumaturge/aspect_index.json`, and synced to every client. Four spots scale with the size of
> the pack rather than with the size of the change, and all four are on the critical path:
>
> * `AspectIndexFile.fingerprint` materialised ~4600 strings (`ResourceLocation.toString()` per item,
>   base-aspect entry and recipe), sorted them, and hashed the result. Replaced with a single
>   allocation-free FNV-1a pass over namespace/path characters using an order-independent sum/xor pair,
>   which is why the sort is no longer needed. `FORMAT_VERSION` 3 → 4 so caches rebuild themselves.
> * `AspectIndexEvents.buildAndBroadcast` ran fingerprint, load, build and write inline on the server
>   thread, after `Done (…)` but still in its way. The heavy half now runs on
>   `Util.backgroundExecutor()` against a snapshot of `RecipeManager`/`RegistryAccess`, and is
>   published with `server.execute` under a generation counter so that an overtaken build — or one
>   finishing after `ServerStoppedEvent` — is discarded instead of overwriting a newer index.
> * The payload encoded the index once per recipient. `AspectIndex` now caches its encoded form, and
>   the payload writes a varint length plus that array. `TCPayloads.VERSION` 2 → 3.
> * The client rebuilt its JEI aspect pages on every index packet, including when the content was
>   identical to what it already had. `AspectIndex.contentEquals` now short-circuits that.
>
> Measured at 1867 entries / 2416 recipes: warm start 61–90 ms on the server thread → 92 ms on
> `Worker-Main-16`; cold start 165 ms → 77 ms on `Worker-Main-12`. Nothing about ordering changed from
> the client's point of view: the index still arrives on login and after a reload, and reads before the
> first build still see `AspectList.EMPTY`.
>
> Worth knowing for the next step: `Varint21FrameDecoder.MAX_VARINT21_BYTES` is 3, so a single packet
> cannot exceed 2,097,151 bytes — one full-index packet hits that around 14k–20k entries. Chunking or
> delta sync will be needed before then; this PR only makes the per-recipient cost disappear.

## Verification record

Two `server-gate.ps1` runs on 2026-10-04 against the patched jar
(`server-gate-20261004-202702.log`, `server-gate-20261004-202756.log`), both `VERDICT: PASS`, both
leaving zero server JVMs and a free world lock:

```
[20:26:57.157] [Server thread/INFO] … Done (3.930s)! For help, type "help"
[20:26:57.208] [Worker-Main-12/INFO] [thaumaturge/] … no longer matches … rebuilding
[20:26:57.285] [Worker-Main-12/INFO] [thaumaturge/] Wrote aspect index for 1867 items
[20:27:51.552] [Server thread/INFO] … Done (4.309s)! For help, type "help"
[20:27:51.644] [Worker-Main-16/INFO] [thaumaturge/] Loaded aspect index for 1867 items
```

For comparison, the same warm line before the patch, from the previous run's log:

```
[20:15:09.084] [Server thread/INFO] [thaumaturge/] Loaded aspect index for 1867 items
```

Change 4 and the encode side of change 3 are client-side and were not exercised by these runs; both
compile against verified signatures and the encode path reuses the pattern already in this repository
(`GolemAccessoryStates.encodedSize`). A client with JEI joining a dedicated server is what closes that
gap.
