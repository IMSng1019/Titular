# Titular GUI、语言与富文本编辑器实施计划

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 将 `/titular` 改造成权限感知的引导式界面，支持客户端本地中文/英文切换，并提供以当前编辑玩家真实用户名为中心的前缀/后缀富文本编辑器。

**Architecture:** 保留服务端权威的 `ClientSnapshot`、修订号请求和权限校验；客户端新增 locale 管理器与引导路由，现有管理面板作为子页面复用。富文本仍由 `StyledTextDocument` 表示，新增纯组合模型和只读用户名预览，称号数据继续通过现有 `TitleDefinition`/网络 codec 保存。

**Tech Stack:** Java 17、Fabric 1.20.4/Yarn、Minecraft GUI widgets、Gson、JUnit 5、现有 Fabric Loom split client source set。

---

### Task 1: 客户端 locale 管理与双语资源

**Files:**
- Create: `src/client/java/titular/modid/client/ClientLocale.java`
- Create: `src/client/java/titular/modid/client/ClientLanguageManager.java`
- Create: `src/client/java/titular/modid/client/ClientText.java`
- Create: `src/test/java/titular/modid/client/ClientLanguageManagerTest.java`
- Modify: `src/main/resources/assets/titular/lang/en_us.json`
- Create: `src/main/resources/assets/titular/lang/zh_cn.json`

**Step 1: Write the failing tests**

Cover default English, setting Chinese, unknown locale fallback, malformed/absent config fallback, and round-trip persistence through an injected temporary path. Also verify `%s` arguments are interpolated and missing keys fall back to English.

**Step 2: Run tests to verify failure**

Run: `\.\gradlew.bat test --tests titular.modid.client.ClientLanguageManagerTest`

Expected: FAIL because locale classes do not exist.

**Step 3: Implement the minimal locale layer**

Use an enum with stable wire-independent IDs (`en_us`, `zh_cn`). Load bundled JSON maps through the class loader, keep English as the fallback map, and persist only the selected locale in `config/titular-client.json` (or an injected path in tests). Expose `ClientText.text(key, args...)` returning `Text.literal` so changing locale does not depend on Minecraft's global language option.

**Step 4: Run the focused tests**

Run the same command; expected PASS.

**Step 5: Commit**

`git add src/client/java/titular/modid/client src/test/java/titular/modid/client/ClientLanguageManagerTest.java src/main/resources/assets/titular/lang && git commit -m "feat: add local client language support"`

### Task 2: 引导页路由与权限投影

**Files:**
- Modify: `src/main/java/titular/modid/client/TitularScreenState.java`
- Modify: `src/test/java/titular/modid/client/TitularScreenStateTest.java`
- Create: `src/client/java/titular/modid/client/screen/LandingAction.java`

**Step 1: Add failing projection tests**

Assert that `PLAYER` exposes only switch-title and language actions, `ADMIN` adds select-primary-group, and `SUPERADMIN` adds manage-title. Assert route state starts at HOME and preserves the selected title/tab when a snapshot revision changes.

**Step 2: Run the focused state tests**

Run: `\.\gradlew.bat test --tests titular.modid.client.TitularScreenStateTest`

Expected: FAIL for the new HOME/action APIs.

**Step 3: Implement pure routing helpers**

Add a `Page` enum (`HOME`, `TITLE_SWITCH`, `PRIMARY_GROUP`, `MANAGEMENT`, `LANGUAGE`) and a permission-filtered action list. Keep the existing `Tab` enum and old request helpers for compatibility; do not put permission checks in client-only code beyond hiding projected actions.

**Step 4: Re-run state tests**

Expected: PASS, including all existing assertions.

**Step 5: Commit**

`git add src/main/java/titular/modid/client/TitularScreenState.java src/client/java/titular/modid/client/screen/LandingAction.java src/test/java/titular/modid/client/TitularScreenStateTest.java && git commit -m "feat: model guided titular screen routes"`

### Task 3: 重构屏幕入口和本地语言设置面板

**Files:**
- Modify: `src/client/java/titular/modid/client/screen/TitularScreen.java`
- Create: `src/client/java/titular/modid/client/screen/LandingPanel.java`
- Create: `src/client/java/titular/modid/client/screen/LanguageSettingsPanel.java`
- Modify: `src/client/java/titular/modid/client/screen/TitleSelectionPanel.java`
- Modify: `src/client/java/titular/modid/client/screen/SelfGroupPanel.java`
- Modify: `src/client/java/titular/modid/client/screen/SettingsPanel.java`

