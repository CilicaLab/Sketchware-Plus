## Pipeline
- a.a.a.ProjectBuilder orchestrates compile. a.a.a.Ix writes AndroidManifest.xml. a.a.a.Ox writes layout XML (Ox#b()). a.a.a.Jx writes activity Java (Jx#generateCode(boolean, String)). a.a.a.Lx writes components. a.a.a.qq (mod.jbk.build.BuiltInLibraries) resolves libraries. a.a.a.yq resolves file paths.

## View/Layout system
When using UPDATE_LAYOUT_FULL_XML, generate or modify standard Android XML as requested by the user, without touching the root layout — it's system-managed and handled automatically by Sketchware Plus.

## Custom views
Never create, modify, or invent a reference to a custom view. Custom views are created and managed exclusively by Sketchware Plus and the user (via the "Add Custom View" flow) — the AI must not generate a custom view tag/reference that doesn't already exist in the project.

## Libraries
- Registry: BuiltInLibraries.KNOWN_BUILT_IN_LIBRARIES. Enable via ManageLibraryActivity → jC.c(sc_id).k(). Extraction via BuiltInLibraries.extractCompileAssets(). AI must only reference libraries that exist in KNOWN_BUILT_IN_LIBRARIES — never invent a Maven dependency, this app only supports the built-in set.

## Java injection (NOT via raw Jx edits)
- Fields/init: Jx#extraVariables() for opCodes "addCustomVariable" / "addInitializer". Imports: EventsHandler "Import" event, wrapped in the exact marker comment format already used (do not alter the marker string). Hooks pulled via LogicHandler.base() / LogicHandler.imports(), called from Jx.generateCode() (line 257). AI must generate injections in this block/event format, NEVER as raw text pasted into generated Java — Jx will overwrite raw edits on next build.

## Targeted View Editing (Preferred for small changes)
- Model: ViewBean#id (unique per-layout), ViewBean#parent (id of parent or "root"), ViewBean#index (position in parent).
- Find: No global lookup. Loop `jC.a(sc_id).d(xmlName)` to find by `id`.
- Modify: Field edits (e.g. `viewBean.text.text = "value"`) are immediate. Persist via `jC.a(sc_id).n(path)` + `ViewEditor#a(ArrayList)`.
- Insert: `jC.a(sc_id).a(xmlName, new ViewBean(id, type))` appends to list. Set `parent` and `index` first. Unique ID check mandatory (mirror `ViewEditor.java:976`).
- Delete: `jC.a(sc_id).b(xmlName, targetBean)` returns list of target + recursive children. Delete each via `jC.a(sc_id).a(projectFile, bean)` iterating backwards (mirror `ViewEditor.java:454`).
- Whitelist: Only use attributes from `sketchware.plus.utility.AttributeConstants`.

## Automatic Component Lifecycle
- Mechanism: Adding a `ComponentBean` to the project data via `eC#a(javaName, type, id, param1)` is sufficient. This works for BOTH built-in components and Custom Components added by the user.
- Side Effects: 
    - `mq.java` automatically resolves and adds required Java imports during build.
    - `Jx.java` automatically generates the private field declaration and the initialization code inside `initializeLogic()`.
    - `Hx.java` automatically makes the component's event listeners (e.g. `onResponse` for `RequestNetwork`) available in the Logic Editor.
- AI Action: Use `ADD_COMPONENT` with the correct `typeName` for custom components or standard name for built-in ones. The assistant can now detect custom components in the project context.

## Hard rules for the AI
- Never suggest editing a.a.a.Jx/Ox/Ix/Lx/yq/eC output format directly — always go through the documented insertion points above.
- Layout Consistency: For all layout modifications, use `UPDATE_LAYOUT_FULL_XML` and provide a complete Android XML structure. Rely on `SketchwareXmlBridge` for synchronization.
- Root Layout Protection: Do not attempt to modify the system-managed root layout (usually a LinearLayout with id="root"). The bridge protects this; focus edits on the children.
- Never invent a class, method, or library that isn't confirmed to exist in this file — say "I'm not sure this exists in Sketchware Plus" instead of guessing.
- Every layout change must be valid ViewBeanParser input (reuse the existing attribute whitelist).
- Every custom view reference must have a real ProjectFileBean created first.
- Every library reference must be from KNOWN_BUILT_IN_LIBRARIES only.
- Every Java addition must go through Jx's block/event injection points, never raw text insertion.

## Programmatic actions (no UI/Activity needed)
- Custom view: jC.b(sc_id).d.add(new ProjectFileBean(...)) → jC.b(sc_id).j() → jC.b(sc_id).l() (persists to mysc/<sc_id>/file). Pair with eC.c.put(xmlName, new ArrayList<ViewBean>()) + dataManager.n(path) to attach a layout.
- Library: jC.c(sc_id).c(libraryBean) → jC.c(sc_id).k() (persists to mysc/<sc_id>/library). Only from KNOWN_BUILT_IN_LIBRARIES.
- Import: jC.a(sc_id).a(activityJavaName, EventBean.EVENT_TYPE_ACTIVITY, 0, "", "Import") to create the event, then add a BlockBean with the import string into eC.d, then save. Never write import text directly into generated Java.
- Manifest permission: jq.addPermission(String permission), then Ix.a() + yq.a("AndroidManifest.xml", content) persists. Launcher activity: AndroidManifestInjector.setLauncherActivity(sc_id, name). Custom manifest attributes: AndroidManifestInjector, backed by Injection/androidmanifest/attributes.json.
- AI must NEVER hand-write raw AndroidManifest.xml text and save it directly — always go through jq / AndroidManifestInjector so Ix stays the single source of truth on next build.
