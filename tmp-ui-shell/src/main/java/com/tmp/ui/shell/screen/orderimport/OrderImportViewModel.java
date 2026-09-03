package com.tmp.ui.shell.screen.orderimport;

import com.tmp.order.api.OrderId;
import com.tmp.order.api.imports.OrderImportConfirmResult;
import com.tmp.order.api.imports.OrderImportConflictException;
import com.tmp.order.api.imports.OrderImportFileParseResult;
import com.tmp.order.api.imports.OrderImportPreview;
import com.tmp.order.api.imports.OrderImportProblem;
import com.tmp.order.api.imports.OrderImportProblemSeverity;
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
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
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
    static final String MSG_PREVIEW_OK = "Готов к импорту";
    static final String MSG_PREVIEW_WARNINGS = "Можно импортировать. Есть предупреждения.";
    static final String MSG_PREVIEW_ERRORS = "Импорт невозможен. Исправьте ошибки в исходном файле.";
    static final String MSG_NO_PLAN = "Сначала выполните проверку файла.";
    static final String MSG_FILE_UNREADABLE = "Не удалось прочитать файл выгрузки.";
    static final String MSG_NO_PROBLEMS = "Ошибок и предупреждений нет.";
    static final String MSG_CHECKING = "Проверка файла...";
    static final String SUBTITLE =
            "Загрузка заказов и спецификаций из файла расчётной программы";
    static final String FORMAT_HINT = "Поддерживаются файлы STXT/TXT";

    private final OrderImportService orderImportService;
    private final StxtOrderFileParser stxtOrderFileParser;
    private final AuthorizationService authorizationService;

    private final StringProperty title = new SimpleStringProperty("Импорт заказа");
    private final StringProperty subtitle = new SimpleStringProperty(SUBTITLE);
    private final StringProperty fileName = new SimpleStringProperty("");
    private final StringProperty filePathTooltip = new SimpleStringProperty("");
    private final StringProperty previewOrdersText = new SimpleStringProperty("");
    private final StringProperty previewOrderNumbersText = new SimpleStringProperty("");
    private final StringProperty previewPositionCount = new SimpleStringProperty("");
    private final StringProperty previewProductQuantity = new SimpleStringProperty("");
    private final StringProperty previewSpecificationLineCount = new SimpleStringProperty("");
    private final StringProperty previewErrorCountText = new SimpleStringProperty("Ошибки: 0");
    private final StringProperty previewWarningCountText =
            new SimpleStringProperty("Предупреждения: 0");
    private final StringProperty previewStatusText = new SimpleStringProperty("");
    private final StringProperty problemsEmptyText = new SimpleStringProperty("");
    private final StringProperty importButtonText = new SimpleStringProperty("Импортировать заказ");
    private final StringProperty statusMessage = new SimpleStringProperty("");
    private final StringProperty errorMessage = new SimpleStringProperty("");
    private final StringProperty successTitle = new SimpleStringProperty("");
    private final StringProperty successMessage = new SimpleStringProperty("");
    private final BooleanProperty loading = new SimpleBooleanProperty(false);
    private final BooleanProperty canImport = new SimpleBooleanProperty(false);
    private final BooleanProperty canSelectFile = new SimpleBooleanProperty(false);
    private final BooleanProperty fileSelected = new SimpleBooleanProperty(false);
    private final BooleanProperty previewVisible = new SimpleBooleanProperty(false);
    private final BooleanProperty problemsTableVisible = new SimpleBooleanProperty(false);
    private final BooleanProperty successVisible = new SimpleBooleanProperty(false);
    private final BooleanProperty canOpenImportedOrder = new SimpleBooleanProperty(false);
    private final BooleanProperty workingVisible = new SimpleBooleanProperty(true);
    private final ObservableList<OrderImportProblemRow> problems = FXCollections.observableArrayList();

    private Path selectedFile;
    private PreparedOrderImportPlan preparedPlan;
    private boolean previewSucceededWithoutErrors;
    private int previewOrderCount;
    private String previewOrderNumberRaw = "";
    private OrderImportConfirmResult lastConfirmResult;
    private Runnable onCancel = () -> {
    };
    private Runnable onImportSuccess = () -> {
    };
    private Consumer<OrderId> onOpenImportedOrder = id -> {
    };
    private Runnable onGoToOrderList = () -> {
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

    public void setOnOpenImportedOrder(Consumer<OrderId> onOpenImportedOrder) {
        this.onOpenImportedOrder =
                Objects.requireNonNull(onOpenImportedOrder, "onOpenImportedOrder");
    }

    public void setOnGoToOrderList(Runnable onGoToOrderList) {
        this.onGoToOrderList = Objects.requireNonNull(onGoToOrderList, "onGoToOrderList");
    }

    public void open() {
        clearWorkingState();
        refreshPermissions();
        statusMessage.set("");
        errorMessage.set("");
        clearSuccessState();
        updateImportAvailability();
        updateVisibility();
    }

    public void refreshPermissions() {
        boolean allowed = authorizationService.hasPermission(
                PermissionId.of(UiShellScreens.ORDER_CREATE_PERMISSION));
        canSelectFile.set(allowed && !successVisible.get());
        if (!allowed) {
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
        filePathTooltip.set(file.toAbsolutePath().toString());
        selectedFile = file;
        fileSelected.set(true);
        clearPreviewOnly();
        clearSuccessState();
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
            updateVisibility();
            return;
        }
        loading.set(true);
        statusMessage.set(MSG_CHECKING);
        errorMessage.set("");
        clearSuccessState();
        updateVisibility();
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
            updateVisibility();
        }
    }

    /**
     * Confirms import through Import Core using the prepared plan from preview. Does not create
     * documents directly.
     */
    public void confirmImport() {
        if (successVisible.get()) {
            return;
        }
        if (!authorizationService.hasPermission(
                PermissionId.of(UiShellScreens.ORDER_CREATE_PERMISSION))) {
            errorMessage.set(OrderUiErrorMapper.ACCESS_DENIED);
            canImport.set(false);
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
        try {
            OrderImportConfirmResult result = orderImportService.confirm(plan);
            preparedPlan = null;
            previewSucceededWithoutErrors = false;
            canSelectFile.set(false);
            lastConfirmResult = result;
            applySuccess(result);
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
            updateVisibility();
        }
    }

    /** Resets to the initial file-selection state without leaving the Import screen. */
    public void importAnother() {
        clearWorkingState();
        clearSuccessState();
        refreshPermissions();
        statusMessage.set("");
        errorMessage.set("");
        updateImportAvailability();
        updateVisibility();
    }

    public void openImportedOrder() {
        if (lastConfirmResult == null || lastConfirmResult.createdOrderCount() != 1) {
            return;
        }
        onOpenImportedOrder.accept(lastConfirmResult.orderId());
    }

    public void goToOrderList() {
        onGoToOrderList.run();
    }

    /** Cancels import without persistence and returns via callback (fallback without Shell Back). */
    public void cancel() {
        clearWorkingState();
        clearSuccessState();
        statusMessage.set("");
        errorMessage.set("");
        updateImportAvailability();
        updateVisibility();
        onCancel.run();
    }

    public String confirmationTitle() {
        if (previewOrderCount > 1) {
            return "Импортировать " + previewOrderCount + " заказа?";
        }
        String number = previewOrderNumberRaw;
        if (number == null || number.isBlank()) {
            return "Импортировать заказ?";
        }
        return "Импортировать заказ №" + number + "?";
    }

    public String confirmationBody() {
        if (previewOrderCount > 1) {
            return "После импорта заказы будут переданы в работу и станут недоступны для редактирования.";
        }
        return "После импорта заказ будет передан в работу. Изменить заказ, позиции и спецификации после этого будет нельзя.";
    }

    public String confirmationConfirmLabel() {
        return previewOrderCount > 1 ? "Импортировать заказы" : "Импортировать заказ";
    }

    public StringProperty titleProperty() {
        return title;
    }

    public StringProperty subtitleProperty() {
        return subtitle;
    }

    public StringProperty fileNameProperty() {
        return fileName;
    }

    public StringProperty filePathTooltipProperty() {
        return filePathTooltip;
    }

    public StringProperty previewOrdersTextProperty() {
        return previewOrdersText;
    }

    public StringProperty previewOrderNumbersTextProperty() {
        return previewOrderNumbersText;
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

    public StringProperty previewStatusTextProperty() {
        return previewStatusText;
    }

    public StringProperty problemsEmptyTextProperty() {
        return problemsEmptyText;
    }

    public StringProperty importButtonTextProperty() {
        return importButtonText;
    }

    public StringProperty statusMessageProperty() {
        return statusMessage;
    }

    public StringProperty errorMessageProperty() {
        return errorMessage;
    }

    public StringProperty successTitleProperty() {
        return successTitle;
    }

    public StringProperty successMessageProperty() {
        return successMessage;
    }

    public BooleanProperty loadingProperty() {
        return loading;
    }

    public BooleanProperty canImportProperty() {
        return canImport;
    }

    public BooleanProperty canSelectFileProperty() {
        return canSelectFile;
    }

    public BooleanProperty fileSelectedProperty() {
        return fileSelected;
    }

    public BooleanProperty previewVisibleProperty() {
        return previewVisible;
    }

    public BooleanProperty problemsTableVisibleProperty() {
        return problemsTableVisible;
    }

    public BooleanProperty successVisibleProperty() {
        return successVisible;
    }

    public BooleanProperty canOpenImportedOrderProperty() {
        return canOpenImportedOrder;
    }

    public BooleanProperty workingVisibleProperty() {
        return workingVisible;
    }

    public ObservableList<OrderImportProblemRow> problems() {
        return problems;
    }

    /** @deprecated retained for older tests; use {@link #problems()}. */
    @Deprecated
    public ObservableList<OrderImportProblem> errors() {
        return FXCollections.observableArrayList(
                problems.stream()
                        .map(OrderImportProblemRow::source)
                        .filter(p -> p.severity() == OrderImportProblemSeverity.ERROR)
                        .toList());
    }

    /** @deprecated retained for older tests; use {@link #problems()}. */
    @Deprecated
    public ObservableList<OrderImportProblem> warnings() {
        return FXCollections.observableArrayList(
                problems.stream()
                        .map(OrderImportProblemRow::source)
                        .filter(p -> p.severity() == OrderImportProblemSeverity.WARNING)
                        .toList());
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

    OrderImportConfirmResult lastConfirmResultForTest() {
        return lastConfirmResult;
    }

    int previewOrderCountForTest() {
        return previewOrderCount;
    }

    private void applySuccess(OrderImportConfirmResult result) {
        successVisible.set(true);
        workingVisible.set(false);
        canOpenImportedOrder.set(result.createdOrderCount() == 1);
        successTitle.set("Импорт завершён");
        if (result.createdOrderCount() > 1) {
            successMessage.set(
                    "Импортировано заказов: "
                            + result.createdOrderCount()
                            + "\nВсе заказы переданы в работу.\nПозиций создано: "
                            + result.createdPositionCount()
                            + "\nСтрок спецификации: "
                            + result.createdSpecificationLineCount());
        } else {
            successMessage.set(
                    "Заказ №"
                            + result.orderNumber()
                            + " создан и передан в работу.\nПозиций создано: "
                            + result.createdPositionCount()
                            + "\nСтрок спецификации: "
                            + result.createdSpecificationLineCount());
        }
        statusMessage.set("");
    }

    private void applyConfirmFailure(String message, List<OrderImportProblem> problemList) {
        preparedPlan = null;
        previewSucceededWithoutErrors = false;
        if (!problemList.isEmpty()) {
            setProblems(problemList);
        }
        errorMessage.set(message);
        clearSuccessState();
        canImport.set(false);
    }

    private void applyAdapterFailure(OrderImportFileParseResult parse) {
        preparedPlan = null;
        previewSucceededWithoutErrors = false;
        previewOrderCount = 0;
        previewOrderNumberRaw = "";
        previewOrdersText.set("");
        previewOrderNumbersText.set("");
        previewPositionCount.set("");
        previewProductQuantity.set("");
        previewSpecificationLineCount.set("");
        setProblems(mergeProblems(parse.errors(), parse.warnings()));
        previewVisible.set(true);
        if (parse.errors().isEmpty()) {
            errorMessage.set(MSG_FILE_UNREADABLE);
            previewStatusText.set(MSG_FILE_UNREADABLE);
        } else {
            errorMessage.set("");
            previewStatusText.set(MSG_PREVIEW_ERRORS);
        }
        statusMessage.set("");
        updateImportAvailability();
    }

    private void applyPreview(OrderImportPreview preview, List<OrderImportProblem> adapterWarnings) {
        previewOrderCount = preview.orderCount();
        previewOrderNumberRaw = nullToEmpty(preview.orderNumber());
        applyOrderSummary(preview);
        previewPositionCount.set(Integer.toString(preview.positionCount()));
        previewProductQuantity.set(formatQuantity(preview.totalProductQuantity()));
        previewSpecificationLineCount.set(Integer.toString(preview.specificationLineCount()));
        List<OrderImportProblem> merged = mergeProblems(preview.errors(), adapterWarnings);
        merged = mergeProblems(merged, preview.warnings());
        setProblems(merged);
        previewVisible.set(true);
        importButtonText.set(previewOrderCount > 1 ? "Импортировать заказы" : "Импортировать заказ");
        if (preview.canConfirm() && preview.preparedPlan().isPresent() && preview.errors().isEmpty()) {
            preparedPlan = preview.preparedPlan().orElse(null);
            previewSucceededWithoutErrors = preparedPlan != null;
            if (adapterWarnings.isEmpty() && preview.warnings().isEmpty()) {
                previewStatusText.set(MSG_PREVIEW_OK);
            } else {
                previewStatusText.set(MSG_PREVIEW_WARNINGS);
            }
            errorMessage.set("");
            statusMessage.set("");
        } else {
            preparedPlan = null;
            previewSucceededWithoutErrors = false;
            previewStatusText.set(MSG_PREVIEW_ERRORS);
            errorMessage.set("");
            statusMessage.set("");
        }
        updateImportAvailability();
    }

    private void applyOrderSummary(OrderImportPreview preview) {
        if (preview.orderCount() <= 1) {
            previewOrdersText.set("Заказ: " + nullToEmpty(preview.orderNumber()));
            previewOrderNumbersText.set("");
        } else {
            previewOrdersText.set("Заказов: " + preview.orderCount());
            previewOrderNumbersText.set("Номера: " + nullToEmpty(preview.orderNumber()));
        }
    }

    private void applyBlockingException(String message) {
        preparedPlan = null;
        previewSucceededWithoutErrors = false;
        previewOrderCount = 0;
        previewOrderNumberRaw = "";
        problems.clear();
        previewOrdersText.set("");
        previewOrderNumbersText.set("");
        previewPositionCount.set("");
        previewProductQuantity.set("");
        previewSpecificationLineCount.set("");
        previewErrorCountText.set("Ошибки: 0");
        previewWarningCountText.set("Предупреждения: 0");
        problemsEmptyText.set("");
        problemsTableVisible.set(false);
        previewVisible.set(false);
        previewStatusText.set("");
        errorMessage.set(message == null || message.isBlank() ? MSG_FILE_UNREADABLE : message);
        statusMessage.set("");
        updateImportAvailability();
    }

    private void setProblems(List<OrderImportProblem> source) {
        List<OrderImportProblem> sorted = new ArrayList<>(source);
        sorted.sort(
                Comparator.comparingInt(
                                (OrderImportProblem p) ->
                                        p.severity() == OrderImportProblemSeverity.ERROR ? 0 : 1)
                        .thenComparing(p -> nullToEmpty(p.location()))
                        .thenComparing(p -> p.positionIndex() == null ? -1 : p.positionIndex())
                        .thenComparing(
                                p ->
                                        p.specificationLineIndex() == null
                                                ? -1
                                                : p.specificationLineIndex())
                        .thenComparing(OrderImportProblem::message));
        List<OrderImportProblemRow> rows = new ArrayList<>(sorted.size());
        for (OrderImportProblem problem : sorted) {
            rows.add(OrderImportProblemRow.from(problem));
        }
        problems.setAll(rows);
        int errorCount =
                (int)
                        sorted.stream()
                                .filter(p -> p.severity() == OrderImportProblemSeverity.ERROR)
                                .count();
        int warningCount = sorted.size() - errorCount;
        previewErrorCountText.set("Ошибки: " + errorCount);
        previewWarningCountText.set("Предупреждения: " + warningCount);
        if (sorted.isEmpty()) {
            problemsTableVisible.set(false);
            problemsEmptyText.set(MSG_NO_PROBLEMS);
        } else {
            problemsTableVisible.set(true);
            problemsEmptyText.set("");
        }
    }

    private void updateImportAvailability() {
        boolean allowed =
                authorizationService.hasPermission(
                        PermissionId.of(UiShellScreens.ORDER_CREATE_PERMISSION));
        boolean ready =
                allowed
                        && !successVisible.get()
                        && preparedPlan != null
                        && previewSucceededWithoutErrors
                        && problems.stream()
                                .noneMatch(
                                        row ->
                                                row.source().severity()
                                                        == OrderImportProblemSeverity.ERROR);
        canImport.set(ready && !loading.get());
        canSelectFile.set(allowed && !successVisible.get() && !loading.get());
    }

    private void updateVisibility() {
        workingVisible.set(!successVisible.get());
    }

    private void clearWorkingState() {
        selectedFile = null;
        fileName.set("");
        filePathTooltip.set("");
        fileSelected.set(false);
        clearPreviewOnly();
    }

    private void clearPreviewOnly() {
        preparedPlan = null;
        previewSucceededWithoutErrors = false;
        previewOrderCount = 0;
        previewOrderNumberRaw = "";
        canImport.set(false);
        problems.clear();
        previewOrdersText.set("");
        previewOrderNumbersText.set("");
        previewPositionCount.set("");
        previewProductQuantity.set("");
        previewSpecificationLineCount.set("");
        previewErrorCountText.set("Ошибки: 0");
        previewWarningCountText.set("Предупреждения: 0");
        previewStatusText.set("");
        problemsEmptyText.set("");
        problemsTableVisible.set(false);
        previewVisible.set(false);
        importButtonText.set("Импортировать заказ");
    }

    private void clearSuccessState() {
        lastConfirmResult = null;
        successVisible.set(false);
        successTitle.set("");
        successMessage.set("");
        canOpenImportedOrder.set(false);
        workingVisible.set(true);
    }

    private static List<OrderImportProblem> mergeProblems(
            List<OrderImportProblem> first, List<OrderImportProblem> second) {
        List<OrderImportProblem> merged = new ArrayList<>(first);
        merged.addAll(second);
        return merged;
    }

    private static String validationUserMessage(OrderImportValidationException ex) {
        if (ex.problems() != null && !ex.problems().isEmpty()) {
            return ex.problems().get(0).message();
        }
        String message = ex.getMessage();
        return message == null || message.isBlank() ? MSG_PREVIEW_ERRORS : message;
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
