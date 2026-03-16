# DemoSelector.java Deep Guide

This document explains the app in [DemoSelector.java](DemoSelector.java) from three angles at once:

1. What the code does, step by step.
2. Which TamboUI abstractions it uses, and at what level.
3. How you could restructure it, still in a single file, into a clearer MVC-style design.

The goal is not just to understand this file. The goal is to give you the mental model you need to build your own TamboUI application confidently.

## 1. Executive Summary

This app is a full-screen terminal UI built with the highest-level full-screen API that TamboUI offers for this use case:

- `ToolkitApp` from `tamboui-toolkit`
- The declarative Toolkit DSL via `text()`, `panel()`, `dock()`, `column()`, `list()`
- Semantic input bindings via `Actions`

At runtime, the app behaves like this:

1. `main()` creates `DemoSelector` and calls `run()`.
2. `ToolkitApp.run()` creates a `ToolkitRunner` internally.
3. `onStart()` initializes the demo data and initial UI state.
4. `render()` is called to build the element tree for the current frame.
5. TamboUI renders that tree into an off-screen buffer.
6. TamboUI diffs the new buffer against the previous one and only writes changed cells to the terminal.
7. Key events are routed to the focused element.
8. If a handler returns `EventResult.HANDLED`, the toolkit requests a redraw.
9. `render()` runs again using the new state.

So this is immediate-mode in rendering model, but high-level in authoring style.

That combination is the key idea to keep in your head:

- You write a declarative view.
- You keep mutable application state in fields.
- TamboUI rebuilds the UI every frame from that state.
- Widgets are not a retained DOM.

## 2. What File You Are Looking At

This file is both:

- a runnable JBang script
- a Java class in package `dev.tamboui.demo`

The top header tells JBang how to run it:

```java
///usr/bin/env jbang "$0" "$@" ; exit $?

//JAVA 17
//DEPS dev.tamboui:tamboui-toolkit:LATEST
//DEPS dev.tamboui:tamboui-jline3-backend:LATEST
```

That means:

- Java 17
- toolkit module included
- JLine 3 backend included

Important consequence: this app is not using the Panama backend. It is using the JLine backend.

That matters for the key-handling discussion later, because some key encoding differences you observed are partly terminal-level and partly backend-level.

## 3. Where This Sits in TamboUI's API Stack

TamboUI documents several API layers:

1. Immediate Mode
2. TuiRunner
3. Toolkit DSL
4. Inline Display / Inline Toolkit for CLI-style preserved-scroll apps

This app uses the top full-screen layer: Toolkit DSL via `ToolkitApp`.

### 3.1 The layers, from lowest to highest

#### Immediate Mode

Lowest level. You manage:

- terminal setup
- raw mode
- alternate screen
- event loop
- frame drawing

You work with:

- `Terminal`
- `Frame`
- `Buffer`
- low-level widgets

Use this when you want maximum control.

#### TuiRunner

Middle level. TamboUI manages:

- terminal setup
- event loop
- polling
- resize handling
- redraw timing

You provide:

- an event handler
- a renderer

The event handler returns `true` or `false` to request redraw.

#### Toolkit DSL

Highest full-screen level. You describe UI declaratively with elements.

You work with:

- `ToolkitApp`
- `ToolkitRunner`
- `Element`
- fluent element factories like `panel()`, `text()`, `list()`, `dock()`

Events are routed automatically to elements. If an element handles an event, the toolkit redraws.

### 3.2 So is this low-level or high-level?

Both answers are true depending on which axis you mean.

#### High-level from the application author's point of view

This app is high-level because it uses:

- `ToolkitApp`
- declarative elements
- automatic focus/event routing
- semantic actions like `Actions.SELECT`

You are not manually polling terminal input or rendering cells yourself.

#### Still built on an immediate-mode engine underneath

This app is still immediate-mode internally because TamboUI's rendering model is immediate-mode:

- every frame is rebuilt from current state
- the full element tree is rendered again
- the terminal output is optimized by buffer diffing

So the authoring API is high-level, but the rendering model is immediate-mode.

That distinction explains a lot of the design in this file.

## 4. Inventory of TamboUI Abstractions Used Here

Here is every important TamboUI import in the file and how to classify it.

