# Titular 称号模组 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 为 Fabric 1.20.4 实现服务端权威、JSON 持久化、支持三级权限与 LuckPerms 软依赖的纯视觉称号系统。

**Architecture:** 共享领域模型和格式化器不依赖界面；`TitularService` 作为唯一业务写入口；服务端生成权限裁剪快照，客户端只保存镜像并渲染。聊天在服务端装饰显示名，头顶名和 Tab 在客户端通过薄 Mixin 调用同一格式化器。

**Tech Stack:** Java 17、Fabric Loader/API 1.20.4、Yarn mappings、Gson、JUnit 5、Mixin、LuckPerms API 5.4（compileOnly）。

---

### Task 1: 整理模板并建立测试基线

**Files:**
- Modify: `build.gradle`
- Modify: `src/main/resources/fabric.mod.json`
- Modify: `src/main/resources/titular.mixins.json`
- Modify: `src/client/resources/titular.client.mixins.json`
- Delete: `src/main/java/titular/modid/mixin/ExampleMixin.java`
- Delete: `src/client/java/titular/modid/client/mixin/ExampleClientMixin.java`
- Create: `src/test/java/titular/modid/SmokeTest.java`

**Step 1: Write the failing test**

```java
class SmokeTest {
    @Test void junitIsAvailable() {
        assertEquals("titular", Titular.MOD_ID);
    }
}
```

**Step 2: Run test to verify it fails**

Run: `.\gradlew.bat test --tests titular.modid.SmokeTest`

Expected: FAIL because JUnit Platform is not configured.

**Step 3: Write minimal implementation**

Add JUnit Jupiter, `useJUnitPlatform()`, LuckPerms repository/`compileOnly`, correct mod metadata, and remove example mixins from configs.

**Step 4: Run test to verify it passes**

Run: `.\gradlew.bat test --tests titular.modid.SmokeTest`

Expected: PASS.

**Step 5: Checkpoint**

Run: `.\gradlew.bat build`

Expected: BUILD SUCCESSFUL.

### Task 2: 建立领域模型与富文本 codec

**Files:**
- Create: `src/main/java/titular/modid/model/DisplayMode.java`
- Create: `src/main/java/titular/modid/model/PermissionLevel.java`
- Create: `src/main/java/titular/modid/model/TitleDefinition.java`
- Create: `src/main/java/titular/modid/model/GroupDefinition.java`
- Create: `src/main/java/titular/modid/model/PlayerTitleState.java`
- Create: `src/main/java/titular/modid/model/TitularSettings.java`
- Create: `src/main/java/titular/modid/model/TitularData.java`
- Create: `src/main/java/titular/modid/codec/TextJsonCodec.java`
- Test: `src/test/java/titular/modid/codec/TextJsonCodecTest.java`
- Test: `src/test/java/titular/modid/model/ModelDefaultsTest.java`

**Step 1: Write failing codec and default tests**

Test a Text containing red/bold and blue/italic siblings, empty defaults, defensive collection copies, and nullable IDs.

```java
Text decoded = TextJsonCodec.decode(TextJsonCodec.encode(source));
assertEquals(TextJsonCodec.encode(source), TextJsonCodec.encode(decoded));
```

**Step 2: Verify RED**

Run: `.\gradlew.bat test --tests "titular.modid.codec.*" --tests "titular.modid.model.*"`

Expected: FAIL because models/codecs do not exist.

**Step 3: Implement minimal immutable records and codec**

Use ordered `List`/`LinkedHashMap` copies. Treat missing collections as empty and default mode as `PREFIX`. The text codec uses a bounded literal styled-run schema (text/color/bold/italic/underlined/strikethrough/extra) and rejects registry-backed interactive components; this keeps plain JUnit and server startup independent of Minecraft registry bootstrap.

**Step 4: Verify GREEN**

Run the same targeted test command; expected PASS.

### Task 3: 实现 JSON 存储与损坏文件保护

**Files:**
- Create: `src/main/java/titular/modid/storage/TitularStorage.java`
- Create: `src/main/java/titular/modid/storage/StorageException.java`
- Create: `src/main/java/titular/modid/storage/JsonTitularStorage.java`
- Create: `src/main/java/titular/modid/storage/TitularJsonCodec.java`
- Test: `src/test/java/titular/modid/storage/JsonTitularStorageTest.java`

**Step 1: Write failing tests**

Cover empty first load, exact four files, round trip, replacement save, and malformed `groups.json` backup.

```java
TitularData data = storage.load();
assertTrue(data.titles().isEmpty());
assertTrue(Files.exists(root.resolve("titles.json")));
```

