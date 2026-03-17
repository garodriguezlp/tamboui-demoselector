///usr/bin/env jbang "$0" "$@" ; exit $?

//JAVA 17

//COMPILE_OPTIONS -encoding UTF-8
//RUNTIME_OPTIONS -Dfile.encoding=UTF-8

//REPOS central

// Only needed for snapshot versions
//REPOS central-portal-snapshots

//DEPS dev.tamboui:tamboui-toolkit:LATEST
//DEPS dev.tamboui:tamboui-jline3-backend:LATEST

/*
 * Copyright TamboUI Contributors
 * SPDX-License-Identifier: MIT
 */
package dev.tamboui.demo;

import static dev.tamboui.toolkit.Toolkit.column;
import static dev.tamboui.toolkit.Toolkit.dock;
import static dev.tamboui.toolkit.Toolkit.list;
import static dev.tamboui.toolkit.Toolkit.panel;
import static dev.tamboui.toolkit.Toolkit.text;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import dev.tamboui.layout.Constraint;
import dev.tamboui.style.Color;
import dev.tamboui.style.Overflow;
import dev.tamboui.toolkit.app.ToolkitApp;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.elements.ListElement;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.bindings.Actions;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;

/**
 * Architecture overview:
 * This is an explicit MVC implementation adapted to TamboUI's render loop.
 * Model: DemoSelectorModel owns all mutable app state and derived list state.
 * View: DemoSelectorView renders purely from model state.
 * Controller: DemoSelectorController translates key events into model mutations.
 *
 * Unlike listener-driven console MVC demos, TamboUI redraws after handled events.
 * Flow is: key event -> controller updates model -> EventResult.HANDLED -> framework calls render().
 */
public class DemoSelectorRefactored extends ToolkitApp {

    private final DemoSelectorModel model;
    private final DemoSelectorView view;
    private final DemoSelectorController controller;

    private DemoSelectorRefactored() {
        this.model = new DemoSelectorModel();
        this.view = new DemoSelectorView(model);
        this.controller = new DemoSelectorController(model, this::quit);
    }

    /**
     * Demo entry point.
     * @param args CLI arguments
     * @throws Exception on unexpected error
     */
    public static void main(String[] args) throws Exception {
        var selector = new DemoSelectorRefactored();
        selector.run();

        if (selector.model.selectedDemo() != null) {
            System.out.println(selector.model.selectedDemo());
            System.exit(0);
        } else {
            System.exit(1);
        }
    }

    @Override
    protected void onStart() {
        model.initialize();
    }

    @Override
    protected Element render() {
        return view.render(controller::handle);
    }
}

final class DemoSelectorModel {

    private final ListElement<?> demoList;
    private final Map<String, List<DemoInfo>> demosByModule = new TreeMap<>();
    private final Set<String> expandedModules = new HashSet<>();
    private final List<DisplayItem> displayItems = new ArrayList<>();

    private String filter = "";
    private String selectedDemo = null;

    DemoSelectorModel() {
        demoList = list()
            .highlightSymbol("> ")
            .highlightColor(Color.YELLOW)
            .autoScroll()
            .scrollbar()
            .scrollbarThumbColor(Color.CYAN);
    }

    void initialize() {
        initializeStaticDemos();
        expandedModules.addAll(demosByModule.keySet());
        rebuildDisplayList();
        if (!displayItems.isEmpty()) {
            demoList.selected(findFirstSelectable());
        }
    }

    ListElement<?> demoList() {
        return demoList;
    }

    List<DisplayItem> displayItems() {
        return displayItems;
    }

    String filter() {
        return filter;
    }

    String selectedDemo() {
        return selectedDemo;
    }

    int listSize() {
        return displayItems.size();
    }

    int selectedIndex() {
        return demoList.selected();
    }

    void selectIndex(int index) {
        demoList.selected(index);
    }

    String title() {
        if (filter.isEmpty()) {
            return "Demos (" + totalDemoCount() + ")";
        }
        return "Filter: " + filter;
    }