| Import | Role in this app | Layer | Abstraction level |
| --- | --- | --- | --- |
| `ToolkitApp` | Application base class | Toolkit | High |
| `Element` | Root UI node type returned by `render()` | Toolkit | High |
| `ListElement<?>` | Stateful list element wrapper | Toolkit | High |
| `EventResult` | Event routing result | Toolkit | High |
| `Toolkit.*` static factories | Declarative element DSL | Toolkit | High |
| `Actions` | Semantic action names | TUI bindings | Mid/high |
| `KeyEvent` | Actual input event object | TUI | Mid |
| `KeyCode` | Low-level key identity | TUI | Mid/low |
| `Constraint` | Layout sizing primitive | Core/layout | Mid |
| `Color` | Styling primitive | Core/style | Mid |
| `Overflow` | Text/layout behavior | Toolkit/Core styling | Mid |

### 4.1 High-level abstractions used

These are the most important high-level abstractions in this file:

- `ToolkitApp`
- `Element`
- `ListElement`
- `panel()`, `text()`, `column()`, `dock()`, `list()`
- `onKeyEvent(...)`
- `focusable()`
- `id(...)`

These are high-level because they let you think in terms of:

- app state
- view composition
- event handlers
- focusable widgets

instead of buffers and terminal cells.

### 4.2 Mid-level abstractions used

These are more infrastructural:

- `Actions`
- `KeyEvent`
- `KeyCode`
- `Constraint`
- `Overflow`

They expose more of the framework mechanics:

- how key routing works
- how layout sizing works
- how text wrapping works

### 4.3 Low-level abstractions not used directly

This app does not directly use the lowest-level TamboUI types such as:

- `Terminal`
- `Frame`
- `Buffer`
- `Cell`
- direct widget rendering into rectangles

Those are present underneath the toolkit layer, but not exposed in your app code.

## 5. How To Read This File Top To Bottom

If you are new to TamboUI, do not read it as a random list of methods. Read it in this order.

### 5.1 First: identify the state

The real heart of the app is not `render()`. It is the state fields.

```java
private final ListElement<?> demoList;
private final Map<String, List<DemoInfo>> demosByModule = new TreeMap<>();
private final Set<String> expandedModules = new HashSet<>();
private String filter = "";
private String selectedDemo = null;
private final List<DisplayItem> displayItems = new ArrayList<>();
```

These fields define the current truth of the application.

Interpret them like this:

- `demosByModule`: source domain data
- `expandedModules`: UI expansion state
- `filter`: search query state
- `selectedDemo`: result to print when exiting
- `displayItems`: derived view-model for the list
- `demoList`: toolkit-managed widget state for list selection and scrolling

This split is important.

Not all state in a Toolkit app belongs in plain Java fields, and not all state belongs inside toolkit elements.

This app uses both kinds:

#### Domain/UI state owned by the app

- filter text
- which modules are expanded
- chosen demo
- grouped demo data
- flattened display list

#### Widget state owned by the toolkit element

- selected row index in `demoList`
- scroll position in `demoList`

That is an example of a healthy TamboUI pattern: let stateful toolkit elements own their internal view mechanics when appropriate.

### 5.2 Second: find the lifecycle

The lifecycle is:

#### `main()`

Creates the app and runs it.

#### `onStart()`

Initializes data and first screen state.

#### `render()`

Builds the UI tree for the current frame.

#### `handleKey(...)`

Mutates state in response to user input.

#### `quit()` via `ToolkitApp`

Stops the runner.

This is the standard mental model for `ToolkitApp` apps.

### 5.3 Third: locate the derived state builder

`rebuildDisplayList()` is one of the most important methods in the file.

It transforms:

- grouped demos
- expanded/collapsed state
- current filter

into:

- the flat list shown on screen

That means it is a derived-state builder, effectively a mini view-model generator.

This is not the same thing as drawing. It is a preprocessing step that makes rendering simpler.

### 5.4 Fourth: read `render()` as a pure projection

Once you understand the state and derived state, `render()` becomes easy:

1. Convert `displayItems` into strings for the list.
2. Derive the title from `filter`.
3. Derive the description panel from current selection.
4. Compose the screen with a dock layout.

That is the right way to read TamboUI code: render is a state projection.

### 5.5 Fifth: read input handling as command dispatch

`handleKey()` is not just "keyboard code". It is the command layer of the app.

Each branch corresponds to a user command:

- next section
- previous section
- collapse
- expand
- toggle/select
- clear filter
- backspace filter
- append typed character

If you mentally rename `handleKey()` to `dispatchUserCommand()`, the architecture becomes easier to reason about.

## 6. Application State Breakdown

This section is crucial because TamboUI apps are easiest to understand when you classify state correctly.

### 6.1 Source data

`demosByModule`

