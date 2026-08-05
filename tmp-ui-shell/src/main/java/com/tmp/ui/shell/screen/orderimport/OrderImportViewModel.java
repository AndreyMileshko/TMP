package com.tmp.ui.shell.screen.orderimport;

import com.tmp.order.api.imports.OrderImportConfirmResult;
import com.tmp.order.api.imports.OrderImportConflictException;
import com.tmp.order.api.imports.OrderImportFileParseResult;
import com.tmp.order.api.imports.OrderImportPreview;
import com.tmp.order.api.imports.OrderImportProblem;
import com.tmp.order.api.imports.OrderImportProcessingException;
import com.tmp.order.api.imports.OrderImportService;
import com.tmp.order.api.imports.OrderImportValidationException;
import com.tmp.order.api.imports.PreparedOrderImportPlan;
import com.tmp.order.api.imports.StxtOrderFileParser;
import com.tmp.security.api.AccessDeniedException;
import com.tmp.security.api.AuthorizationService;
import com.tmp.security.api.PermissionId;
import com.tmp.ui.shell.UiShellScreens;
import com.tmp.ui.shell.order.error.OrderUiErrorMapper;
import com.tmp.ui.shell.order.error.OrderUiOperation;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 * Order import ViewModel: File → STXT adapter → Import Core preview → confirm. Read-only after
 * success; no editing of imported data on this screen.
 */
@SuppressFBWarnings(
        value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "URF_UNREAD_FIELD"},
        justification = "JavaFX ViewModel intentionally exposes observable properties")
public final class OrderImportViewModel {

    static final String MSG_SELECT_FILE = "Выберите файл выгрузки для проверки.";
    static final String MSG_PREVIEW_OK = "Проверка завершена. Можно выполнить импорт.";
    static final String MSG_PREVIEW_ERRORS = "Импорт невозможен: исправьте ошибки в файле.";
    static final String MSG_PREVIEW_WARNINGS =
            "Проверка завершена с предупреждениями. Импорт разрешён.";
    static final String MSG_CANCELLED = "Импорт отменён. Данные не сохранены.";
    static final String MSG_NO_PLAN = "Сначала выполните проверку файла.";
    static final String MSG_FILE_UNREADABLE = "Не удалось прочитать файл выгрузки.";

    private final OrderImportService orderImportService;
    private final StxtOrderFileParser stxtOrderFileParser;
    private final AuthorizationService authorizationService;

    private final StringProperty title = new SimpleStringProperty("Импорт заказа");
    private final StringProperty fileName = new SimpleStringProperty("");
    private final StringProperty previewOrderNumber = new SimpleStringProperty("");
    private final StringProperty previewPositionCount = new SimpleStringProperty("");
    private final StringProperty previewProductQuantity = new SimpleStringProperty("");
    private final StringProperty previewSpecificationLineCount = new SimpleStringProperty("");
    private final StringProperty previewErrorCountText = new SimpleStringProperty("Ошибки: 0");
    private final StringProperty previewWarningCountText =
            new SimpleStringProperty("Предупреждения: 0");
    private final StringProperty statusMessage = new SimpleStringProperty("");
    private final StringProperty errorMessage = new SimpleStringProperty("");
    private final StringProperty successMessage = new SimpleStringProperty("");
    private final BooleanProperty loading = new SimpleBooleanProperty(false);
    private final BooleanProperty canValidate = new SimpleBooleanProperty(false);
    private final BooleanProperty canImport = new SimpleBooleanProperty(false);
    private final BooleanProperty canSelectFile = new SimpleBooleanProperty(false);
    private final ObservableList<OrderImportProblem> errors = FXCollections.observableArrayList();
    private final ObservableList<OrderImportProblem> warnings = FXCollections.observableArrayList();

    private Path selectedFile;
    private PreparedOrderImportPlan preparedPlan;
    private boolean previewSucceededWithoutErrors;
    private Runnable onCancel = () -> {
    };
    private Runnable onImportSuccess = () -> {
    };

    public OrderImportViewModel(
            OrderImportService orderImportService,
            StxtOrderFileParser stxtOrderFileParser,
            AuthorizationService authorizationService) {
        this.orderImportService = Objects.requireNonNull(orderImportService, "orderImportService");
        this.stxtOrderFileParser =
                Objects.requireNonNull(stxtOrderFileParser, "stxtOrderFileParser");
        this.authorizationService =
                Objects.requireNonNull(authorizationService, "authorizationService");
        refreshPermissions();
        updateImportAvailability();
    }

