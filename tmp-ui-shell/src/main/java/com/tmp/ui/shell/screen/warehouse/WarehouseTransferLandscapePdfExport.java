package com.tmp.ui.shell.screen.warehouse;

import com.tmp.warehouse.api.WarehouseApi.TransferDocumentPrintLineView;
import com.tmp.warehouse.api.WarehouseApi.TransferDocumentPrintView;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import javafx.print.PageLayout;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.transform.Transform;
import javafx.stage.Stage;
import javax.imageio.ImageIO;

/**
 * Writes Warehouse print pages as a real A4 landscape PDF (MediaBox width &gt; height). Used when
 * the selected Windows printer is a virtual PDF driver that ignores JavaFX landscape orientation.
 *
 * <p>Renders the same JavaFX page nodes used for physical printing (no portrait stretch / rotate).
 */
final class WarehouseTransferLandscapePdfExport {

    private WarehouseTransferLandscapePdfExport() {}

    static void export(
            TransferDocumentPrintView document, PageLayout pageLayout, Path target)
            throws IOException {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(pageLayout, "pageLayout");
        Objects.requireNonNull(target, "target");
        if (!PrintJobOperations.isLandscapeLayout(pageLayout)) {
            throw new IllegalArgumentException(
                    "PDF export requires a verified A4 LANDSCAPE PageLayout, got printable "
                            + pageLayout.getPrintableWidth()
                            + "×"
                            + pageLayout.getPrintableHeight()
                            + " orient="
                            + pageLayout.getPageOrientation());
        }
        List<List<TransferDocumentPrintLineView>> pages =
                WarehouseTransferPrintPagination.paginate(document);
        int total = pages.size();
        List<byte[]> jpegPages = new ArrayList<>(total);
        List<int[]> jpegSizes = new ArrayList<>(total);
        for (int i = 0; i < total; i++) {
            Node pageNode =
                    WarehouseTransferPrintSupport.buildPage(
                            document, pages.get(i), i + 1, total, pageLayout);
            BufferedImage image = renderPageImage(pageNode, pageLayout);
            ByteArrayOutputStream jpeg = new ByteArrayOutputStream();
            if (!ImageIO.write(image, "jpg", jpeg)) {
                throw new IOException("JPEG writer unavailable for warehouse print PDF export");
            }
            jpegPages.add(jpeg.toByteArray());
            jpegSizes.add(new int[] {image.getWidth(), image.getHeight()});
        }
        writeLandscapePdf(target, jpegPages, jpegSizes);
    }

    /**
     * Visible for tests: asserts the PDF starts with {@code %PDF} and declares an A4 landscape
     * MediaBox (width &gt; height).
     */
    static boolean pdfDeclaresA4LandscapeMediaBox(byte[] pdfBytes) {
        if (pdfBytes == null || pdfBytes.length < 8) {
            return false;
        }
        String ascii = new String(pdfBytes, StandardCharsets.ISO_8859_1);
        if (!ascii.startsWith("%PDF")) {
            return false;
        }
        int idx = ascii.indexOf("/MediaBox");
        if (idx < 0) {
            return false;
        }
        int end = ascii.indexOf(']', idx);
        if (end < 0) {
            return false;
        }
        String box = ascii.substring(idx, end + 1);
        return box.contains(formatPt(PrintJobOperations.A4_LANDSCAPE_WIDTH_PT))
                && box.contains(formatPt(PrintJobOperations.A4_LANDSCAPE_HEIGHT_PT));
    }

    static BufferedImage renderPageImage(Node pageNode, PageLayout pageLayout) {
        double width = Math.max(1, pageLayout.getPrintableWidth());
        double height = Math.max(1, pageLayout.getPrintableHeight());
        Stage stage = new Stage();
        stage.setOpacity(0);
        Scene scene = new Scene(new StackPane(pageNode), width, height);
        WarehouseTransferPrintSupport.stylesheetUrl()
                .ifPresent(url -> scene.getStylesheets().add(url));
        stage.setScene(scene);
        stage.show();
        try {
            pageNode.applyCss();
            pageNode.autosize();
            if (pageNode instanceof javafx.scene.Parent parent) { parent.layout(); }
            double scale = 2.0;
            int pixelWidth = Math.max(1, (int) Math.ceil(width * scale));
            int pixelHeight = Math.max(1, (int) Math.ceil(height * scale));
            javafx.scene.SnapshotParameters params = new javafx.scene.SnapshotParameters();
            params.setFill(Color.WHITE);
            params.setTransform(Transform.scale(scale, scale));
            WritableImage fxImage =
                    pageNode.snapshot(params, new WritableImage(pixelWidth, pixelHeight));
            return toBufferedImage(fxImage);
        } finally {
            stage.close();
        }
    }

