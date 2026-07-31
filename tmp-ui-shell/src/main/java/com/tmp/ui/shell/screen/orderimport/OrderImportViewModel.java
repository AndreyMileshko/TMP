package com.tmp.ui.shell.screen.orderimport;

import com.tmp.order.api.imports.OrderImportBatch;
import com.tmp.order.api.imports.OrderImportConfirmResult;
import com.tmp.order.api.imports.OrderImportConflictException;
import com.tmp.order.api.imports.OrderImportDuplicateException;
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
    static final String MSG_IMPORT_SUCCESS = "Импорт успешно завершён.";
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
    }

    public void refreshPermissions() {
        boolean allowed = authorizationService.hasPermission(
                PermissionId.of(UiShellScreens.ORDER_CREATE_PERMISSION));
        canSelectFile.set(allowed);
        if (!allowed) {
            canValidate.set(false);
            canImport.set(false);
            errorMessage.set(OrderUiErrorMapper.ACCESS_DENIED);
        }
    }

    /**
     * Remembers the chosen file and runs STXT adapter + Import Core preview. Does not confirm.
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
            canImport.set(false);
            return;
        }
        loading.set(true);
        errorMessage.set("");
        successMessage.set("");
        statusMessage.set("");
        try {
            OrderImportFileParseResult parse = stxtOrderFileParser.parseFile(selectedFile);
            if (parse.hasErrors() || parse.batch().isEmpty()) {
                applyAdapterFailure(parse);
                return;
            }
            OrderImportBatch batch = parse.batch().orElseThrow();
            List<OrderImportProblem> adapterWarnings = new ArrayList<>(parse.warnings());
            OrderImportPreview preview = orderImportService.preview(batch);
            applyPreview(preview, adapterWarnings);
        } catch (OrderImportConflictException ex) {
            applyBlockingException(ex.getMessage());
        } catch (OrderImportDuplicateException ex) {
            applyBlockingException(ex.getMessage());
        } catch (AccessDeniedException ex) {
            applyBlockingException(OrderUiErrorMapper.text(ex, OrderUiOperation.CREATE));
        } catch (RuntimeException ex) {
            applyBlockingException(safeTechnicalMessage(ex));
        } finally {
            loading.set(false);
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
        if (preparedPlan == null || !canImport.get()) {
            errorMessage.set(MSG_NO_PLAN);
            return;
        }
        loading.set(true);
        errorMessage.set("");
        successMessage.set("");
        try {
            OrderImportConfirmResult result = orderImportService.confirm(preparedPlan);
            canImport.set(false);
            canValidate.set(false);
            canSelectFile.set(false);
            preparedPlan = null;
            previewOrderNumber.set(nullToEmpty(result.orderNumber()));
            previewPositionCount.set(Integer.toString(result.createdPositionCount()));
            previewSpecificationLineCount.set(
                    Integer.toString(result.createdSpecificationLineCount()));
            successMessage.set(
                    MSG_IMPORT_SUCCESS
                            + " Номер заказа: "
                            + result.orderNumber()
                            + ". Позиций: "
                            + result.createdPositionCount()
                            + ". Строк спецификации: "
                            + result.createdSpecificationLineCount()
                            + ".");
            statusMessage.set(MSG_IMPORT_SUCCESS);
            onImportSuccess.run();
        } catch (OrderImportConflictException ex) {
            canImport.set(false);
            errorMessage.set(ex.getMessage());
            statusMessage.set("");
        } catch (OrderImportDuplicateException ex) {
            canImport.set(false);
            errorMessage.set(ex.getMessage());
            statusMessage.set("");
        } catch (OrderImportValidationException ex) {
            canImport.set(false);
            errors.setAll(ex.problems());
            errorMessage.set(MSG_PREVIEW_ERRORS);
            statusMessage.set("");
        } catch (OrderImportProcessingException ex) {
            canImport.set(false);
            errorMessage.set(ex.getMessage());
            statusMessage.set("");
        } catch (AccessDeniedException ex) {
            canImport.set(false);
            errorMessage.set(OrderUiErrorMapper.text(ex, OrderUiOperation.CREATE));
            statusMessage.set("");
        } catch (RuntimeException ex) {
            canImport.set(false);
            errorMessage.set(safeTechnicalMessage(ex));
            statusMessage.set("");
        } finally {
            loading.set(false);
        }
    }

    /** Cancels import without persistence and returns via callback. */
    public void cancel() {
        clearWorkingState();
        statusMessage.set(MSG_CANCELLED);
        errorMessage.set("");
        successMessage.set("");
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

    private void applyAdapterFailure(OrderImportFileParseResult parse) {
        preparedPlan = null;
        canImport.set(false);
        errors.setAll(parse.errors());
        warnings.setAll(parse.warnings());
        previewOrderNumber.set("");
        previewPositionCount.set("");
        previewProductQuantity.set("");
        previewSpecificationLineCount.set("");
        if (parse.errors().isEmpty()) {
            errorMessage.set(MSG_FILE_UNREADABLE);
        } else {
            errorMessage.set(MSG_PREVIEW_ERRORS);
        }
        statusMessage.set("");
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
        if (preview.canConfirm()) {
            preparedPlan = preview.preparedPlan().orElse(null);
            canImport.set(preparedPlan != null);
            if (mergedWarnings.isEmpty()) {
                statusMessage.set(MSG_PREVIEW_OK);
            } else {
                statusMessage.set(MSG_PREVIEW_WARNINGS);
            }
            errorMessage.set("");
        } else {
            preparedPlan = null;
            canImport.set(false);
            statusMessage.set("");
            errorMessage.set(MSG_PREVIEW_ERRORS);
        }
    }

    private void applyBlockingException(String message) {
        preparedPlan = null;
        canImport.set(false);
        errors.clear();
        warnings.clear();
        previewOrderNumber.set("");
        previewPositionCount.set("");
        previewProductQuantity.set("");
        previewSpecificationLineCount.set("");
        errorMessage.set(message == null || message.isBlank() ? MSG_FILE_UNREADABLE : message);
        statusMessage.set("");
    }

    private void clearWorkingState() {
        selectedFile = null;
        fileName.set("");
        clearPreviewOnly();
        canValidate.set(false);
        canImport.set(false);
    }

    private void clearPreviewOnly() {
        preparedPlan = null;
        canImport.set(false);
        errors.clear();
        warnings.clear();
        previewOrderNumber.set("");
        previewPositionCount.set("");
        previewProductQuantity.set("");
        previewSpecificationLineCount.set("");
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
        if (error instanceof OrderImportProcessingException) {
            return error.getMessage();
        }
        String simpleName = error.getClass().getSimpleName();
        if (simpleName.contains("Conflict")) {
            return OrderImportConflictException.USER_MESSAGE;
        }
        if (simpleName.contains("Duplicate")) {
            return OrderImportDuplicateException.USER_MESSAGE;
        }
        return OrderImportProcessingException.USER_MESSAGE;
    }
}
