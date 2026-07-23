package com.tmp.ui.shell.screen.accessdenied;

import java.util.Objects;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

@SuppressFBWarnings(value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "URF_UNREAD_FIELD"}, justification = "JavaFX ViewModel/Controller intentionally expose observable properties and retain ViewModel for FXML wiring")
/**
 * Access Denied screen ViewModel вЂ” display only; no authorization checks.
 */
public final class AccessDeniedViewModel {

    public static final String DEFAULT_MESSAGE = "РЈ РІР°СЃ РЅРµС‚ РґРѕСЃС‚СѓРїР° Рє СЌС‚РѕР№ РѕРїРµСЂР°С†РёРё.";

    private final StringProperty message = new SimpleStringProperty(DEFAULT_MESSAGE);
    private Runnable onBack = () -> {
    };

    public AccessDeniedViewModel() {
    }

    public void setMessage(String message) {
        if (message == null || message.isBlank()) {
            this.message.set(DEFAULT_MESSAGE);
        } else {
            this.message.set(message);
        }
    }

    public void setOnBack(Runnable onBack) {
        this.onBack = Objects.requireNonNull(onBack, "onBack");
    }

    public StringProperty messageProperty() {
        return message;
    }

    public void back() {
        onBack.run();
    }
}
