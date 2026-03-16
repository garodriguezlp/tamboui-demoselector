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

import static dev.tamboui.toolkit.Toolkit.*;

/**
 * Interactive TUI demo selector with collapsible module groups.
 */
public class DemoSelector extends ToolkitApp {

    private static final boolean KEY_TRACE_ENABLED = Boolean.parseBoolean(
        System.getProperty("demo.selector.trace", "true"));
    private static final Path KEY_TRACE_FILE = Path.of(
        System.getProperty("demo.selector.trace.file", "demo-selector-keys.log"));

    private final ListElement<?> demoList;
    private final Map<String, List<DemoInfo>> demosByModule = new TreeMap<>();
    private final Set<String> expandedModules = new HashSet<>();
    private String filter = "";
    private String selectedDemo = null;

    // Display list state
    private final List<DisplayItem> displayItems = new ArrayList<>();

    private DemoSelector() {
        demoList = list()
            .highlightSymbol("> ")
            .highlightColor(Color.YELLOW)
            .autoScroll()
            .scrollbar()
            .scrollbarThumbColor(Color.CYAN);
    }

    /**
     * Demo entry point.
     * @param args the CLI arguments
     * @throws Exception on unexpected error
     */
    public static void main(String[] args) throws Exception {
        var selector = new DemoSelector();
        selector.run();

        if (selector.selectedDemo != null) {
            System.out.println(selector.selectedDemo);
            System.exit(0);
        } else {
            System.exit(1);
        }
    }

    @Override
    protected void onStart() {
        initializeStaticDemos();
        // Expand all modules by default
        expandedModules.addAll(demosByModule.keySet());
        rebuildDisplayList();
        if (!displayItems.isEmpty()) {
            demoList.selected(findFirstSelectable());
        }
    }

    /**
     * Rebuilds the display list based on current filter and expanded state.
     */
    private void rebuildDisplayList() {
        displayItems.clear();
        var lowerFilter = filter.toLowerCase(Locale.ROOT);

        for (var entry : demosByModule.entrySet()) {
            var module = entry.getKey();
            var demos = entry.getValue();

            // Filter demos
            var filteredDemos = filter.isEmpty() ? demos : demos.stream()
                    .filter(d -> d.displayName().toLowerCase(Locale.ROOT).contains(lowerFilter)
                            || d.name().toLowerCase(Locale.ROOT).contains(lowerFilter)
                            || d.description().toLowerCase(Locale.ROOT).contains(lowerFilter)
                            || d.tags().stream().anyMatch(t -> t.toLowerCase(Locale.ROOT).contains(lowerFilter)))
                    .toList();

            if (filteredDemos.isEmpty() && !filter.isEmpty()) {
                continue; // Skip empty groups when filtering
            }

            // Add module header
            var expanded = expandedModules.contains(module);
            var demoCount = filter.isEmpty() ? demos.size() : filteredDemos.size();
            displayItems.add(new DisplayItem(module, null, expanded, demoCount));

            // Add demos if expanded
            if (expanded) {
                for (var demo : filteredDemos) {
                    displayItems.add(new DisplayItem(module, demo, false, 0));
                }
            }
        }
    }

    /**
     * Returns the currently selected item.
     */
    private DisplayItem selectedItem() {
        var idx = demoList.selected();
        if (idx >= 0 && idx < displayItems.size()) {
            return displayItems.get(idx);
        }
        return null;
    }

    /**
     * Returns the total demo count across all groups.
     */
    private int totalDemoCount() {
        return demosByModule.values().stream().mapToInt(List::size).sum();
    }