    Color listBorderColor() {
        return filter.isEmpty() ? Color.WHITE : Color.YELLOW;
    }

    DisplayItem selectedItem() {
        var idx = selectedIndex();
        if (idx >= 0 && idx < displayItems.size()) {
            return displayItems.get(idx);
        }
        return null;
    }

    Element descriptionContent() {
        var selected = selectedItem();
        if (selected == null) {
            return text("");
        }
        if (selected.demo() != null) {
            return demoDescription(selected.demo());
        }
        return moduleDescription(selected);
    }

    private Element demoDescription(DemoInfo demo) {
        var tags = demo.tags();
        var tagsLine = tags.isEmpty() ? "" : "Tags: " + String.join(", ", tags);
        return column(
            text(tagsLine).magenta().overflow(Overflow.WRAP_WORD),
            text(""),
            text(demo.description()).overflow(Overflow.WRAP_WORD)
        );
    }

    private Element moduleDescription(DisplayItem selected) {
        var count = demosByModule.get(selected.module()).size();
        var hint = selected.expanded()
            ? "Press ← or Enter to collapse."
            : "Press → or Enter to expand.";

        return column(
            text(selected.module()).bold().cyan().length(1),
            text(""),
            text(count + " demo" + (count != 1 ? "s" : "") + " in this module.").length(1),
            text(""),
            text(hint).dim().length(1)
        );
    }

    boolean hasFilter() {
        return !filter.isEmpty();
    }

    void clearFilter() {
        filter = "";
        refreshAfterFilterChange();
    }

    void backspaceFilter() {
        filter = filter.substring(0, filter.length() - 1);
        refreshAfterFilterChange();
    }

    void appendFilter(char c) {
        filter += c;
        refreshAfterFilterChange();
    }

    void toggleModule(String module) {
        if (expandedModules.contains(module)) {
            expandedModules.remove(module);
        } else {
            expandedModules.add(module);
        }

        var current = selectedItem();
        var currentModule = current != null ? current.module() : null;

        rebuildDisplayList();

        if (currentModule != null) {
            for (var i = 0; i < displayItems.size(); i++) {
                var item = displayItems.get(i);
                if (item.demo() == null && item.module().equals(currentModule)) {
                    demoList.selected(i);
                    return;
                }
            }
        }
        demoList.selected(findFirstSelectable());
    }

    void chooseDemoAndQuit(DemoInfo demo, Runnable quitAction) {
        selectedDemo = demo.name();
        quitAction.run();
    }

    void moveToNextModuleHeader() {
        for (int i = selectedIndex() + 1; i < listSize(); i++) {
            if (displayItems.get(i).demo() == null) {
                demoList.selected(i);
                return;
            }
        }
    }

    void moveToPreviousModuleHeader() {
        for (int i = selectedIndex() - 1; i >= 0; i--) {
            if (displayItems.get(i).demo() == null) {
                demoList.selected(i);
                return;
            }
        }
    }

    void moveToParentModuleHeader() {
        for (int i = selectedIndex() - 1; i >= 0; i--) {
            if (displayItems.get(i).demo() == null) {
                demoList.selected(i);
                return;
            }
        }
    }

    void moveToFirstChildIfPresent() {
        var current = selectedIndex();
        if (current + 1 < listSize() && displayItems.get(current + 1).demo() != null) {
            demoList.selected(current + 1);
        }
    }

    int findFirstSelectable() {
        return 0;
    }

    private int totalDemoCount() {
        return demosByModule.values().stream().mapToInt(List::size).sum();
    }

    private void refreshAfterFilterChange() {
        rebuildDisplayList();
        demoList.selected(findFirstSelectable());
    }