This is the underlying data model for what can be shown.

It is loaded by `initializeStaticDemos()` and grouped by module.

### 6.2 UI control state

`expandedModules`

This controls visibility of child demo rows per module.

`filter`

This controls filtering logic.

`selectedDemo`

This stores the output result after user confirms a demo.

### 6.3 Derived presentation state

`displayItems`

This is a flattening of a hierarchical model into a linear list suitable for `ListElement`.

This is one of the strongest patterns in the file.

Instead of making the list widget understand hierarchy, the code:

1. keeps hierarchy in the domain state
2. computes a flat presentation model
3. renders that flattened model

That is a clean pattern and worth reusing.

### 6.4 Internal widget state

`demoList`

The docs explicitly describe `ListElement` as a stateful toolkit widget whose selection/scrolling it manages internally.

This is why the file uses:

- `demoList.selected()`
- `demoList.selected(index)`

instead of storing a separate `selectedIndex` field.

That is a design choice. It is valid, but it has tradeoffs:

- Pro: less boilerplate
- Pro: list behavior stays close to the widget
- Con: selection state is less explicit in your domain/controller model
- Con: testing application logic in isolation becomes harder

For small apps, this is fine. For larger apps, many teams prefer to move selection into a controller and push it into the widget during render.

## 7. The Main Architectural Pattern Already Present

Even though the file is monolithic, it already follows several good patterns.

### 7.1 Immediate-mode state projection

Pattern:

- mutate state in handlers
- rebuild derived state
- let next frame re-render from current truth

This is absolutely aligned with TamboUI's rendering model.

### 7.2 Derived view-model pattern

Pattern:

- keep a richer domain structure (`demosByModule`)
- derive a flattened presentation structure (`displayItems`)

This is a very good pattern for hierarchical UIs.

### 7.3 Stateful widget delegation

Pattern:

- let `ListElement` manage selection and scroll state

This matches the Toolkit DSL docs.

### 7.4 Declarative composition

Pattern:

- `render()` describes the screen tree declaratively
- layout and styling are chained fluently

This is standard Toolkit DSL usage.

### 7.5 Event-command pattern

Pattern:

- keyboard events map to application commands
- commands update state
- redraw follows automatically if handled

### 7.6 Event tracing for terminal compatibility issues

Pattern:

- when terminal encoding is ambiguous, instrument raw events and semantic matches

That is a pragmatic and correct debugging move for terminal software.

## 8. What `render()` Is Actually Doing

`render()` is the view layer, even though it lives inside the same class as the controller-ish code.

### 8.1 First it converts `displayItems` to list text

```java
List<String> lines = new ArrayList<>();
for (var item : displayItems) {
    lines.add(item.toDisplayString());
}
```

This is a presentation projection from a richer record to plain rendered lines.

### 8.2 Then it computes screen-local derived values

- title text
- selected item
- description panel content

These are view-derived values, not persistent app state.

That distinction is good.

Do not store everything as fields. Some values should exist only during render.

### 8.3 Then it builds a dock layout

The whole screen is composed with `dock()`:

- top header
- left demo list panel at `Constraint.percentage(50)`
- center description panel
- bottom footer

This is a high-level toolkit layout abstraction that ultimately maps to core layout constraints.

### 8.4 Then it configures interaction on the left panel

This line matters a lot conceptually:

```java
.id("demo-list")
.focusable()
.onKeyEvent(this::handleKey)
```

This is how the Toolkit DSL connects the visual element tree to event routing and focus management.

Meaning:

- `id("demo-list")`: registers a stable identity
- `focusable()`: participates in focus routing
- `onKeyEvent(...)`: receives key events when focused

That is pure Toolkit-level behavior, not low-level terminal handling.

## 9. The Real Event Flow in This App

This is one of the most important parts to understand.

### 9.1 What happens when a key is pressed

Conceptually:

1. The backend reads terminal input.
2. TamboUI converts it into a `KeyEvent`.
3. `TuiRunner` polls that event.
4. `ToolkitRunner` routes it through `EventRouter`.
5. The focused element's key handler is called.
6. Your `handleKey()` returns `HANDLED` or `UNHANDLED`.
7. If handled, ToolkitRunner requests redraw.
8. `render()` executes again.

### 9.2 Why redraw happens after `HANDLED`

The framework source makes this explicit.

At the TUI level, `TuiRunner` uses the boolean returned by the event handler to decide whether to render another frame.

At the Toolkit level, `ToolkitRunner.handleEvent(...)` routes the event to elements and returns `true` when the result is handled.

