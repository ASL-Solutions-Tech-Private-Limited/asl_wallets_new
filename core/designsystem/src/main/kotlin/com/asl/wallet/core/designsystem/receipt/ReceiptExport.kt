package com.asl.wallet.core.designsystem.receipt

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.content.FileProvider
import com.asl.wallet.core.designsystem.component.ShopInfo
import java.io.File

/** One receipt as plain lines, used for share text, PDF and print. Everything in it is already masked. */
data class ReceiptDoc(
    val title: String,
    val status: String,
    val amount: String?,
    val rows: List<Pair<String, String>>,
    val statement: List<List<String>> = emptyList(),
    val statementHeader: List<String> = emptyList(),
    val footer: String,
)

/** Shares receipt PDFs from the private cache only. Its own class so it can sit next to other FileProviders. */
class ReceiptFileProvider : FileProvider()

object ReceiptExport {
    const val AUTHORITY_SUFFIX = ".receipts"


    fun text(shop: ShopInfo, d: ReceiptDoc): String = buildString {
        appendLine("${shop.name} · ${shop.id}")
        if (shop.addressLine.isNotBlank()) appendLine(shop.addressLine)
        appendLine()
        appendLine(listOfNotNull(d.title, d.amount).joinToString(" · ") + " — " + d.status)
        d.rows.forEach { (k, v) -> appendLine("$k: $v") }
        if (d.statement.isNotEmpty()) {
            appendLine()
            d.statement.forEach { appendLine(it.joinToString("  ")) }
        }
        appendLine()
        append(d.footer)
    }

    fun shareText(context: Context, chooserTitle: String, text: String) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        context.startActivity(Intent.createChooser(send, chooserTitle))
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    /** [thermal] = 58 mm slip layout; otherwise an A4 page. */
    fun html(shop: ShopInfo, d: ReceiptDoc, thermal: Boolean): String {
        val w = if (thermal) "48mm" else "160mm"
        val fs = if (thermal) "9pt" else "11pt"
        val rows = d.rows.joinToString("") { (k, v) -> "<tr><td>${esc(k)}</td><td class=r>${esc(v)}</td></tr>" }
        val st = if (d.statement.isEmpty()) "" else
            "<table class=st><tr>${d.statementHeader.joinToString("") { "<th>${esc(it)}</th>" }}</tr>" +
                d.statement.joinToString("") { r -> "<tr>${r.joinToString("") { "<td>${esc(it)}</td>" }}</tr>" } + "</table>"
        return """<!DOCTYPE html><html><head><meta charset="utf-8"><style>
            body{font-family:sans-serif;font-size:$fs;width:$w;margin:0 auto;color:#000}
            h1{font-size:1.2em;margin:0} .m{color:#444} .amt{font-size:1.6em;font-weight:bold}
            table{width:100%;border-collapse:collapse;margin-top:6px} td{padding:2px 0;vertical-align:top} .r{text-align:right}
            .st th,.st td{border-bottom:1px solid #999;padding:2px;text-align:left} hr{border:0;border-top:1px dashed #000}
            </style></head><body>
            <h1>${esc(shop.name)} · ${esc(shop.id)}</h1><div class=m>${esc(shop.addressLine)}</div><hr>
            <div><b>${esc(d.title)}</b> — ${esc(d.status)}</div>${d.amount?.let { "<div class=amt>${esc(it)}</div>" } ?: ""}
            <table>$rows</table>$st<hr><div class=m>${esc(d.footer)}</div></body></html>"""
    }