**Step 1: Implement the HOME route and navigation skeleton**

Make `/titular` initialize on HOME. Render three stable-width action buttons using `LandingAction` and a language gear/button for every permission level. Route buttons to the existing title, primary-group, and superadmin management panels; add a translated Back button to every child route.

**Step 2: Implement local language controls**

`LanguageSettingsPanel` displays English/中文 as a two-option segmented control. On selection, call `ClientLanguageManager.setLocale`, persist immediately, clear the old error, and ask `TitularScreen` to rebuild. Do not send a C2S packet. Keep server display-mode controls in the superadmin management settings panel and label them distinctly.

**Step 3: Migrate visible labels to `ClientText`**

Replace hard-coded `Text.literal`/`Text.translatable` labels in touched panels with the locale helper, while preserving server error strings verbatim. Use constrained widths and vertical scrolling where the screen is narrower than the management form.

**Step 4: Compile the client**

Run: `\.\gradlew.bat compileClientJava`

Expected: PASS with no GUI API errors.

**Step 5: Commit**

`git add src/client/java/titular/modid/client/screen && git commit -m "feat: add guided titular landing screen"`

### Task 4: 实名中心预览与富文本称号编辑

**Files:**
- Create: `src/client/java/titular/modid/client/editor/TitlePreview.java`
- Create: `src/client/java/titular/modid/client/editor/TitlePreviewWidget.java`
- Create: `src/test/java/titular/modid/client/editor/TitlePreviewTest.java`
- Modify: `src/client/java/titular/modid/client/screen/TitleManagementPanel.java`
- Modify: `src/client/java/titular/modid/client/editor/RichTextEditorWidget.java`
- Modify: `src/client/java/titular/modid/client/editor/FormattingToolbar.java`

**Step 1: Write the failing preview tests**

Given styled prefix/suffix documents and a username, assert the composed `Text` order is prefix, exact username, suffix; assert username has no editor style mutation and empty sides are valid. Test name resolution preference: online raw name, current session name for self, UUID fallback.

**Step 2: Run focused tests**

Run: `\.\gradlew.bat test --tests titular.modid.client.editor.TitlePreviewTest`

Expected: FAIL because the preview model is missing.

**Step 3: Implement the pure preview model**

Add `TitlePreview.compose(Text prefix, String username, Text suffix)` that copies each component into a fresh root. Add a small name resolver that reads `ClientSnapshot.onlinePlayers()` and falls back safely without touching server state.

**Step 4: Add the GUI composition**

Update the superadmin title editor to show prefix editor on the left, a read-only actual username in the center, and suffix editor on the right, with a live preview line. Keep the existing selection semantics: color, bold, italic, underline, strikethrough, and reset apply to the focused editor's selection; no format codes are rendered. Ensure toolbar child buttons remain clickable after relayout.

**Step 5: Run tests and compile**

Run: `\.\gradlew.bat test --tests titular.modid.client.editor.*` and `\.\gradlew.bat compileClientJava`; expected PASS.

**Step 6: Commit**

`git add src/client/java/titular/modid/client/editor src/client/java/titular/modid/client/screen/TitleManagementPanel.java src/test/java/titular/modid/client/editor/TitlePreviewTest.java && git commit -m "feat: add real-name rich title preview"`

### Task 5: 集成检查与回归修正

**Files:**
- Modify as needed: touched screen, locale, resource, and test files.

**Step 1: Run the complete automated suite**

Run: `\.\gradlew.bat test`

Expected: all existing and new tests pass.

**Step 2: Build the mod**

Run: `\.\gradlew.bat build`

Expected: BUILD SUCCESSFUL, including remapped client classes and both language resources.

**Step 3: Inspect the diff**

Run: `git diff --check` and review `git status --short`. Confirm no server packet accepts a client locale, no client-only class leaked into common source, and no permission-sensitive management action is merely client-gated.

**Step 4: Smoke-test the interaction**

Run a development client/server pair and verify `/titular` opens HOME, each permission sees the correct actions, locale changes immediately and survives restart, primary-group selection changes the available title list, and the editor shows the real player name with independently styled prefix/suffix.