**Step 2: Verify RED**

Run: `.\gradlew.bat test --tests titular.modid.storage.JsonTitularStorageTest`

Expected: FAIL because storage is missing.

**Step 3: Implement minimal storage**

Parse each document explicitly with Gson `JsonObject`; save through sibling temporary files and `Files.move(..., REPLACE_EXISTING, ATOMIC_MOVE)` with a non-atomic fallback. On first-load corruption rename the source to `.broken-<timestamp>` before writing defaults.

**Step 4: Verify GREEN**

Run the targeted storage test; expected PASS.

### Task 4: 实现继承解析和称号可用池

**Files:**
- Create: `src/main/java/titular/modid/service/TitlePoolResolver.java`
- Create: `src/main/java/titular/modid/service/TitularService.java`
- Create: `src/main/java/titular/modid/service/MutationResult.java`
- Test: `src/test/java/titular/modid/service/TitlePoolResolverTest.java`
- Test: `src/test/java/titular/modid/service/TitularServiceActivationTest.java`

**Step 1: Write failing inheritance tests**

Cover one level, multiple levels, duplicate IDs, missing titles, missing groups, cycles, primary/extra/LuckPerms groups, and extra titles.

```java
assertEquals(List.of("child", "shared", "parent", "extra"), resolver.resolve(state, data));
```

**Step 2: Verify RED**

Run: `.\gradlew.bat test --tests "titular.modid.service.TitlePoolResolverTest"`

Expected: FAIL because resolver is missing.

**Step 3: Implement resolver**

Traverse each single-parent chain with a per-chain visited set and a global insertion-ordered title set. Warn and skip invalid references.

**Step 4: Verify resolver GREEN**

Run the resolver tests; expected PASS.

**Step 5: Write failing activation tests**

Test valid activation, unavailable ID rejection, clear activation, and invalid active title becoming visually empty.

**Step 6: Implement service mutation transaction**

Create a candidate immutable data object, save it, then replace in-memory data and increment revision. Storage failure leaves state unchanged.

**Step 7: Verify GREEN**

Run: `.\gradlew.bat test --tests "titular.modid.service.*"`

Expected: PASS.

### Task 5: 实现三级权限和管理操作

**Files:**
- Create: `src/main/java/titular/modid/permission/PermissionResolver.java`
- Create: `src/main/java/titular/modid/permission/VanillaPermissionResolver.java`
- Extend: `src/main/java/titular/modid/service/TitularService.java`
- Test: `src/test/java/titular/modid/permission/PermissionResolverTest.java`
- Test: `src/test/java/titular/modid/service/TitularServiceManagementTest.java`

**Step 1: Write failing tests**

Verify PLAYER, OP2/`titular.admin`, OP4/`titular.superadmin`, superadmin inheritance, self-only admin group change, arbitrary offline player changes, group/title CRUD, referenced group deletion rejection, title deletion cleanup, and settings changes.

**Step 2: Verify RED**

Run: `.\gradlew.bat test --tests "titular.modid.permission.*" --tests "titular.modid.service.TitularServiceManagementTest"`

Expected: FAIL.

**Step 3: Implement permission and management methods**

Every public mutation accepts actor identity plus resolved `PermissionLevel`; no packet handler mutates maps directly.

**Step 4: Verify GREEN**

Run the same tests; expected PASS.

### Task 6: 实现统一格式化器

**Files:**
- Create: `src/main/java/titular/modid/format/TitularFormatter.java`
- Test: `src/test/java/titular/modid/format/TitularFormatterTest.java`

**Step 1: Write failing tests**

Test `PREFIX`、`SUFFIX`、`BOTH`、空称号、多段样式，以及原始名字的内容/样式未变。

```java
MutableText formatted = TitularFormatter.format(original, title, DisplayMode.BOTH);
assertEquals(originalJson, TextJsonCodec.encode(original));
assertEquals(expectedJson, TextJsonCodec.encode(formatted));
```

**Step 2: Verify RED**

Run: `.\gradlew.bat test --tests titular.modid.format.TitularFormatterTest`

Expected: FAIL.

**Step 3: Implement minimal formatter**

Build a fresh root and append copied prefix/name/suffix components; never mutate inputs.

**Step 4: Verify GREEN**

Run targeted test; expected PASS.

### Task 7: 定义权限裁剪快照与网络 codec

