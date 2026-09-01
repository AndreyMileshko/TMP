package com.tmp.ui.shell.screen.login;

import com.tmp.security.api.AuthenticationFailedException;
import com.tmp.security.api.AuthenticationService;
import com.tmp.security.api.InvalidPasswordException;
import com.tmp.security.api.Login;
import com.tmp.security.api.PasswordConfirmationMismatchException;
import com.tmp.security.api.PasswordSetupRequiredException;
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

    public enum SubmitOutcome {
        SUCCESS,
        FAILED,
        PASSWORD_SETUP_REQUIRED
    }

    private final AuthenticationService authenticationService;
    private final StringProperty login = new SimpleStringProperty("");
    private final StringProperty errorMessage = new SimpleStringProperty("");
    private SessionSummary lastSession;
    private Login pendingPasswordSetupLogin;
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

    public Optional<Login> pendingPasswordSetupLogin() {
        return Optional.ofNullable(pendingPasswordSetupLogin);
    }

    /**
     * Attempts authentication. Returns outcome for the controller.
     */
    public SubmitOutcome submit(char[] password) {
        Objects.requireNonNull(password, "password");
        errorMessage.set("");
        lastSession = null;
        pendingPasswordSetupLogin = null;
        try {
            lastSession = authenticationService.login(Login.of(login.get()), password);
            onLoginSuccess.run();
            return SubmitOutcome.SUCCESS;
        } catch (PasswordSetupRequiredException ex) {
            pendingPasswordSetupLogin = ex.login();
            return SubmitOutcome.PASSWORD_SETUP_REQUIRED;
        } catch (AuthenticationFailedException ex) {
            errorMessage.set(ex.getMessage());
            return SubmitOutcome.FAILED;
        } catch (IllegalArgumentException ex) {
            errorMessage.set(AuthenticationFailedException.GENERIC_MESSAGE);
            return SubmitOutcome.FAILED;
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    public boolean completePasswordSetup(char[] newPassword, char[] confirmPassword) {
        Objects.requireNonNull(newPassword, "newPassword");
        Objects.requireNonNull(confirmPassword, "confirmPassword");
        errorMessage.set("");
        lastSession = null;
        Login setupLogin = pendingPasswordSetupLogin != null
                ? pendingPasswordSetupLogin
                : Login.of(login.get());
        try {
            lastSession = authenticationService.completePasswordSetup(setupLogin, newPassword, confirmPassword);
            pendingPasswordSetupLogin = null;
            onLoginSuccess.run();
            return true;
        } catch (InvalidPasswordException | PasswordConfirmationMismatchException ex) {
            errorMessage.set(ex.getMessage());
            return false;
        } catch (IllegalArgumentException ex) {
            errorMessage.set(ex.getMessage());
            return false;
        } finally {
            Arrays.fill(newPassword, '\0');
            Arrays.fill(confirmPassword, '\0');
        }
    }
}
