package com.tmp.ui.shell.screen.login;

import com.tmp.security.api.AuthenticationFailedException;
import com.tmp.security.api.AuthenticationService;
import com.tmp.security.api.Login;
import com.tmp.security.api.SessionSummary;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

@SuppressFBWarnings(value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "URF_UNREAD_FIELD"}, justification = "JavaFX ViewModel/Controller intentionally expose observable properties and retain ViewModel for FXML wiring")
/**
 * Login screen ViewModel. Spring bean; no FXML controller references.
 */
public final class LoginViewModel {

    private final AuthenticationService authenticationService;
    private final StringProperty login = new SimpleStringProperty("");
    private final StringProperty errorMessage = new SimpleStringProperty("");
    private SessionSummary lastSession;
    private Runnable onLoginSuccess = () -> {
    };

    public LoginViewModel(AuthenticationService authenticationService) {
        this.authenticationService = Objects.requireNonNull(authenticationService, "authenticationService");
    }

    public void setOnLoginSuccess(Runnable onLoginSuccess) {
        this.onLoginSuccess = Objects.requireNonNull(onLoginSuccess, "onLoginSuccess");
    }

    public StringProperty loginProperty() {
        return login;
    }

    public StringProperty errorMessageProperty() {
        return errorMessage;
    }

    public Optional<SessionSummary> lastSession() {
        return Optional.ofNullable(lastSession);
    }

    /**
     * Attempts authentication. Returns {@code true} on success.
     * On failure sets {@link #errorMessageProperty()} to the generic safe message.
     */
    public boolean submit(char[] password) {
        Objects.requireNonNull(password, "password");
        errorMessage.set("");
        lastSession = null;
        try {
            lastSession = authenticationService.login(Login.of(login.get()), password);
            onLoginSuccess.run();
            return true;
        } catch (AuthenticationFailedException ex) {
            errorMessage.set(ex.getMessage());
            return false;
        } catch (IllegalArgumentException ex) {
            errorMessage.set(AuthenticationFailedException.GENERIC_MESSAGE);
            return false;
        } finally {
            Arrays.fill(password, '\0');
        }
    }
}