    public void setOnCancel(Runnable onCancel) {
        this.onCancel = Objects.requireNonNull(onCancel, "onCancel");
    }

    public void setOnImportSuccess(Runnable onImportSuccess) {
        this.onImportSuccess = Objects.requireNonNull(onImportSuccess, "onImportSuccess");
    }

    public void open() {
        clearWorkingState();
        refreshPermissions();
        statusMessage.set("");
        errorMessage.set("");
        successMessage.set("");
        updateImportAvailability();
    }

    public void refreshPermissions() {
        boolean allowed = authorizationService.hasPermission(
                PermissionId.of(UiShellScreens.ORDER_CREATE_PERMISSION));
        canSelectFile.set(allowed);
        if (!allowed) {
            canValidate.set(false);
            canImport.set(false);
            errorMessage.set(OrderUiErrorMapper.ACCESS_DENIED);
        } else {
            updateImportAvailability();
        }
    }

    /**
     * Remembers the chosen file and runs STXT adapter + Import Core preview. Does not confirm.
     * FileChooser cancel must not call this method.
     */
    public void selectFile(Path file) {
        Objects.requireNonNull(file, "file");
        refreshPermissions();
        if (!canSelectFile.get()) {
            return;
        }
        Path namePath = file.getFileName();
        fileName.set(namePath == null ? "" : namePath.toString());
        selectedFile = file;
        clearPreviewOnly();
        canValidate.set(true);
        validatePreview();
    }

    /** Re-runs STXT adapter + Import Core preview for the selected file. */
    public void validatePreview() {
        refreshPermissions();
        if (!canSelectFile.get()) {
            return;
        }
        if (selectedFile == null) {
            errorMessage.set(MSG_SELECT_FILE);
            preparedPlan = null;
            previewSucceededWithoutErrors = false;
            updateImportAvailability();
            return;
        }
        loading.set(true);
        errorMessage.set("");
        successMessage.set("");
        statusMessage.set("");
        try {
            OrderImportFileParseResult parse = stxtOrderFileParser.parseFile(selectedFile);
            if (parse.hasErrors() || parse.batches().isEmpty()) {
                applyAdapterFailure(parse);
                return;
            }
            List<OrderImportProblem> adapterWarnings = new ArrayList<>(parse.warnings());
            OrderImportPreview preview = orderImportService.preview(parse.batches());
            applyPreview(preview, adapterWarnings);
        } catch (OrderImportConflictException ex) {
            applyBlockingException(OrderImportConflictException.USER_MESSAGE);
        } catch (AccessDeniedException ex) {
            applyBlockingException(OrderUiErrorMapper.text(ex, OrderUiOperation.CREATE));
        } catch (RuntimeException ex) {
            applyBlockingException(safeTechnicalMessage(ex));
        } finally {
            loading.set(false);
            updateImportAvailability();
        }
    }

    /**
     * Confirms import through Import Core using the prepared plan from preview. Does not create
     * documents directly.
     */
    public void confirmImport() {
        refreshPermissions();
        if (!canSelectFile.get()) {
            return;
        }
        updateImportAvailability();
        if (!canImport.get() || preparedPlan == null || !previewSucceededWithoutErrors) {
            errorMessage.set(MSG_NO_PLAN);
            canImport.set(false);
            return;
        }
        PreparedOrderImportPlan plan = preparedPlan;
        loading.set(true);
        errorMessage.set("");
        successMessage.set("");
        try {
            OrderImportConfirmResult result = orderImportService.confirm(plan);
            preparedPlan = null;
            previewSucceededWithoutErrors = false;
            canValidate.set(false);
            canSelectFile.set(false);
            previewOrderNumber.set(nullToEmpty(result.orderNumber()));
            previewPositionCount.set(Integer.toString(result.createdPositionCount()));
            previewSpecificationLineCount.set(
                    Integer.toString(result.createdSpecificationLineCount()));
            successMessage.set(formatImportSuccess(result));
            statusMessage.set("");
            updateImportAvailability();
            onImportSuccess.run();
        } catch (OrderImportConflictException ex) {
            applyConfirmFailure(OrderImportConflictException.USER_MESSAGE, List.of());
        } catch (OrderImportValidationException ex) {
            applyConfirmFailure(validationUserMessage(ex), ex.problems());
        } catch (OrderImportProcessingException ex) {
            applyConfirmFailure(OrderImportProcessingException.USER_MESSAGE, List.of());
        } catch (AccessDeniedException ex) {
            applyConfirmFailure(OrderUiErrorMapper.text(ex, OrderUiOperation.CREATE), List.of());
        } catch (RuntimeException ex) {
            applyConfirmFailure(safeTechnicalMessage(ex), List.of());
        } finally {
            loading.set(false);
            updateImportAvailability();
        }
    }