**Files:**
- Create: `src/main/java/titular/modid/network/ClientSnapshot.java`
- Create: `src/main/java/titular/modid/network/OnlineDisplayEntry.java`
- Create: `src/main/java/titular/modid/network/TitularPackets.java`
- Create: `src/main/java/titular/modid/network/SnapshotCodec.java`
- Create: `src/main/java/titular/modid/network/RequestCodec.java`
- Create: `src/main/java/titular/modid/network/TitularRequest.java`
- Test: `src/test/java/titular/modid/network/PacketCodecTest.java`
- Test: `src/test/java/titular/modid/network/SnapshotProjectionTest.java`

**Step 1: Write failing round-trip and projection tests**

Ensure PLAYER snapshots omit management data, ADMIN gets group IDs only, and SUPERADMIN gets full definitions/player states.

**Step 2: Verify RED**

Run: `.\gradlew.bat test --tests "titular.modid.network.*"`

Expected: FAIL.

**Step 3: Implement explicit PacketByteBuf codecs**

Bound all collection and string lengths. Reject unknown operation discriminators and oversized rich text before service invocation.

**Step 4: Verify GREEN**

Run network tests; expected PASS.

### Task 8: 接入服务端生命周期、命令和请求处理

**Files:**
- Modify: `src/main/java/titular/modid/Titular.java`
- Create: `src/main/java/titular/modid/server/TitularServerRuntime.java`
- Create: `src/main/java/titular/modid/server/TitularCommand.java`
- Create: `src/main/java/titular/modid/server/ServerNetworking.java`
- Create: `src/main/java/titular/modid/server/SnapshotBroadcaster.java`
- Create: `src/main/java/titular/modid/server/PlayerIdentityResolver.java`
- Test: `src/test/java/titular/modid/server/ServerRequestHandlerTest.java`

**Step 1: Write failing handler tests**

Use fake service/broadcaster boundaries to verify forged admin requests, stale revisions, unknown offline players, successful save then broadcast, join sync, and reload behavior.

**Step 2: Verify RED**

Run targeted server handler tests; expected FAIL.

**Step 3: Implement server wiring**

Load storage on server start, register `/titular`, Fabric join/disconnect/server-stop callbacks, receivers, and per-recipient snapshot projection. The implementation uses `TitularServerRuntime`, `ServerNetworking`, and `ServerRequestHandler`; `/titular` sends an open-screen packet followed by the latest snapshot.

**Step 4: Verify GREEN**

Run server unit tests and `.\gradlew.bat classes`; expected PASS.

### Task 9: 接入 LuckPerms 软依赖

**Files:**
- Create: `src/main/java/titular/modid/permission/LuckPermsFacade.java`
- Create: `src/main/java/titular/modid/permission/LuckPermsIntegration.java`
- Create: `src/main/java/titular/modid/permission/NoLuckPermsFacade.java`
- Extend: `src/main/java/titular/modid/server/TitularServerRuntime.java`
- Test: `src/test/java/titular/modid/permission/LuckPermsGroupSyncTest.java`

**Step 1: Write failing diff tests**

Verify only `luckPermsGroups` changes, manual groups remain, missing Titular group IDs are ignored, identical updates do not save/broadcast, and permission nodes map to the correct tier.

**Step 2: Verify RED**

Run targeted LuckPerms tests; expected FAIL.

**Step 3: Implement isolated integration**

Instantiate `LuckPermsIntegration` only after the loader confirms the mod is present. Subscribe to user data recalculation, schedule Minecraft state writes on the server thread, and unsubscribe on stop.

**Step 4: Verify GREEN**

Run tests and `.\gradlew.bat classes`; expected PASS without LuckPerms installed at runtime.

### Task 10: 实现客户端镜像和三处显示接入

**Files:**
- Modify: `src/client/java/titular/modid/client/TitularClient.java`
- Create: `src/client/java/titular/modid/client/ClientTitularState.java`
- Create: `src/client/java/titular/modid/client/ClientNetworking.java`
- Create: `src/client/java/titular/modid/client/mixin/LivingEntityRendererMixin.java`
- Create: `src/client/java/titular/modid/client/mixin/PlayerListHudMixin.java`
- Create: `src/main/java/titular/modid/mixin/ChatDisplayNameMixin.java`
- Modify: `src/main/resources/titular.mixins.json`
- Modify: `src/client/resources/titular.client.mixins.json`
- Test: `src/test/java/titular/modid/format/DisplayEntryEquivalenceTest.java`

**Step 1: Write failing equivalence test**

Create one fixture and assert chat-name, head-name, and Tab-name adapters all delegate to `TitularFormatter` and return equivalent Text JSON.

**Step 2: Verify RED**

Run targeted test; expected FAIL.

