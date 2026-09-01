package com.tmp.ui.shell.screen.login;

import com.tmp.security.api.AuthenticationFailedException;
import com.tmp.security.api.AuthenticationService;
import com.tmp.security.api.InvalidActivationCodeException;
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

    private static final String ACTIVATION_REQUIRED_MESSAGE =
            "Для этой учётной записи требуется активация. Используйте «Первый вход / активация».";

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
     * Attempts authentication. Returns outcome for the controller.
     */
    public SubmitOutcome submit(char[] password) {
        Objects.requireNonNull(password, "password");
        errorMessage.set("");
        lastSession = null;
        try {
            lastSession = authenticationService.login(Login.of(login.get()), password);
            onLoginSuccess.run();
            return SubmitOutcome.SUCCESS;
        } catch (PasswordSetupRequiredException ex) {
            errorMessage.set(ACTIVATION_REQUIRED_MESSAGE);
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

    /**
     * Completes activation with login, code, and new password. Returns empty on success or an
     * error message to display inside the activation dialog.
     */
    public Optional<String> completeActivation(
            String activationLogin, String activationCode, char[] newPassword, char[] confirmPassword) {
        Objects.requireNonNull(activationLogin, "activationLogin");
        Objects.requireNonNull(activationCode, "activationCode");
        Objects.requireNonNull(newPassword, "newPassword");
        Objects.requireNonNull(confirmPassword, "confirmPassword");
        errorMessage.set("");
        lastSession = null;
        try {
            lastSession = authenticationService.completePasswordSetup(
                    Login.of(activationLogin), activationCode, newPassword, confirmPassword);
            onLoginSuccess.run();
            return Optional.empty();
        } catch (InvalidActivationCodeException ex) {
            return Optional.of(ex.getMessage());
        } catch (InvalidPasswordException | PasswordConfirmationMismatchException ex) {
            return Optional.of(ex.getMessage());
        } catch (IllegalArgumentException ex) {
            return Optional.of(safeMessage(ex));
        } finally {
            Arrays.fill(newPassword, '\0');
            Arrays.fill(confirmPassword, '\0');
        }
    }

    private static String safeMessage(Throwable ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return "Операция не выполнена";
        }
        return message;
    }
}