    /** Cancels import without persistence and returns via callback. */
    public void cancel() {
        clearWorkingState();
        statusMessage.set(MSG_CANCELLED);
        errorMessage.set("");
        successMessage.set("");
        updateImportAvailability();
        onCancel.run();
    }

    public StringProperty titleProperty() {
        return title;
    }

    public StringProperty fileNameProperty() {
        return fileName;
    }

    public StringProperty previewOrderNumberProperty() {
        return previewOrderNumber;
    }

    public StringProperty previewPositionCountProperty() {
        return previewPositionCount;
    }

    public StringProperty previewProductQuantityProperty() {
        return previewProductQuantity;
    }

    public StringProperty previewSpecificationLineCountProperty() {
        return previewSpecificationLineCount;
    }

    public StringProperty previewErrorCountTextProperty() {
        return previewErrorCountText;
    }

    public StringProperty previewWarningCountTextProperty() {
        return previewWarningCountText;
    }

    public StringProperty statusMessageProperty() {
        return statusMessage;
    }

    public StringProperty errorMessageProperty() {
        return errorMessage;
    }

    public StringProperty successMessageProperty() {
        return successMessage;
    }

    public BooleanProperty loadingProperty() {
        return loading;
    }

    public BooleanProperty canValidateProperty() {
        return canValidate;
    }

    public BooleanProperty canImportProperty() {
        return canImport;
    }

    public BooleanProperty canSelectFileProperty() {
        return canSelectFile;
    }

    public ObservableList<OrderImportProblem> errors() {
        return errors;
    }

    public ObservableList<OrderImportProblem> warnings() {
        return warnings;
    }

    PreparedOrderImportPlan preparedPlanForTest() {
        return preparedPlan;
    }

    Path selectedFileForTest() {
        return selectedFile;
    }

    boolean previewSucceededWithoutErrorsForTest() {
        return previewSucceededWithoutErrors;
    }

    private void applyConfirmFailure(String message, List<OrderImportProblem> problems) {
        preparedPlan = null;
        previewSucceededWithoutErrors = false;
        if (!problems.isEmpty()) {
            errors.setAll(problems);
            updateProblemCounters();
        }
        errorMessage.set(message);
        successMessage.set("");
        statusMessage.set("");
        canImport.set(false);
    }

    private void applyAdapterFailure(OrderImportFileParseResult parse) {
        preparedPlan = null;
        previewSucceededWithoutErrors = false;
        errors.setAll(parse.errors());
        warnings.setAll(parse.warnings());
        previewOrderNumber.set("");
        previewPositionCount.set("");
        previewProductQuantity.set("");
        previewSpecificationLineCount.set("");
        updateProblemCounters();
        if (parse.errors().isEmpty()) {
            errorMessage.set(MSG_FILE_UNREADABLE);
        } else {
            errorMessage.set(firstProblemOr(parse.errors(), MSG_PREVIEW_ERRORS));
        }
        statusMessage.set("");
        updateImportAvailability();
    }