**Step 3: Implement state and thin adapters**

Replace snapshots atomically on the client thread. Mixins only obtain the raw name and UUID, read the current display entry, and call the formatter. The server chat mixin changes only the display-name component in message parameters.

**Step 4: Verify mappings and GREEN**

Run: `.\gradlew.bat test --tests titular.modid.format.DisplayEntryEquivalenceTest`

Run: `.\gradlew.bat compileJava compileClientJava`

Expected: all PASS; adjust mixin targets to actual Yarn 1.20.4 signatures if compilation or Mixin validation identifies a mismatch.

### Task 11: 实现富文本编辑文档和控件

**Files:**
- Create: `src/client/java/titular/modid/client/editor/StyledTextDocument.java`
- Create: `src/client/java/titular/modid/client/editor/RichTextEditorWidget.java`
- Create: `src/client/java/titular/modid/client/editor/FormattingToolbar.java`
- Test: `src/test/java/titular/modid/client/editor/StyledTextDocumentTest.java`

**Step 1: Write failing document tests**

Cover mouse-selection-compatible half-open ranges, replace selection, backspace/delete, colored/style toggles, reset, and adjacent run merge.

**Step 2: Verify RED**

Run targeted editor model tests; expected FAIL.

**Step 3: Implement pure document model**

Keep selection indices clamped and normalized. Convert to/from Text by grouping adjacent equal `Style` values.

**Step 4: Verify document GREEN**

Run tests; expected PASS.

**Step 5: Implement widget and toolbar**

Use `DrawContext`, Minecraft text measurement, mouse drag selection, familiar formatting buttons, color swatches and tooltips. Do not render format codes.

**Step 6: Compile client**

Run: `.\gradlew.bat compileClientJava`

Expected: PASS.

### Task 12: 实现玩家、管理员和高级管理员界面

**Files:**
- Create: `src/client/java/titular/modid/client/screen/TitularScreen.java`
- Create: `src/client/java/titular/modid/client/screen/TitleSelectionPanel.java`
- Create: `src/client/java/titular/modid/client/screen/SelfGroupPanel.java`
- Create: `src/client/java/titular/modid/client/screen/PlayerManagementPanel.java`
- Create: `src/client/java/titular/modid/client/screen/GroupManagementPanel.java`
- Create: `src/client/java/titular/modid/client/screen/TitleManagementPanel.java`
- Create: `src/client/java/titular/modid/client/screen/SettingsPanel.java`

**Step 1: Add screen state tests where logic is separable**

Test permission-to-tab projection, selection retention across snapshot revisions, and request generation without a live client.

**Step 2: Verify RED, implement state, verify GREEN**

Run the targeted tests around each state class.

**Step 3: Build complete controls**

Player page supports selection/clear and preview. Admin page supports own primary group. Superadmin pages support offline player lookup, all player group/title fields, group/title CRUD and display mode. Disable save until local validation passes; surface server rejection messages without closing the screen.

**Step 4: Compile client**

Run: `.\gradlew.bat compileClientJava`

Expected: PASS.

### Task 13: 完成重载、文档和端到端验证

**Files:**
- Modify: `README.md`
- Modify: `src/main/resources/fabric.mod.json`
- Modify as needed: runtime/network/screen files from prior tasks

**Step 1: Write any regression tests found during integration**

Add a failing test before each correction.

**Step 2: Run full automated verification**

Run: `.\gradlew.bat test`

Run: `.\gradlew.bat build`

Run: `git diff --check` only if Git metadata becomes available.

Expected: all tests PASS and BUILD SUCCESSFUL with no compiler or Mixin warnings caused by Titular.

**Step 3: Development smoke test**

Run a development server and client. Verify first-run empty files, `/titular`, no-title state, player selection, OP2 self group change, OP4 full management, offline player edit, reload, and immediate snapshot refresh.

Verify chat, head name and Tab all show identical prefix/suffix behavior while commands and signed message contents retain the original player name/message.

**Step 4: LuckPerms smoke test**

Run once without LuckPerms and once with LuckPerms. Verify both permission nodes and a changed LP group mirror, including preservation of manual extra groups.

**Step 5: Final review**

Inspect changed files for unrelated template churn, unsafe packet bounds, client-only classes in common code, direct storage writes outside `TitularService`, and missing service-side permission checks.

## Execution Notes

The user selected execution in the current session. The repository currently has no `.git`, so worktree creation and task commits are unavailable. Keep all edits scoped to `J:\mc\titular`, preserve unrelated files, and use the checkpoints above instead of commit checkpoints.