So in a Toolkit app:

- `EventResult.HANDLED` effectively means: this changed something important enough to redraw
- `EventResult.UNHANDLED` means: continue routing or skip redraw

### 9.3 Therefore: this app is not manually forcing redraw

This is a subtle but very important answer to your question.

The app does not explicitly call a redraw API anywhere in normal event handling.

It only:

- mutates Java state
- returns `EventResult.HANDLED`

Then the framework redraws.

So when you see methods like:

- `rebuildDisplayList()`
- `refreshAfterFilterChange()`
- `toggleModule()`

those methods are not repaint calls.

They are state update helpers.

The redraw itself is still happening "behind the scenes" via ToolkitRunner and TuiRunner.

### 9.4 Why it may feel less magical than Panama

It may feel less magical here for two reasons:

#### Reason 1: this file makes state recomputation explicit

The app manually recomputes `displayItems` after state changes. That can look like a UI refresh operation, but it is really just preparing the next frame's data.

#### Reason 2: you are using the JLine backend here, not Panama

The docs describe the same rendering model across backends, but input decoding can feel different in practice between backends and terminals.

The redraw model is not fundamentally different:

- event handled -> redraw requested
- next frame rebuilt
- buffer diff sent to terminal

What can differ is input behavior, timing feel, resize behavior, and terminal capability handling.

## 10. Redraw, Buffer Diffing, and Why Full Re-render Is Okay

If you are new to immediate-mode TUIs, full redraw can sound expensive. In TamboUI it is the intended model.

### 10.1 The model

TamboUI docs describe this pipeline:

1. Application state
2. Widgets/elements render to a buffer
3. Buffer is diffed
4. Only changed terminal cells are written

So the app is conceptually redrawing the whole UI every frame, but the terminal output is optimized.

### 10.2 What your app is responsible for

Your app is responsible for:

- keeping correct state
- making `render()` a correct projection of that state

TamboUI is responsible for:

- frame creation
- buffer management
- diffing
- terminal output
- event loop

### 10.3 What `rebuildDisplayList()` is in this model

It is not a paint call.

It is a view-model recomputation step. Think of it as:

```text
state -> derived list model -> render() -> element tree -> buffer -> terminal diff
```

### 10.4 When you would need more explicit redraw control

Mostly when updating UI state from background work.

For that, the relevant API is not "force redraw now" in your event handler. The pattern is:

1. schedule or start background work
2. call `runner().runOnRenderThread(...)`
3. mutate UI state on the render thread
4. let the runner redraw on the next event-loop iteration

That is the right model for timers, async tasks, downloads, or long-running operations.

## 11. Why the Special Keys Were Painful

This file contains excellent evidence that you already hit one of the hardest parts of terminal UI programming: terminals do not send idealized keyboard events.

### 11.1 Space is semantically overloaded

TamboUI bindings docs say `SELECT` typically maps to Enter and Space.

That means this condition is expected behavior:

```java
if (event.matches(Actions.SELECT))
```

The problem is your app also wants plain space to be text input for the filter.

Those two meanings conflict:

- semantic select action
- literal character input

Your code resolves that explicitly:

```java
var isPlainSpace = event.code() == KeyCode.CHAR
        && event.character() == ' '
        && !event.hasCtrl()
        && !event.hasAlt();
if (event.matches(Actions.SELECT) && !isPlainSpace) { ... }
```

That is a correct and pragmatic resolution.

Interpretation:

- treat Enter as selection
- do not let plain Space trigger select
- let Space remain usable as filter text

This is a real design choice, not a hack.

The root problem is semantic bindings are intentionally more abstract than raw key identity.

### 11.2 Backspace is messy because terminals are messy

Your code checks multiple possibilities:

- `Actions.DELETE_BACKWARD`
- raw BS (`\b`, code 8)
- raw DEL (`\u007f`, code 127)
- Ctrl+H
- key code names containing BACKSPACE or DELETE
- final fallback on control characters except ESC

That is not overengineering. It reflects terminal reality.

The bindings docs explain why terminals are difficult:

- many Ctrl combinations collapse to control characters
- some keys are ambiguous
- encoding depends on terminal and platform
- some backends or terminal stacks normalize differently

Backspace is one of the oldest and most inconsistent cases in terminal software.

### 11.3 Why your tracing code is justified

The logging methods:

- `traceKeyEvent(...)`
- `traceDeleteCheck(...)`
- `describeEventKind(...)`
- `printableChar(...)`