    private void applyPreview(OrderImportPreview preview, List<OrderImportProblem> adapterWarnings) {
        previewOrderNumber.set(nullToEmpty(preview.orderNumber()));
        previewPositionCount.set(Integer.toString(preview.positionCount()));
        previewProductQuantity.set(formatQuantity(preview.totalProductQuantity()));
        previewSpecificationLineCount.set(Integer.toString(preview.specificationLineCount()));
        errors.setAll(preview.errors());
        List<OrderImportProblem> mergedWarnings = new ArrayList<>(adapterWarnings);
        mergedWarnings.addAll(preview.warnings());
        warnings.setAll(mergedWarnings);
        updateProblemCounters();
        if (preview.canConfirm() && preview.preparedPlan().isPresent() && preview.errors().isEmpty()) {
            preparedPlan = preview.preparedPlan().orElse(null);
            previewSucceededWithoutErrors = preparedPlan != null;
            if (mergedWarnings.isEmpty()) {
                statusMessage.set(MSG_PREVIEW_OK);
            } else {
                statusMessage.set(MSG_PREVIEW_WARNINGS);
            }
            errorMessage.set("");
        } else {
            preparedPlan = null;
            previewSucceededWithoutErrors = false;
            statusMessage.set("");
            errorMessage.set(firstProblemOr(preview.errors(), MSG_PREVIEW_ERRORS));
        }
        updateImportAvailability();
    }

    private void applyBlockingException(String message) {
        preparedPlan = null;
        previewSucceededWithoutErrors = false;
        errors.clear();
        warnings.clear();
        previewOrderNumber.set("");
        previewPositionCount.set("");
        previewProductQuantity.set("");
        previewSpecificationLineCount.set("");
        updateProblemCounters();
        errorMessage.set(message == null || message.isBlank() ? MSG_FILE_UNREADABLE : message);
        statusMessage.set("");
        updateImportAvailability();
    }

    private void updateImportAvailability() {
        boolean allowed = canSelectFile.get();
        boolean ready =
                allowed
                        && preparedPlan != null
                        && previewSucceededWithoutErrors
                        && errors.isEmpty();
        canImport.set(ready);
    }

    private void updateProblemCounters() {
        previewErrorCountText.set("Ошибки: " + errors.size());
        previewWarningCountText.set("Предупреждения: " + warnings.size());
    }

    private void clearWorkingState() {
        selectedFile = null;
        fileName.set("");
        clearPreviewOnly();
        canValidate.set(false);
    }

    private void clearPreviewOnly() {
        preparedPlan = null;
        previewSucceededWithoutErrors = false;
        canImport.set(false);
        errors.clear();
        warnings.clear();
        previewOrderNumber.set("");
        previewPositionCount.set("");
        previewProductQuantity.set("");
        previewSpecificationLineCount.set("");
        updateProblemCounters();
    }

    private static String formatImportSuccess(OrderImportConfirmResult result) {
        if (result.createdOrderCount() > 1) {
            return "Заказы "
                    + result.orderNumber()
                    + " успешно импортированы.\nСоздано позиций: "
                    + result.createdPositionCount()
                    + ".\nСтрок спецификации: "
                    + result.createdSpecificationLineCount()
                    + ".";
        }
        return "Заказ "
                + result.orderNumber()
                + " успешно импортирован.\nСоздано позиций: "
                + result.createdPositionCount()
                + ".\nСтрок спецификации: "
                + result.createdSpecificationLineCount()
                + ".";
    }

    private static String validationUserMessage(OrderImportValidationException ex) {
        String fromProblems = firstProblemOr(ex.problems(), null);
        if (fromProblems != null) {
            return fromProblems;
        }
        String message = ex.getMessage();
        return message == null || message.isBlank() ? MSG_PREVIEW_ERRORS : message;
    }

    private static String firstProblemOr(List<OrderImportProblem> problems, String fallback) {
        if (problems == null || problems.isEmpty()) {
            return fallback;
        }
        String joined = problems.stream()
                .map(OrderImportProblem::message)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.joining(" "));
        return joined.isEmpty() ? fallback : problems.get(0).message();
    }

    private static String formatQuantity(BigDecimal value) {
        if (value == null) {
            return "";
        }
        return value.stripTrailingZeros().toPlainString();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String safeTechnicalMessage(Throwable error) {
        if (error instanceof OrderImportConflictException) {
            return OrderImportConflictException.USER_MESSAGE;
        }
        if (error instanceof OrderImportProcessingException) {
            return OrderImportProcessingException.USER_MESSAGE;
        }
        String simpleName = error.getClass().getSimpleName();
        if (simpleName.contains("Conflict")) {
            return OrderImportConflictException.USER_MESSAGE;
        }
        return OrderImportProcessingException.USER_MESSAGE;
    }
}