    @Override
    protected Element render() {
        List<String> lines = new ArrayList<>();
        for (var item : displayItems) {
            lines.add(item.toDisplayString());
        }

        var title = filter.isEmpty()
                ? "Demos (" + totalDemoCount() + ")"
                : "Filter: " + filter;

        var selected = selectedItem();

        // Build description panel content based on selection
        Element descriptionContent;
        if (selected != null && selected.demo() != null) {
            var tags = selected.demo().tags();
            var tagsLine = tags.isEmpty() ? "" : "Tags: " + String.join(", ", tags);
            descriptionContent = column(
                    text(tagsLine).magenta().overflow(Overflow.WRAP_WORD),
                    text(""),
                    text(selected.demo().description()).overflow(Overflow.WRAP_WORD)
            );
        } else if (selected != null) {
            // Module header selected
            var count = demosByModule.get(selected.module()).size();
            descriptionContent = column(
                    text(selected.module()).bold().cyan().length(1),
                    text(""),
                    text(count + " demo" + (count != 1 ? "s" : "") + " in this module.").length(1),
                    text(""),
                    text(selected.expanded() ? "Press ← or Enter to collapse." : "Press → or Enter to expand.")
                            .dim().length(1)
            );
        } else {
            descriptionContent = text("");
        }

        return dock()
                // Header
                .top(panel(
                        text(" TamboUI Demo Selector ").bold().cyan()
                ).rounded().borderColor(Color.CYAN))

                // Demo list (left side)
                .left(panel(
                        demoList.items(lines)
                )
                        .title(title)
                        .rounded()
                        .borderColor(filter.isEmpty() ? Color.WHITE : Color.YELLOW)
                        .id("demo-list")
                        .focusable()
                        .onKeyEvent(this::handleKey), Constraint.percentage(50))

                // Description panel (center/right)
                .center(panel(descriptionContent)
                        .title("Description")
                        .rounded()
                        .borderColor(Color.DARK_GRAY))

                // Footer
                .bottom(panel(
                        text(" Type: Filter | ←/→: Collapse/Expand | ↑↓: Navigate | PgUp/PgDn: Sections | Enter: Select | Ctrl+C: Quit ").dim()
                ).rounded().borderColor(Color.DARK_GRAY));
    }

    private EventResult handleKey(KeyEvent event) {
        var listSize = displayItems.size();
        var current = demoList.selected();
        var actionCancel = event.matches(Actions.CANCEL);
        var actionDeleteBackward = event.matches(Actions.DELETE_BACKWARD);

        // Note: Basic navigation (UP/DOWN/HOME/END) is now handled automatically
        // by ListElement via ContainerElement forwarding. Only custom behavior
        // (section jumping, collapse/expand, filtering) needs manual handling.

        // Some terminals send backspace as DEL (127), others as BS (8), and some
        // map it through action bindings only; trace all events to diagnose.
        var isRawBackspace = event.code() == KeyCode.CHAR && (event.character() == '\b' || event.character() == '\u007f');
        var eventKind = describeEventKind(event, actionCancel, actionDeleteBackward, isRawBackspace);
        traceKeyEvent(event, "IN", eventKind);

        // PAGE_DOWN: Jump to next section
        if (event.matches(Actions.PAGE_DOWN)) {
            for (int i = current + 1; i < listSize; i++) {
                if (displayItems.get(i).demo() == null) {
                    demoList.selected(i);
                    break;
                }
            }
            return EventResult.HANDLED;
        }

        // PAGE_UP: Jump to previous section
        if (event.matches(Actions.PAGE_UP)) {
            for (int i = current - 1; i >= 0; i--) {
                if (displayItems.get(i).demo() == null) {
                    demoList.selected(i);
                    break;
                }
            }
            return EventResult.HANDLED;
        }

        // Left: collapse current group or go to parent
        if (event.matches(Actions.MOVE_LEFT)) {
            var selected = selectedItem();
            if (selected != null) {
                if (selected.demo() == null) {
                    // On module header - collapse if expanded
                    if (selected.expanded()) {
                        toggleModule(selected.module());
                    }
                } else {
                    // On demo - go to parent module header
                    for (var i = current - 1; i >= 0; i--) {
                        if (displayItems.get(i).demo() == null) {
                            demoList.selected(i);
                            break;
                        }
                    }
                }
            }
            return EventResult.HANDLED;
        }

        // Right: expand current group or go to first child
        if (event.matches(Actions.MOVE_RIGHT)) {
            var selected = selectedItem();
            if (selected != null && selected.demo() == null) {
                if (!selected.expanded()) {
                    // Expand
                    toggleModule(selected.module());
                } else if (current + 1 < listSize && displayItems.get(current + 1).demo() != null) {
                    // Already expanded - go to first child
                    demoList.selected(current + 1);
                }
            }
            return EventResult.HANDLED;
        }

        // Select: toggle module or select demo
        var isPlainSpace = event.code() == KeyCode.CHAR
                && event.character() == ' '
                && !event.hasCtrl()
                && !event.hasAlt();
        if (event.matches(Actions.SELECT) && !isPlainSpace) {
            var selected = selectedItem();
            if (selected != null) {
                if (selected.demo() == null) {
                    // Module header - toggle
                    toggleModule(selected.module());
                } else {
                    // Demo - select and quit
                    selectedDemo = selected.demo().name();
                    quit();
                }
            }
            return EventResult.HANDLED;
        }
        if (event.matches(Actions.SELECT) && isPlainSpace) {
            traceKeyEvent(event, "SELECT_IGNORED_SPACE", eventKind);
        }

        // Clear filter with Escape/Cancel
        if (actionCancel && !filter.isEmpty()) {
            filter = "";
            rebuildDisplayList();
            demoList.selected(findFirstSelectable());
            traceKeyEvent(event, "CANCEL_CLEAR", eventKind);
            return EventResult.HANDLED;
        }

        // Backspace/Delete: tolerate different terminal mappings and key encodings.
        if (isDeleteLikeEvent(event, actionDeleteBackward, isRawBackspace) && !filter.isEmpty()) {
            filter = filter.substring(0, filter.length() - 1);
            refreshAfterFilterChange();
            traceKeyEvent(event, "BACKSPACE_APPLIED", eventKind);
            return EventResult.HANDLED;
        }

        // Type to filter
        if (event.code() == KeyCode.CHAR && !event.hasCtrl() && !event.hasAlt()) {
            var c = event.character();
            if (Character.isLetterOrDigit(c) || c == '-' || c == '_' || c == ' ') {
                filter += c;
                refreshAfterFilterChange();
                traceKeyEvent(event, "FILTER_APPEND", eventKind);
                return EventResult.HANDLED;
            }
        }

        traceKeyEvent(event, "UNHANDLED", eventKind);

        return EventResult.UNHANDLED;
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
                filter);
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