are doing the right kind of debugging for a TUI.

In GUI applications, input is usually already normalized.

In terminal applications, debugging often means asking:

- What byte sequence actually arrived?
- What key code did the backend infer?
- Did semantic binding match what I expected?
- Was this a character, a control char, or an action alias?

### 11.4 What I would keep as a design rule

For apps like yours:

- prefer semantic actions for navigation and standard commands
- drop to raw key inspection when you need literal text entry or terminal-specific compatibility

That is exactly what this file does.

It is the correct hybrid approach.

## 12. The Current Implementation Is Not Really MVC

This is the direct answer: no, the current file is not meaningfully MVC in the sense recommended by the TamboUI docs.

It contains MVC-like ingredients, but they are not separated.

### 12.1 What TamboUI authors recommend

The MVC guide recommends:

- Controller: owns state and commands
- View: pure function from controller state to `Element`
- Event handling: dispatches events to controller commands

The docs are explicit that the view should be a pure state-to-element transformation.

### 12.2 What this file currently is

It is closer to this:

- `DemoSelector` = app shell + controller + view + key handler + startup bootstrap + debug instrumentation

So it is a monolithic application object.

### 12.3 Why it still works fine

Because the app is small.

For a small file, this architecture is perfectly workable. The state is not huge, the commands are understandable, and the render logic is still readable.

The issue is not correctness. The issue is scale and clarity.

### 12.4 MVC-ish pieces that already exist

Even though it is not clean MVC, these pieces already hint in that direction:

#### Controller-ish pieces

- `toggleModule(...)`
- `refreshAfterFilterChange()`
- `rebuildDisplayList()`
- `initializeStaticDemos()`

#### View-ish pieces

- `render()`
- `DisplayItem.toDisplayString()`

#### Input-dispatch-ish pieces

- `handleKey(...)`

So the file is not far from MVC. It just has not been separated structurally.

## 13. A Better Single-File Architecture

Yes. You can absolutely improve the architecture while keeping everything in a single source file.

In fact, the TamboUI MVC guide's own examples are very compatible with a single-file approach using nested static classes.

### 13.1 Recommended single-file shape

I would structure it like this:

```java
public final class DemoSelector extends ToolkitApp {

    private final Controller controller = new Controller();
    private final View view = new View(controller);
    private final KeyHandler keyHandler = new KeyHandler(controller, this::quit);

    @Override
    protected void onStart() {
        controller.initialize();
    }

    @Override
    protected Element render() {
        return view.render().onKeyEvent(keyHandler::handle);
    }

    static final class Controller { ... }
    static final class View { ... }
    static final class KeyHandler { ... }

    record DemoInfo(...) { }
    record DisplayItem(...) { }
}
```

This stays single-file but separates responsibilities.

### 13.2 What goes into the controller

Put these in `Controller`:

- `Map<String, List<DemoInfo>> demosByModule`
- `Set<String> expandedModules`
- `String filter`
- `String selectedDemo`
- `List<DisplayItem> displayItems`
- possibly also selection index if you want full MVC control
- command methods like `toggleModule`, `clearFilter`, `appendFilterChar`, `backspaceFilter`, `initialize`
- query methods like `displayItems()`, `selectedDemo()`, `title()`, `descriptionData()`

The controller should own state transitions.

### 13.3 What goes into the view

Put these in `View`:

- creation/configuration of `ListElement`
- `render()`
- panel composition
- description panel construction
- line generation from `DisplayItem`

The view should read controller state and build elements.

### 13.4 What goes into the key handler

Put these in `KeyHandler`:

- event-to-command mapping
- special key normalization helpers
- trace/logging helpers

This class should decide:

- what command a key means
- which controller method to call
- whether the event was handled

### 13.5 Why this is better

Because it separates three different kinds of change:

- UI layout changes live in `View`
- application behavior changes live in `Controller`
- input interpretation changes live in `KeyHandler`

That is exactly the kind of separation you want once the app becomes more ambitious.

## 14. The Biggest Design Choice: Who Owns Selection?

This is the most important architectural decision if you refactor.

### Option A: keep selection inside `ListElement`

This is closest to the current code.

Pros:

- simpler
- less plumbing
- uses toolkit stateful widget as intended

Cons:

- controller is less pure
- selected item depends on view/widget state
- harder to unit test behavior outside toolkit

### Option B: move selection into controller

The controller owns:

- selected index
- maybe scroll state too, if needed later

The view just reflects that selection into the list element.

Pros:

- more explicit MVC
- easier testing
- clearer app logic for commands like section jumps and parent navigation

Cons:

- more code
- you are taking over logic that the widget can already manage

### My recommendation for your case

For a single-file app very similar to this one:

- keep `ListElement` for scroll behavior
- but strongly consider moving selected index to the controller if the app will grow

Why:

- current behavior already depends heavily on selection semantics
- hierarchical navigation is app logic, not merely widget logic
- a future "run selected demo", "preview selected demo", or multi-pane synchronized UI will be easier with explicit selection state

If you want to stay lightweight, you can also use a hybrid approach:

- keep selection in `ListElement` now
- structure code so moving it later is easy

## 15. A Concrete MVC-in-One-File Refactor Plan

If I were refactoring this without splitting files, I would do it in this order.

### Step 1: introduce nested `Controller`

Move into it:

- data fields
- derived state rebuild methods
- initialization logic
- domain records if you want

At this stage you do not need to change behavior.

### Step 2: introduce nested `View`

Move into it:

- `render()` logic
- description panel rendering
- title logic
- display line generation

Pass controller into the view.

### Step 3: introduce nested `KeyHandler`

Move into it:

- `handleKey(...)`
- special key normalization helpers
- trace helpers

Pass controller plus a quit callback.

### Step 4: keep `DemoSelector` as composition root only

Then `DemoSelector` becomes very small:

- build controller/view/key handler
- call controller.initialize in `onStart()`
- render via view
- quit when needed

That is the cleanest single-file architecture for this app.

## 16. Example Skeleton for a Single-File MVC Version

This is not a full rewrite, just a structural sketch of what I would aim for.

```java
public final class DemoSelector extends ToolkitApp {

    private final Controller controller = new Controller();
    private final View view = new View(controller);
    private final KeyHandler keyHandler = new KeyHandler(controller, this::quit);

    public static void main(String[] args) throws Exception {
        var app = new DemoSelector();
        app.run();
        System.out.println(app.controller.selectedDemoOrNull());
    }

    @Override
    protected void onStart() {
        controller.initializeStaticDemos();
        controller.expandAllModules();
        controller.rebuildDisplayList();
    }

    @Override
    protected Element render() {
        return view.render(keyHandler::handle);
    }

    static final class Controller {
        private final Map<String, List<DemoInfo>> demosByModule = new TreeMap<>();
        private final Set<String> expandedModules = new HashSet<>();
        private final List<DisplayItem> displayItems = new ArrayList<>();
        private String filter = "";
        private String selectedDemo;

        List<DisplayItem> displayItems() {
            return List.copyOf(displayItems);
        }

        String filter() {
            return filter;
        }

        void appendFilter(char c) {
            filter += c;
            rebuildDisplayList();
        }

        void backspaceFilter() {
            if (!filter.isEmpty()) {
                filter = filter.substring(0, filter.length() - 1);
                rebuildDisplayList();
            }
        }

        void toggleModule(String module) { ... }
        void chooseDemo(String name) { selectedDemo = name; }
        String selectedDemoOrNull() { return selectedDemo; }
        void rebuildDisplayList() { ... }
        void initializeStaticDemos() { ... }
        void expandAllModules() { expandedModules.addAll(demosByModule.keySet()); }
    }

    static final class View {
        private final Controller controller;
        private final ListElement<?> demoList = list()
            .highlightSymbol("> ")
            .highlightColor(Color.YELLOW)
            .autoScroll()
            .scrollbar()
            .scrollbarThumbColor(Color.CYAN);

        View(Controller controller) {
            this.controller = controller;
        }

        Element render(java.util.function.Function<KeyEvent, EventResult> handler) {
            return dock()
                .top(...)
                .left(panel(demoList.items(lines()))
                    .id("demo-list")
                    .focusable()
                    .onKeyEvent(handler::apply), Constraint.percentage(50))
                .center(...)
                .bottom(...);
        }

        private List<String> lines() { ... }
    }

    static final class KeyHandler {
        private final Controller controller;
        private final Runnable quitAction;

        KeyHandler(Controller controller, Runnable quitAction) {
            this.controller = controller;
            this.quitAction = quitAction;
        }

        EventResult handle(KeyEvent event) {
            // semantic navigation
            // raw key compatibility fallbacks
            // controller commands
            return EventResult.UNHANDLED;
        }
    }

    record DemoInfo(String name, String displayName, String description,
                    String module, Set<String> tags) { }

    record DisplayItem(String module, DemoInfo demo, boolean expanded, int demoCount) { }
}
```