    private void rebuildDisplayList() {
        displayItems.clear();
        var lowerFilter = filter.toLowerCase(Locale.ROOT);

        for (var entry : demosByModule.entrySet()) {
            var module = entry.getKey();
            var demos = entry.getValue();
            var filteredDemos = filteredDemos(demos, lowerFilter);

            if (filteredDemos.isEmpty() && !filter.isEmpty()) {
                continue;
            }

            var expanded = expandedModules.contains(module);
            var demoCount = filter.isEmpty() ? demos.size() : filteredDemos.size();
            displayItems.add(new DisplayItem(module, null, expanded, demoCount));

            if (expanded) {
                for (var demo : filteredDemos) {
                    displayItems.add(new DisplayItem(module, demo, false, 0));
                }
            }
        }
    }

    private List<DemoInfo> filteredDemos(List<DemoInfo> demos, String lowerFilter) {
        if (filter.isEmpty()) {
            return demos;
        }

        return demos.stream()
            .filter(d -> matchesFilter(d, lowerFilter))
            .toList();
    }

    private boolean matchesFilter(DemoInfo demo, String lowerFilter) {
        return demo.displayName().toLowerCase(Locale.ROOT).contains(lowerFilter)
            || demo.name().toLowerCase(Locale.ROOT).contains(lowerFilter)
            || demo.description().toLowerCase(Locale.ROOT).contains(lowerFilter)
            || demo.tags().stream().anyMatch(t -> t.toLowerCase(Locale.ROOT).contains(lowerFilter));
    }

    private void initializeStaticDemos() {
        demosByModule.clear();

        addDemo("Core", "hello-world", "Hello World", "Minimal text rendering and basic layout.",
            "intro", "text", "layout");
        addDemo("Core", "keyboard-events", "Keyboard Events", "Captures key events and shows action mapping.",
            "input", "keyboard", "actions");

        addDemo("Widgets", "form-playground", "Form Playground", "Simple form fields with validation hints.",
            "forms", "input", "validation");
        addDemo("Widgets", "table-view", "Table View", "Scrollable table with row highlighting.",
            "table", "scroll", "list");

        addDemo("Navigation", "panel-docking", "Panel Docking", "Multi-panel docking layout and focus changes.",
            "layout", "dock", "focus");
    }

    private void addDemo(String module, String name, String displayName, String description, String... tags) {
        var demoInfo = new DemoInfo(name, displayName, description, module, Set.of(tags));
        demosByModule.computeIfAbsent(module, k -> new ArrayList<>()).add(demoInfo);
    }
}

final class DemoSelectorView {

    private final DemoSelectorModel model;

    DemoSelectorView(DemoSelectorModel model) {
        this.model = model;
    }

    Element render(java.util.function.Function<KeyEvent, EventResult> keyHandler) {
        return dock()
            .top(headerPanel())
            .left(listPanel(keyHandler), Constraint.percentage(50))
            .center(descriptionPanel())
            .bottom(footerPanel());
    }

    private Element headerPanel() {
        return panel(text(" TamboUI Demo Selector ").bold().cyan())
            .rounded()
            .borderColor(Color.CYAN);
    }

    private Element listPanel(java.util.function.Function<KeyEvent, EventResult> keyHandler) {
        return panel(model.demoList().items(displayLines()))
            .title(model.title())
            .rounded()
            .borderColor(model.listBorderColor())
            .id("demo-list")
            .focusable()
            .onKeyEvent(keyHandler::apply);
    }

    private List<String> displayLines() {
        List<String> lines = new ArrayList<>();
        for (var item : model.displayItems()) {
            lines.add(item.toDisplayString());
        }
        return lines;
    }

    private Element descriptionPanel() {
        return panel(model.descriptionContent())
            .title("Description")
            .rounded()
            .borderColor(Color.DARK_GRAY);
    }

    private Element footerPanel() {
        return panel(text(" Type: Filter | ←/→: Collapse/Expand | ↑↓: Navigate | PgUp/PgDn: Sections | Enter: Select | Ctrl+C: Quit ").dim())
            .rounded()
            .borderColor(Color.DARK_GRAY);
    }
}

final class DemoSelectorController {

