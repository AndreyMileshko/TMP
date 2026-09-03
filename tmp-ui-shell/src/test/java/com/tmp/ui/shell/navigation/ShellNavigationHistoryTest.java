package com.tmp.ui.shell.navigation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ShellNavigationHistoryTest {

    @Test
    void backAndForwardRestoreWithoutPushing() {
        ShellNavigationHistory history = new ShellNavigationHistory();
        history.navigate(entry("list"));
        history.navigate(entry("order"));
        history.navigate(entry("item"));
        assertEquals("order", history.goBack(e -> true).orElseThrow().screenId());
        assertEquals("list", history.goBack(e -> true).orElseThrow().screenId());
        assertEquals("order", history.goForward(e -> true).orElseThrow().screenId());
        assertEquals("item", history.goForward(e -> true).orElseThrow().screenId());
        assertFalse(history.canGoForward());
    }

    @Test
    void branchingClearsForward() {
        ShellNavigationHistory history = new ShellNavigationHistory();
        history.navigate(entry("A"));
        history.navigate(entry("B"));
        history.navigate(entry("C"));
        history.goBack(e -> true);
        history.navigate(entry("D"));
        assertFalse(history.canGoForward());
        assertEquals("B", history.goBack(e -> true).orElseThrow().screenId());
    }

    @Test
    void restoreDoesNotOccurFromNavigateReplaceCurrent() {
        ShellNavigationHistory history = new ShellNavigationHistory();
        List<String> restores = new ArrayList<>();
        history.navigate(ShellHistoryEntry.of("list", "p", () -> restores.add("list")));
        history.replaceCurrent(ShellHistoryEntry.of("list", "p", () -> restores.add("memento")));
        history.navigate(ShellHistoryEntry.of("order", "p", () -> restores.add("order")));
        history.goBack(e -> true).orElseThrow().restore().run();
        assertEquals(List.of("memento"), restores);
    }

    @Test
    void clearEmptiesStacks() {
        ShellNavigationHistory history = new ShellNavigationHistory();
        history.navigate(entry("A"));
        history.navigate(entry("B"));
        history.clear();
        assertTrue(history.current().isEmpty());
        assertFalse(history.canGoBack());
        assertFalse(history.canGoForward());
    }

    @Test
    void deniedBackTargetIsSkippedWithoutMovingCurrent() {
        ShellNavigationHistory history = new ShellNavigationHistory();
        history.navigate(entry("list"));
        history.navigate(entry("order"));
        assertTrue(history.goBack(e -> !"list".equals(e.screenId())).isEmpty());
        assertEquals("order", history.current().orElseThrow().screenId());
        assertFalse(history.canGoBack());
    }

    private static ShellHistoryEntry entry(String id) {
        return ShellHistoryEntry.of(id, "perm", () -> {});
    }
}