That structure stays single-file, keeps JBang friendliness, and is much closer to the TamboUI MVC guide.

## 17. Is MVC Recommended by the Authors for This Kind of App?

Yes.

The TamboUI docs explicitly present MVC as the recommended application structure for maintainable Toolkit apps.

Their model is slightly different from classic GUI MVC terminology because the view is literally a function that returns an `Element`, but the idea is standard:

- controller owns state and commands
- view is pure state-to-UI
- event handler dispatches user intent to controller

That maps very well to an app like yours.

### 17.1 Is your current implementation already MVC?

No, not really.

A fair classification would be:

- "single-class stateful Toolkit app with some MVC tendencies"

That is not criticism. It is a perfectly common first shape for a small TUI.

But if your goal is to become expert and build a more serious app, I would not keep growing it in the current monolithic form.

## 18. What Internal TamboUI Mechanics Matter Most Here

If you want to become expert, these are the internal mechanics you should understand first.

### 18.1 Immediate-mode render cycle

Each frame:

1. toolkit asks your app for the root `Element`
2. element tree renders into a frame/buffer
3. toolkit registers elements for focus and event routing
4. terminal diff is emitted

The important idea:

- elements do not persist as a retained widget tree in the way a browser DOM does
- state lives in your fields and in explicit widget state objects

### 18.2 Event routing

ToolkitRunner maintains:

- a focus manager
- an event router
- an element registry

During render, focusable and interactive elements are registered.

During input handling, the router uses that registration plus focus state to decide where the event should go.

### 18.3 Focus management

This app makes the left panel focusable and gives it an ID.

That is the required Toolkit pattern for keyboard focus.

Without `id()` and `focusable()`, keyboard routing would not work the same way.

### 18.4 Stateful element internals

`ListElement` is important because it stores some UI state between frames.

This is one of the places where TamboUI is not "pure function only" in the strictest sense. The render tree is rebuilt, but certain element instances can own persistent widget state.

That is why this app constructs `demoList` once in the constructor and reuses it.

If you recreated `demoList` from scratch every frame, you would likely lose selection/scroll continuity.

That is a very important TamboUI idiom.

### 18.5 Event result to redraw bridge

Toolkit event handling boils down to redraw-or-not.

At lower levels:

- `TuiRunner` event handlers return boolean redraw requests

At Toolkit level:

- routed `EventResult.HANDLED` becomes redraw requested

That is the bridge between application behavior and rendering.

## 19. How To Think About This App in MVC Terms

Here is the clean conceptual decomposition of the existing file.

### Model

Strictly speaking, there is only a very light domain model:

- `DemoInfo`
- grouped demos by module

### Controller

Current controller responsibilities are spread across methods such as:

- `initializeStaticDemos()`
- `rebuildDisplayList()`
- `toggleModule(...)`
- `refreshAfterFilterChange()`
- parts of `handleKey(...)`

### View

Current view responsibilities are:

- `render()`
- `DisplayItem.toDisplayString()`
- description panel branching logic

### Input dispatcher

Current input-dispatch responsibilities are:

- `handleKey(...)`
- delete/backspace compatibility helpers
- trace helpers

In a larger app, I would treat input dispatcher as its own collaborator even if you still call the overall structure MVC.

## 20. Specific Strengths of the Current File

This file is actually better than many first-pass TUIs. These parts are solid.

### 20.1 Good use of semantic actions

It uses `Actions.PAGE_DOWN`, `MOVE_LEFT`, `MOVE_RIGHT`, `SELECT`, `CANCEL`, `DELETE_BACKWARD` instead of hardcoding raw keys everywhere.

That is correct TamboUI style.

### 20.2 Good fallback to raw-key handling where semantics are not enough

This is especially good around:

- backspace handling
- plain-space suppression from select semantics

### 20.3 Good separation between source data and flattened display list

That is a reusable pattern for tree-like views.

### 20.4 Correct reuse of a stateful `ListElement`

Creating the list element once in the constructor is the right shape for a stateful toolkit widget.

### 20.5 Good explicit preservation of selection context when toggling modules

The code tries to stay on the same module header after rebuilding.

That is good UX logic and the kind of app-specific behavior you should keep in a controller.

## 21. Specific Weaknesses or Pressure Points

These are not bugs. They are architectural stress points.

### 21.1 One class owns too many responsibilities

Current responsibilities include:

- startup
- state
- rendering
- input dispatch
- compatibility handling
- logging
- domain data setup

That makes the file harder to extend safely.

