package com.tmp.ui.shell.theme;

import javafx.collections.ObservableList;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.DialogPane;

/**
 * Canonical TMP light theme registration. Applies the global stylesheet once per target.
 */
public final class TmpTheme {

    private static final String THEME_STYLESHEET = "com/tmp/ui/shell/theme/tmp-theme.css";

    private TmpTheme() {
    }

    public static String stylesheetUrl() {
        var resource = TmpTheme.class.getClassLoader().getResource(THEME_STYLESHEET);
        if (resource == null) {
            throw new IllegalStateException("Theme stylesheet not found on classpath: " + THEME_STYLESHEET);
        }
        return resource.toExternalForm();
    }

    public static void apply(Scene scene) {
        addStylesheetIfAbsent(scene.getStylesheets(), stylesheetUrl());
    }

    public static void apply(Parent parent) {
        addStylesheetIfAbsent(parent.getStylesheets(), stylesheetUrl());
    }

    public static void apply(DialogPane dialogPane) {
        addStylesheetIfAbsent(dialogPane.getStylesheets(), stylesheetUrl());
    }

    public static void addStylesheet(Scene scene, String classpathLocation) {
        var resource = TmpTheme.class.getClassLoader().getResource(classpathLocation);
        if (resource != null) {
            addStylesheetIfAbsent(scene.getStylesheets(), resource.toExternalForm());
        }
    }

    private static void addStylesheetIfAbsent(ObservableList<String> stylesheets, String url) {
        if (!stylesheets.contains(url)) {
            stylesheets.add(url);
        }
    }
}
