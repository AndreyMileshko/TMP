package com.tmp.ui.shell.navigation;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Browser-like session navigation history. Not persisted. Back/Forward themselves do not push.
 */
public final class ShellNavigationHistory {

    private final Deque<ShellHistoryEntry> backStack = new ArrayDeque<>();
    private final Deque<ShellHistoryEntry> forwardStack = new ArrayDeque<>();
    private ShellHistoryEntry current;

    public Optional<ShellHistoryEntry> current() {
        return Optional.ofNullable(current);
    }

    public void navigate(ShellHistoryEntry next) {
        Objects.requireNonNull(next, "next");
        if (current != null) {
            backStack.push(current);
        }
        current = next;
        forwardStack.clear();
    }

    public void replaceCurrent(ShellHistoryEntry entry) {
        Objects.requireNonNull(entry, "entry");
        if (current == null) {
            current = entry;
            return;
        }
        current = entry;
    }

    public boolean canGoBack() {
        return !backStack.isEmpty();
    }

    public boolean canGoForward() {
        return !forwardStack.isEmpty();
    }

    public Optional<ShellHistoryEntry> goBack(Predicate<ShellHistoryEntry> allowed) {
        Objects.requireNonNull(allowed, "allowed");
        while (!backStack.isEmpty()) {
            ShellHistoryEntry candidate = backStack.pop();
            if (allowed.test(candidate)) {
                if (current != null) {
                    forwardStack.push(current);
                }
                current = candidate;
                return Optional.of(current);
            }
            // Denied destinations are skipped and discarded; current screen stays put.
        }
        return Optional.empty();
    }

    public Optional<ShellHistoryEntry> goForward(Predicate<ShellHistoryEntry> allowed) {
        Objects.requireNonNull(allowed, "allowed");
        while (!forwardStack.isEmpty()) {
            ShellHistoryEntry candidate = forwardStack.pop();
            if (allowed.test(candidate)) {
                if (current != null) {
                    backStack.push(current);
                }
                current = candidate;
                return Optional.of(current);
            }
            // Denied destinations are skipped and discarded; current screen stays put.
        }
        return Optional.empty();
    }

    public void clear() {
        backStack.clear();
        forwardStack.clear();
        current = null;
    }
}