### 21.2 `displayItems` is mutable shared derived state

This is not inherently wrong, but it means the controller and view are tightly coupled through a mutable list.

If you refactor, consider making the controller the only writer and the view a read-only consumer.

### 21.3 Selection state is split between app logic and widget logic

Commands such as section jumping and parent navigation depend on a mix of:

- app-owned `displayItems`
- widget-owned selection index

That is workable, but not as explicit as it could be.

### 21.4 The filter editing logic is custom text-input logic

That is okay for a small POC, but if the filtering field becomes richer later, you may want a dedicated text-input state/widget instead of manual character accumulation.

## 22. What This Means for the App You Want To Build

You said you want to build a CLI/TUI app very similar to this and become expert in TamboUI.

Here is the most practical takeaway.

### 22.1 If the app is full-screen and interactive like this

Use:

- `ToolkitApp`
- Toolkit DSL
- semantic bindings for navigation
- explicit controller state
- a derived view-model layer for any hierarchical or filtered data

This is the right level for your kind of app.

### 22.2 If the app is more like npm/Gradle style progress inside normal terminal flow

Use:

- `InlineApp` or inline toolkit

That preserves scroll history and is better for status/progress tools.

### 22.3 If you need unusual behavior later

Drop to:

- `ToolkitRunner` for more control
- `TuiRunner` if you need custom event loop behavior
- immediate mode only if you truly need buffer-level control

For now, you should stay at Toolkit level.

## 23. Expert Mental Model To Keep

If you remember only a few things, remember these.

### 23.1 TamboUI is immediate-mode underneath

Do not think:

- "update this widget in place"

Think:

- "update state, then next frame renders the correct UI"

### 23.2 Toolkit is declarative but not magical

It hides:

- event loop
- focus routing
- redraw scheduling
- terminal diffing

But it still follows predictable rules.

### 23.3 `EventResult.HANDLED` is not just propagation control

In practice it also means:

- this event changed UI-relevant state
- redraw the next frame

### 23.4 Stateful elements should be reused across frames

If an element owns UI state, construct it once and keep it in a field.

That is why `demoList` is a field.

### 23.5 Terminal input is not a clean abstract stream

Always be ready for:

- backend differences
- terminal differences
- ambiguous control characters
- semantic-action conflicts with literal text input

## 24. Recommended Improvements Without Leaving a Single File

If you want the highest-value changes while keeping one file, I would do these.

### 24.1 Extract nested static `Controller`

Highest value architectural improvement.

### 24.2 Extract nested static `View`

Makes `render()` easier to reason about and test mentally.

### 24.3 Extract nested static `KeyHandler`

Especially valuable because your key compatibility logic is nontrivial.

### 24.4 Keep `DemoInfo` and `DisplayItem` as records

Those are already good fits.

### 24.5 Optionally introduce a small view-model record

For example:

```java
record DescriptionModel(String title, List<String> lines) { }
```

This can reduce branching noise in `render()`.

### 24.6 Consider moving selection into controller later if the app grows

Not mandatory now, but likely beneficial later.

## 25. Final Verdict

### Is this app using TamboUI correctly?

Yes.

It uses the Toolkit DSL in a way that matches the documented model.

### Is it high-level or low-level?

High-level API usage, immediate-mode rendering model underneath.

### Is it MVC as recommended by the authors?

Not really. It is a monolithic Toolkit app with MVC-like parts.

### Can it be architected better while staying a single file?

Yes, absolutely. A nested `Controller` + `View` + `KeyHandler` structure would be the best next step.

### Is redraw being manually forced here?

Not in the normal sense. The app mutates state and returns `HANDLED`; ToolkitRunner/TuiRunner then redraw the next frame automatically.

### Are the special-key workarounds justified?

Yes. They reflect real terminal and binding ambiguities, especially around Backspace and Space.

## 26. Suggested Next Learning Path

If your goal is to become expert and build a similar app, study in this order:

1. The Toolkit DSL mental model: state -> `render()` -> element tree.
2. The TamboUI MVC guide: controller, pure view, event dispatcher.
3. Bindings and actions: semantic keys vs raw keys.
4. Stateful toolkit elements: lists, tables, inputs, focus.
5. TuiRunner: redraw rules, render thread, ticks, background updates.
6. Core concepts: buffer, frame, diffing, layout constraints.
7. Inline toolkit, only if your future app should preserve scroll history.

If you internalize those seven topics, you will stop treating TamboUI as mysterious and start treating it as a predictable rendering/event system.