    private static final boolean KEY_TRACE_ENABLED = Boolean.parseBoolean(
        System.getProperty("demo.selector.trace", "true"));
    private static final Path KEY_TRACE_FILE = Path.of(
        System.getProperty("demo.selector.trace.file", "demo-selector-keys.log"));

    private final DemoSelectorModel model;
    private final Runnable quitAction;

    DemoSelectorController(DemoSelectorModel model, Runnable quitAction) {
        this.model = model;
        this.quitAction = quitAction;
    }

    EventResult handle(KeyEvent event) {
        var actionCancel = event.matches(Actions.CANCEL);
        var actionDeleteBackward = event.matches(Actions.DELETE_BACKWARD);
        var isRawBackspace = isRawBackspace(event);
        var eventKind = describeEventKind(event, actionCancel, actionDeleteBackward, isRawBackspace);
        traceKeyEvent(event, "IN", eventKind);

        if (event.matches(Actions.PAGE_DOWN)) {
            model.moveToNextModuleHeader();
            return EventResult.HANDLED;
        }

        if (event.matches(Actions.PAGE_UP)) {
            model.moveToPreviousModuleHeader();
            return EventResult.HANDLED;
        }

        if (event.matches(Actions.MOVE_LEFT)) {
            return handleMoveLeft();
        }

        if (event.matches(Actions.MOVE_RIGHT)) {
            return handleMoveRight();
        }

        var isPlainSpace = isPlainSpace(event);
        if (event.matches(Actions.SELECT) && !isPlainSpace) {
            return handleSelect();
        }
        if (event.matches(Actions.SELECT) && isPlainSpace) {
            traceKeyEvent(event, "SELECT_IGNORED_SPACE", eventKind);
        }

        if (actionCancel && model.hasFilter()) {
            model.clearFilter();
            model.selectIndex(model.findFirstSelectable());
            traceKeyEvent(event, "CANCEL_CLEAR", eventKind);
            return EventResult.HANDLED;
        }

        if (isDeleteLikeEvent(event, actionDeleteBackward, isRawBackspace) && model.hasFilter()) {
            model.backspaceFilter();
            traceKeyEvent(event, "BACKSPACE_APPLIED", eventKind);
            return EventResult.HANDLED;
        }

        if (isFilterCharacter(event)) {
            model.appendFilter(event.character());
            traceKeyEvent(event, "FILTER_APPEND", eventKind);
            return EventResult.HANDLED;
        }

        traceKeyEvent(event, "UNHANDLED", eventKind);
        return EventResult.UNHANDLED;
    }

    private EventResult handleMoveLeft() {
        var selected = model.selectedItem();
        if (selected != null) {
            if (selected.demo() == null) {
                if (selected.expanded()) {
                    model.toggleModule(selected.module());
                }
            } else {
                model.moveToParentModuleHeader();
            }
        }
        return EventResult.HANDLED;
    }

    private EventResult handleMoveRight() {
        var selected = model.selectedItem();
        if (selected != null && selected.demo() == null) {
            if (!selected.expanded()) {
                model.toggleModule(selected.module());
            } else {
                model.moveToFirstChildIfPresent();
            }
        }
        return EventResult.HANDLED;
    }

    private EventResult handleSelect() {
        var selected = model.selectedItem();
        if (selected != null) {
            if (selected.demo() == null) {
                model.toggleModule(selected.module());
            } else {
                model.chooseDemoAndQuit(selected.demo(), quitAction);
            }
        }
        return EventResult.HANDLED;
    }

    private boolean isFilterCharacter(KeyEvent event) {
        if (event.code() != KeyCode.CHAR || event.hasCtrl() || event.hasAlt()) {
            return false;
        }
        var c = event.character();
        return Character.isLetterOrDigit(c) || c == '-' || c == '_' || c == ' ';
    }

    private boolean isPlainSpace(KeyEvent event) {
        return event.code() == KeyCode.CHAR
            && event.character() == ' '
            && !event.hasCtrl()
            && !event.hasAlt();
    }