    /** Opens the system print dialog. The WebView is kept until printing finishes. */
    fun print(activity: Activity, jobName: String, html: String, thermal: Boolean) {
        val web = WebView(activity)
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                val pm = activity.getSystemService(Context.PRINT_SERVICE) as PrintManager
                val attrs = PrintAttributes.Builder()
                    .setMediaSize(if (thermal) PrintAttributes.MediaSize("ASL_58MM", "58 mm slip", 2283, 7874) else PrintAttributes.MediaSize.ISO_A4)
                    .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                    .build()
                pm.print(jobName, view.createPrintDocumentAdapter(jobName), attrs)
                printing.remove(view)
            }
        }
        // JavaScript stays off: the page is our own static HTML.
        web.settings.javaScriptEnabled = false
        printing += web
        web.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
    }
    private val printing = mutableListOf<WebView>()

    /** A4 PDF in the app's private cache, shared through FileProvider (no storage permission). */
    fun sharePdf(context: Context, shop: ShopInfo, d: ReceiptDoc, chooserTitle: String, fileName: String = "ASL-receipt.pdf") {
        val doc = PdfDocument()
        val page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
        val c = page.canvas
        val p = Paint().apply { isAntiAlias = true; textSize = 11f }
        val bold = Paint(p).apply { typeface = Typeface.DEFAULT_BOLD; textSize = 14f }
        var y = 48f
        fun line(t: String, paint: Paint = p, x: Float = 40f) { c.drawText(t, x, y, paint); y += paint.textSize + 6 }
        line("${shop.name} · ${shop.id}", bold); line(shop.addressLine)
        y += 8; line(listOfNotNull(d.title, d.amount).joinToString(" · ") + " — " + d.status, bold)
        d.rows.forEach { (k, v) -> c.drawText(k, 40f, y, p); c.drawText(v, 300f, y, p); y += 17 }
        if (d.statement.isNotEmpty()) {
            y += 8; line(d.statementHeader.joinToString("   "), bold)
            d.statement.forEach { r -> if (y < 800) { c.drawText(r.getOrElse(0) { "" }, 40f, y, p); c.drawText(r.getOrElse(1) { "" }.take(40), 110f, y, p); c.drawText(r.getOrElse(2) { "" }, 420f, y, p); c.drawText(r.getOrElse(3) { "" }, 470f, y, p); y += 16 } }
        }
        y += 10; line(d.footer)
        doc.finishPage(page)
        val dir = File(context.cacheDir, "receipts").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        val file = File(dir, fileName)
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        sharePdfFile(context, file, chooserTitle)
    }

    private fun sharePdfFile(context: Context, file: File, chooserTitle: String) {
        val uri = FileProvider.getUriForFile(context, context.packageName + AUTHORITY_SUFFIX, file)
        val send = Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, chooserTitle))
    }

    /**
     * A passbook-style statement over as many A4 pages as needed: shop header, period, opening balance, one line
     * per entry (Date · Description · Debit · Credit · Balance), closing balance. Written to the private receipts
     * cache and shared through FileProvider, like receipts. [rows] must already be safe to print.
     */
    fun writeStatementPdf(context: Context, s: StatementDoc): File {
        val doc = PdfDocument()
        val p = Paint().apply { isAntiAlias = true; textSize = 9f }
        val bold = Paint(p).apply { typeface = Typeface.DEFAULT_BOLD }
        val title = Paint(bold).apply { textSize = 14f }
        val right = Paint(p).apply { textAlign = Paint.Align.RIGHT }
        val rightBold = Paint(bold).apply { textAlign = Paint.Align.RIGHT }
        // x of: date, description (left); debit, credit, balance (right edges)
        val xDate = 36f; val xDesc = 92f; val xDr = 410f; val xCr = 480f; val xBal = 559f
        var pageNo = 0
        var page: PdfDocument.Page? = null
        var y = 0f
        fun header() {
            page?.let { doc.finishPage(it) }
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNo).create())
            val c = page!!.canvas
            y = 44f
            if (pageNo == 1) {
                c.drawText("${s.shop.name} · ${s.shop.id}", xDate, y, title); y += 18
                if (s.shop.addressLine.isNotBlank()) { c.drawText(s.shop.addressLine, xDate, y, p); y += 14 }
                c.drawText(s.title, xDate, y, bold); y += 13
                c.drawText(s.periodLine, xDate, y, p); y += 18
            } else {
                c.drawText("${s.title} · ${s.periodLine}", xDate, y, bold); y += 18
            }
            s.header.getOrNull(0)?.let { c.drawText(it, xDate, y, bold) }
            s.header.getOrNull(1)?.let { c.drawText(it, xDesc, y, bold) }
            s.header.getOrNull(2)?.let { c.drawText(it, xDr, y, rightBold) }
            s.header.getOrNull(3)?.let { c.drawText(it, xCr, y, rightBold) }
            s.header.getOrNull(4)?.let { c.drawText(it, xBal, y, rightBold) }
            y += 4; c.drawLine(xDate, y, xBal, y, p); y += 12
        }
        fun line(cells: List<String>, strong: Boolean = false) {
            if (page == null || y > 800) header()
            val c = page!!.canvas
            val lp = if (strong) bold else p
            val rp = if (strong) rightBold else right
            c.drawText(cells.getOrElse(0) { "" }, xDate, y, lp)
            c.drawText(cells.getOrElse(1) { "" }.let { if (it.length > 52) it.take(51) + "…" else it }, xDesc, y, lp)
            c.drawText(cells.getOrElse(2) { "" }, xDr, y, rp)
            c.drawText(cells.getOrElse(3) { "" }, xCr, y, rp)
            c.drawText(cells.getOrElse(4) { "" }, xBal, y, rp)
            y += 13
        }
        header()
        s.opening?.let { line(listOf("", s.openingLabel, "", "", it), strong = true) }
        s.rows.forEach { line(it) }
        s.closing?.let { line(listOf("", s.closingLabel, s.totalDebit.orEmpty(), s.totalCredit.orEmpty(), it), strong = true) }
        if (y > 800) header()
        y += 10; page!!.canvas.drawText(s.footer, xDate, y, p)
        doc.finishPage(page!!)
        val dir = File(context.cacheDir, "receipts").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        val file = File(dir, s.fileName)
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        return file
    }

    fun shareStatementPdf(context: Context, file: File, chooserTitle: String) = sharePdfFile(context, file, chooserTitle)
}

/** A statement for [ReceiptExport.writeStatementPdf]. Amount cells are pre-formatted text. */
data class StatementDoc(
    val shop: ShopInfo,
    val title: String,
    val periodLine: String,
    val header: List<String>,
    val rows: List<List<String>>,
    val opening: String?,
    val closing: String?,
    val totalDebit: String?,
    val totalCredit: String?,
    val openingLabel: String,
    val closingLabel: String,
    val footer: String,
    val fileName: String,
)