    static BufferedImage toBufferedImage(WritableImage fxImage) {
        int w = (int) Math.round(fxImage.getWidth());
        int h = (int) Math.round(fxImage.getHeight());
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        PixelReader reader = fxImage.getPixelReader();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                image.setRGB(x, y, reader.getArgb(x, y));
            }
        }
        return image;
    }

    private static void writeLandscapePdf(
            Path target, List<byte[]> jpegPages, List<int[]> jpegSizes) throws IOException {
        double pageW = PrintJobOperations.A4_LANDSCAPE_WIDTH_PT;
        double pageH = PrintJobOperations.A4_LANDSCAPE_HEIGHT_PT;
        int pageCount = jpegPages.size();
        List<byte[]> objects = new ArrayList<>();
        objects.add(dictObj("<< /Type /Catalog /Pages 2 0 R >>"));
        StringBuilder kids = new StringBuilder("[");
        int firstPageObj = 3;
        for (int i = 0; i < pageCount; i++) {
            if (i > 0) {
                kids.append(' ');
            }
            kids.append(firstPageObj + i * 3).append(" 0 R");
        }
        kids.append(']');
        objects.add(dictObj("<< /Type /Pages /Kids " + kids + " /Count " + pageCount + " >>"));
        for (int i = 0; i < pageCount; i++) {
            int pageObj = firstPageObj + i * 3;
            int contentObj = pageObj + 1;
            int imageObj = pageObj + 2;
            int[] size = jpegSizes.get(i);
            objects.add(
                    dictObj(
                            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 "
                                    + formatPt(pageW)
                                    + " "
                                    + formatPt(pageH)
                                    + "] /Contents "
                                    + contentObj
                                    + " 0 R /Resources << /XObject << /Im"
                                    + i
                                    + " "
                                    + imageObj
                                    + " 0 R >> >> >>"));
            String content =
                    "q "
                            + formatPt(pageW)
                            + " 0 0 "
                            + formatPt(pageH)
                            + " 0 0 cm /Im"
                            + i
                            + " Do Q\n";
            byte[] contentBytes = content.getBytes(StandardCharsets.US_ASCII);
            objects.add(streamObj(contentBytes, "<< /Length " + contentBytes.length + " >>"));
            byte[] jpeg = jpegPages.get(i);
            String imageDict =
                    "<< /Type /XObject /Subtype /Image /Width "
                            + size[0]
                            + " /Height "
                            + size[1]
                            + " /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length "
                            + jpeg.length
                            + " >>";
            objects.add(streamObj(jpeg, imageDict));
        }

        Path parent = target.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (OutputStream out = Files.newOutputStream(target)) {
            writePdfFile(out, objects);
        }
    }

    private static void writePdfFile(OutputStream out, List<byte[]> objects) throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write("%PDF-1.4\n".getBytes(StandardCharsets.US_ASCII));
        body.write(new byte[] {'%', (byte) 0xE2, (byte) 0xE3, (byte) 0xCF, (byte) 0xD3, '\n'});
        long[] offsets = new long[objects.size() + 1];
        for (int i = 0; i < objects.size(); i++) {
            offsets[i + 1] = body.size();
            body.write((i + 1 + " 0 obj\n").getBytes(StandardCharsets.US_ASCII));
            body.write(objects.get(i));
            body.write("\nendobj\n".getBytes(StandardCharsets.US_ASCII));
        }
        long xrefAt = body.size();
        StringBuilder xref = new StringBuilder();
        xref.append("xref\n0 ").append(objects.size() + 1).append('\n');
        xref.append(String.format(Locale.ROOT, "%010d %05d f ", 0, 65535)).append('\n');
        for (int i = 1; i <= objects.size(); i++) {
            xref.append(String.format(Locale.ROOT, "%010d %05d n ", offsets[i], 0)).append('\n');
        }
        xref.append("trailer\n<< /Size ")
                .append(objects.size() + 1)
                .append(" /Root 1 0 R >>\nstartxref\n")
                .append(xrefAt)
                .append("\n%%EOF\n");
        body.write(xref.toString().getBytes(StandardCharsets.US_ASCII));
        out.write(body.toByteArray());
    }

    private static byte[] dictObj(String dictionary) {
        return (dictionary + "\n").getBytes(StandardCharsets.US_ASCII);
    }

    private static byte[] streamObj(byte[] streamBytes, String dictionary) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            out.write((dictionary + "\nstream\n").getBytes(StandardCharsets.US_ASCII));
            out.write(streamBytes);
            out.write("\nendstream\n".getBytes(StandardCharsets.US_ASCII));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return out.toByteArray();
    }

    private static String formatPt(double pt) {
        return String.format(Locale.ROOT, "%.2f", pt);
    }
}