    private boolean isRawBackspace(KeyEvent event) {
        return event.code() == KeyCode.CHAR && (event.character() == '\b' || event.character() == '\u007f');
    }

    private boolean isDeleteLikeEvent(KeyEvent event, boolean actionDeleteBackward, boolean isRawBackspace) {
        if (actionDeleteBackward || isRawBackspace) {
            traceDeleteCheck(event, true, "ACTION_OR_RAW");
            return true;
        }

        var isCtrlH = event.code() == KeyCode.CHAR
            && event.hasCtrl()
            && (event.character() == 'h' || event.character() == 'H');
        if (isCtrlH) {
            traceDeleteCheck(event, true, "CTRL_H");
            return true;
        }

        var codeName = String.valueOf(event.code());
        if (codeName.contains("BACKSPACE") || codeName.contains("DELETE")) {
            traceDeleteCheck(event, true, "CODE_NAME");
            return true;
        }

        var fallback = event.code() == KeyCode.CHAR
            && !event.hasCtrl()
            && !event.hasAlt()
            && Character.isISOControl(event.character())
            && event.character() != '\u001b';
        traceDeleteCheck(event, fallback, fallback ? "CTRL_CHAR_FALLBACK" : "NO_MATCH");
        return fallback;
    }

    private String describeEventKind(
        KeyEvent event,
        boolean actionCancel,
        boolean actionDeleteBackward,
        boolean isRawBackspace) {
        if (actionCancel) {
            return "CANCEL";
        }
        if (actionDeleteBackward || isRawBackspace) {
            return "DELETE";
        }
        if (event.code() == KeyCode.CHAR
            && event.hasCtrl()
            && (event.character() == 'h' || event.character() == 'H')) {
            return "DELETE";
        }
        if (event.code() == KeyCode.CHAR) {
            return "CHAR";
        }
        return String.valueOf(event.code());
    }

    private void traceKeyEvent(KeyEvent event, String stage, String eventKind) {
        if (!KEY_TRACE_ENABLED) {
            return;
        }

        var ch = event.character();
        var line = String.format(
            "stage=%s kind=%s code=%s charCode=%d char='%s' ctrl=%s alt=%s shift=%s filter='%s'%n",
            stage,
            eventKind,
            event.code(),
            (int) ch,
            printableChar(ch),
            event.hasCtrl(),
            event.hasAlt(),
            event.hasShift(),
            model.filter());
        try {
            Files.writeString(KEY_TRACE_FILE, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
            // Keep UI responsive even if debug logging fails.
        }
    }

    private void traceDeleteCheck(KeyEvent event, boolean matched, String reason) {
        if (!KEY_TRACE_ENABLED) {
            return;
        }
        var line = String.format(
            "stage=DELETE_CHECK matched=%s reason=%s code=%s charCode=%d char='%s' ctrl=%s alt=%s shift=%s filter='%s'%n",
            matched,
            reason,
            event.code(),
            (int) event.character(),
            printableChar(event.character()),
            event.hasCtrl(),
            event.hasAlt(),
            event.hasShift(),
            model.filter());
        try {
            Files.writeString(KEY_TRACE_FILE, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
            // Keep UI responsive even if debug logging fails.
        }
    }

    private String printableChar(char ch) {
        if (ch == '\b') {
            return "\\b";
        }
        if (ch == '\u007f') {
            return "\\u007f";
        }
        if (Character.isISOControl(ch)) {
            return "ctrl";
        }
        return Character.toString(ch);
    }
}

record DemoInfo(String name, String displayName, String description, String module, Set<String> tags) {
}

record DisplayItem(String module, DemoInfo demo, boolean expanded, int demoCount) {

    String toDisplayString() {
        if (demo == null) {
            var icon = expanded ? "▼" : "▶";
            return icon + " " + module + " (" + demoCount + ")";
        }
        return "    " + demo.displayName();
    }
}
