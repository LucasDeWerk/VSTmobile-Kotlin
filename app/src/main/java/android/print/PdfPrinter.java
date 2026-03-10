package android.print;

import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.webkit.WebView;

import java.io.File;

/**
 * Helper placed in the android.print package so it can access the
 * package-private constructors of LayoutResultCallback and WriteResultCallback.
 */
public class PdfPrinter {

    private static final String TAG = "PdfPrinter";

    public interface Callback {
        void onSuccess(File file);
        void onError(String error);
    }

    public static void print(WebView webView, File outputFile, String jobName, Callback callback) {
        try {
            PrintDocumentAdapter adapter = webView.createPrintDocumentAdapter(jobName);

            PrintAttributes attributes = new PrintAttributes.Builder()
                    .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                    .setResolution(new PrintAttributes.Resolution("pdf", "pdf", 300, 300))
                    .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                    .build();

            adapter.onLayout(null, attributes, null,
                    new PrintDocumentAdapter.LayoutResultCallback() {
                        @Override
                        public void onLayoutFinished(PrintDocumentInfo info, boolean changed) {
                            Log.i(TAG, "onLayoutFinished: " + info);
                            writePdf(adapter, outputFile, callback);
                        }

                        @Override
                        public void onLayoutFailed(CharSequence error) {
                            Log.e(TAG, "onLayoutFailed: " + error);
                            callback.onError(error != null ? error.toString() : "Layout failed");
                        }

                        @Override
                        public void onLayoutCancelled() {
                            Log.w(TAG, "onLayoutCancelled");
                            callback.onError("Layout cancelled");
                        }
                    }, null);

        } catch (Exception e) {
            Log.e(TAG, "Error in PdfPrinter.print: " + e.getMessage(), e);
            callback.onError(e.getMessage() != null ? e.getMessage() : "Unknown error");
        }
    }

    private static void writePdf(PrintDocumentAdapter adapter, File outputFile, Callback callback) {
        try {
            ParcelFileDescriptor pfd = ParcelFileDescriptor.open(outputFile,
                    ParcelFileDescriptor.MODE_CREATE
                            | ParcelFileDescriptor.MODE_WRITE_ONLY
                            | ParcelFileDescriptor.MODE_TRUNCATE);

            adapter.onWrite(
                    new PageRange[]{PageRange.ALL_PAGES},
                    pfd,
                    new CancellationSignal(),
                    new PrintDocumentAdapter.WriteResultCallback() {
                        @Override
                        public void onWriteFinished(PageRange[] pages) {
                            closeSilently(pfd);
                            Log.i(TAG, "PDF written: " + outputFile.length() + " bytes");
                            callback.onSuccess(outputFile);
                        }

                        @Override
                        public void onWriteFailed(CharSequence error) {
                            closeSilently(pfd);
                            Log.e(TAG, "onWriteFailed: " + error);
                            callback.onError(error != null ? error.toString() : "Write failed");
                        }

                        @Override
                        public void onWriteCancelled() {
                            closeSilently(pfd);
                            Log.w(TAG, "onWriteCancelled");
                            callback.onError("Write cancelled");
                        }
                    });

        } catch (Exception e) {
            Log.e(TAG, "Error in writePdf: " + e.getMessage(), e);
            callback.onError(e.getMessage() != null ? e.getMessage() : "Write error");
        }
    }

    private static void closeSilently(ParcelFileDescriptor pfd) {
        try {
            pfd.close();
        } catch (Exception ignored) {
        }
    }
}