    private boolean isDeleteLikeEvent(KeyEvent event, boolean actionDeleteBackward, boolean isRawBackspace) {
        if (actionDeleteBackward || isRawBackspace) {
            traceDeleteCheck(event, true, "ACTION_OR_RAW");
            return true;
        }

        // In many terminals Backspace is delivered as Ctrl+H.
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

        // Final fallback: some terminals emit control chars instead of explicit
        // backspace/delete key codes. Exclude ESC because it is handled by CANCEL.
        var fallback = event.code() == KeyCode.CHAR
                && !event.hasCtrl()
                && !event.hasAlt()
                && Character.isISOControl(event.character())
                && event.character() != '\u001b';
        traceDeleteCheck(event, fallback, fallback ? "CTRL_CHAR_FALLBACK" : "NO_MATCH");
        return fallback;
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
                filter);
        try {
            Files.writeString(KEY_TRACE_FILE, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
            // Keep UI responsive even if debug logging fails.
        }
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

    private void refreshAfterFilterChange() {
        rebuildDisplayList();
        demoList.selected(findFirstSelectable());
    }

    /**
     * Toggles the expanded state of a module.
     */
    private void toggleModule(String module) {
        if (expandedModules.contains(module)) {
            expandedModules.remove(module);
        } else {
            expandedModules.add(module);
        }
        // Remember current selection context
        var current = selectedItem();
        var currentModule = current != null ? current.module() : null;

        rebuildDisplayList();

        // Try to stay on the same module header
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

    private int findFirstSelectable() {
        return 0;
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
        var demoInfo = new DemoInfo(
                name,
                displayName,
                description,
                module,
                Set.of(tags)
        );
        demosByModule.computeIfAbsent(module, k -> new ArrayList<>()).add(demoInfo);
    }

    private record DemoInfo(String name, String displayName, String description, String module, Set<String> tags) {
    }

    /**
     * Represents an item in the display list (either a module header or a demo).
     */
    private record DisplayItem(String module, DemoInfo demo, boolean expanded, int demoCount) {

        String toDisplayString() {
            if (demo == null) {
                // Module header
                var icon = expanded ? "▼" : "▶";
                return icon + " " + module + " (" + demoCount + ")";
            } else {
                // Demo entry (indented)
                return "    " + demo.displayName();
            }
        }
    }
}
