package com.example.vstmobile.utils

import android.content.Context
import android.content.Intent
import android.print.PdfPrinter
import android.util.Log
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

private const val TAG = "HTML_TO_PDF"

/**
 * Generates a PDF from HTML content and opens it with the system viewer.
 */
suspend fun generateAndOpenPdf(
    context: Context,
    htmlContent: String,
    fileName: String
): Boolean = try {
    val pdfFile = generatePdfFromHtml(context, htmlContent, fileName)
    if (pdfFile != null && pdfFile.exists() && pdfFile.length() > 0) {
        Log.i(TAG, "✅ PDF ready: ${pdfFile.length()} bytes → opening...")
        openPdfFile(context, pdfFile)
    } else {
        Log.e(TAG, "PDF file is null or empty")
        false
    }
} catch (e: Exception) {
    Log.e(TAG, "Error generating/opening PDF: ${e.message}", e)
    false
}

/**
 * Generates a PDF file from HTML using WebView's PrintDocumentAdapter.
 * Delegates to a Java helper (PdfPrinter) that can access the package-private
 * callback constructors.
 */
suspend fun generatePdfFromHtml(
    context: Context,
    htmlContent: String,
    fileName: String
): File? = withContext(Dispatchers.Main) {
    suspendCancellableCoroutine { cont ->
        try {
            val webView = WebView(context)
            webView.settings.javaScriptEnabled = false
            webView.settings.useWideViewPort = true
            webView.settings.loadWithOverviewMode = true
            webView.settings.setSupportZoom(false)

            // Attach to window so WebView actually lays out & renders
            val activity = context as? android.app.Activity
            val rootView = activity?.window?.decorView?.findViewById<ViewGroup>(android.R.id.content)

            if (rootView != null) {
                val params = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                webView.visibility = android.view.View.INVISIBLE
                rootView.addView(webView, params)
            }

            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String?) {
                    Log.i(TAG, "WebView page loaded, starting PDF generation...")

                    view.postDelayed({
                        try {
                            val dir = File(context.cacheDir, "pdfs").also { it.mkdirs() }
                            val outputFile = File(dir, fileName)

                            PdfPrinter.print(webView, outputFile, fileName, object : PdfPrinter.Callback {
                                override fun onSuccess(file: File) {
                                    Log.i(TAG, "✅ PDF written: ${file.absolutePath} (${file.length()} bytes)")
                                    cleanup(webView, rootView)
                                    if (cont.isActive) cont.resume(file)
                                }

                                override fun onError(error: String) {
                                    Log.e(TAG, "❌ PDF generation failed: $error")
                                    cleanup(webView, rootView)
                                    if (cont.isActive) cont.resume(null)
                                }
                            })
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ Error creating print adapter: ${e.message}", e)
                            cleanup(webView, rootView)
                            if (cont.isActive) cont.resume(null)
                        }
                    }, 800)
                }
            }

            webView.loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)

            cont.invokeOnCancellation {
                cleanup(webView, rootView)
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in generatePdfFromHtml: ${e.message}", e)
            if (cont.isActive) cont.resume(null)
        }
    }
}

private fun cleanup(webView: WebView, rootView: ViewGroup?) {
    try {
        rootView?.removeView(webView)
        webView.destroy()
    } catch (_: Exception) {}
}

fun openPdfFile(context: Context, file: File): Boolean = try {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    context.startActivity(Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/pdf")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    })
    true
} catch (e: Exception) {
    Log.e(TAG, "Error opening PDF: ${e.message}", e)
    false
}
