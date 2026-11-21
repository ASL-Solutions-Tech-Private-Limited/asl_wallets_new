package com.asl.wallet.core.designsystem.receipt

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.content.FileProvider
import com.asl.wallet.core.designsystem.R
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

    // ---- professional PDF styling, shared by the single receipt and the statement ----
    private const val BRAND = "ASL Wallets"
    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val MARGIN = 40f
    private val CONTENT_R = PAGE_W - MARGIN
    private val INK = 0xFF042549.toInt()
    private val BRAND1 = 0xFF0A3F82.toInt()
    private val BRAND2 = 0xFF1565C0.toInt()
    private val GREEN = 0xFF0F9D58.toInt(); private val GREEN_BG = 0xFFE7F6EE.toInt()
    private val RED = 0xFFD62828.toInt(); private val RED_BG = 0xFFFDECEC.toInt()
    private val AMBER = 0xFFB45309.toInt(); private val AMBER_BG = 0xFFFDF0DD.toInt()
    private val SLATE = 0xFF475569.toInt(); private val SLATE_BG = 0xFFEEF2F7.toInt()
    private val PAPER_INK = 0xFF141B24.toInt()
    private val PAPER_MUT = 0xFF5C6673.toInt()
    private val PAPER_LINE = 0xFFE4E8EE.toInt()
    private val CARD_BG = 0xFFFAFBFD.toInt()
    private val TABLE_HEAD = 0xFFEEF3FA.toInt()
    private val TABLE_RULE = 0xFFD8E2EF.toInt()
    private val CHIP_BG = 0xFFF3F6FB.toInt()
    private val CHIP_LINE = 0xFFE2E8F1.toInt()
    private val ZEBRA = 0xFFF7F9FC.toInt()

    private var logo: Bitmap? = null
    private var logoTried = false
    private fun logo(context: Context): Bitmap? {
        if (!logoTried) { logoTried = true; logo = runCatching { BitmapFactory.decodeResource(context.resources, R.drawable.classic_ic_asl_logo) }.getOrNull() }
        return logo
    }

    private fun paint(size: Float, color: Int, bold: Boolean = false, align: Paint.Align = Paint.Align.LEFT, spacing: Float = 0f) = Paint().apply {
        isAntiAlias = true; textSize = size; this.color = color; textAlign = align; letterSpacing = spacing
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }
    private fun fill(color: Int) = Paint().apply { isAntiAlias = true; this.color = color; style = Paint.Style.FILL }
    private fun stroke(color: Int, w: Float = 1f) = Paint().apply { isAntiAlias = true; this.color = color; style = Paint.Style.STROKE; strokeWidth = w }

    private fun ellipsize(p: Paint, text: String, maxWidth: Float): String {
        if (maxWidth <= 0f || p.measureText(text) <= maxWidth) return text
        var t = text
        while (t.isNotEmpty() && p.measureText("$t…") > maxWidth) t = t.dropLast(1)
        return "$t…"
    }

    /** Brand gradient header band with the ASL badge; returns the y just below it. [tall] = first page. */
    private fun band(c: Canvas, context: Context, title: String, right1: String?, right2: String?, tall: Boolean): Float {
        val h = if (tall) 92f else 58f
        c.drawRect(0f, 0f, PAGE_W.toFloat(), h, Paint().apply {
            isAntiAlias = true
            shader = LinearGradient(0f, 0f, PAGE_W.toFloat(), 0f, intArrayOf(INK, BRAND1, BRAND2), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
        })
        val ls = if (tall) 46f else 34f
        val ly = (h - ls) / 2f
        c.drawRoundRect(RectF(MARGIN, ly, MARGIN + ls, ly + ls), 11f, 11f, fill(Color.WHITE))
        logo(context)?.let { c.drawBitmap(it, null, RectF(MARGIN + 5, ly + 5, MARGIN + ls - 5, ly + ls - 5), Paint().apply { isFilterBitmap = true; isAntiAlias = true }) }
        val tx = MARGIN + ls + 14
        c.drawText(BRAND, tx, ly + (if (tall) 22f else 16f), paint(if (tall) 17f else 14f, Color.WHITE, bold = true))
        c.drawText(title, tx, ly + (if (tall) 40f else 31f), paint(if (tall) 10.5f else 9.5f, 0xCCFFFFFF.toInt()))
        right1?.let { c.drawText(ellipsize(paint(9f, 0xE6FFFFFF.toInt(), align = Paint.Align.RIGHT), it, 220f), CONTENT_R, ly + 16f, paint(9f, 0xE6FFFFFF.toInt(), align = Paint.Align.RIGHT)) }
        right2?.let { c.drawText(ellipsize(paint(9f, 0xB3FFFFFF.toInt(), align = Paint.Align.RIGHT), it, 220f), CONTENT_R, ly + 31f, paint(9f, 0xB3FFFFFF.toInt(), align = Paint.Align.RIGHT)) }
        return h + 26f
    }

    private fun retailer(c: Canvas, shop: ShopInfo, y0: Float): Float {
        var y = y0
        c.drawText(ellipsize(paint(12f, PAPER_INK, bold = true), "${shop.name} · ${shop.id}", CONTENT_R - MARGIN), MARGIN, y, paint(12f, PAPER_INK, bold = true)); y += 15
        if (shop.addressLine.isNotBlank()) { c.drawText(ellipsize(paint(9.5f, PAPER_MUT), shop.addressLine, CONTENT_R - MARGIN), MARGIN, y, paint(9.5f, PAPER_MUT)); y += 14 }
        y += 4; c.drawLine(MARGIN, y, CONTENT_R, y, stroke(PAPER_LINE)); return y + 20
    }

    private fun statusColors(status: String): Pair<Int, Int> {
        val s = status.lowercase()
        return when {
            "money in" in s -> GREEN to GREEN_BG
            "money out" in s -> RED to RED_BG
            listOf("success", "paid", "added", "transferred", "complete", "credited", "done").any { it in s } -> GREEN to GREEN_BG
            listOf("fail", "declin", "reject", "reversed", "refund").any { it in s } -> RED to RED_BG
            listOf("pending", "process", "progress", "initiat").any { it in s } -> AMBER to AMBER_BG
            else -> SLATE to SLATE_BG
        }
    }

    private fun pill(c: Canvas, text: String, rightX: Float, centerY: Float) {
        val (fg, bg) = statusColors(text)
        val label = text.uppercase()
        val w = paint(9f, fg, bold = true).measureText(label) + 22f
        val hh = 20f; val left = rightX - w; val top = centerY - hh / 2
        c.drawRoundRect(RectF(left, top, rightX, top + hh), 10f, 10f, fill(bg))
        c.drawText(label, left + w / 2, centerY + 3.2f, paint(9f, fg, bold = true, align = Paint.Align.CENTER))
    }

    private fun amountCard(c: Canvas, amount: String, status: String?, y0: Float): Float {
        val h = 64f; val r = RectF(MARGIN, y0, CONTENT_R, y0 + h)
        c.drawRoundRect(r, 14f, 14f, fill(CARD_BG)); c.drawRoundRect(r, 14f, 14f, stroke(PAPER_LINE))
        val cx = MARGIN + 32; val cy = y0 + h / 2
        c.drawCircle(cx, cy, 18f, fill(BRAND2))
        c.drawText("₹", cx, cy + 6f, paint(16f, Color.WHITE, bold = true, align = Paint.Align.CENTER))
        val tx = MARGIN + 62
        c.drawText("AMOUNT", tx, y0 + 25f, paint(8.5f, PAPER_MUT, spacing = 0.08f))
        c.drawText(ellipsize(paint(22f, PAPER_INK, bold = true), amount, 260f), tx, y0 + 48f, paint(22f, PAPER_INK, bold = true))
        if (!status.isNullOrBlank()) pill(c, status, CONTENT_R - 14f, y0 + 22f)
        return y0 + h + 18f
    }

    private fun receiptFooter(c: Canvas, note: String) {
        val fy = PAGE_H - 60f
        c.drawLine(MARGIN, fy, CONTENT_R, fy, stroke(PAPER_LINE))
        val text = note.ifBlank { "Computer-generated receipt — no signature required." }
        c.drawText(ellipsize(paint(8.5f, PAPER_MUT), text, CONTENT_R - MARGIN - 80f), MARGIN, fy + 20f, paint(8.5f, PAPER_MUT))
        c.drawText(BRAND, CONTENT_R, fy + 20f, paint(11f, INK, bold = true, align = Paint.Align.RIGHT))
    }


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

    /** Draws the full A4 receipt (header band, retailer, amount card, detail rows, footer) onto [c]. */
    private fun drawReceipt(c: Canvas, context: Context, shop: ShopInfo, d: ReceiptDoc) {
        var y = band(c, context, d.title, null, null, tall = true)
        y = retailer(c, shop, y)
        if (!d.amount.isNullOrBlank()) y = amountCard(c, d.amount, d.status, y)
        y += 2
        d.rows.forEach { (k, v) ->
            val kp = paint(10f, PAPER_MUT)
            val vp = paint(10f, PAPER_INK, bold = true, align = Paint.Align.RIGHT)
            c.drawText(k, MARGIN, y, kp)
            c.drawText(ellipsize(vp, v, CONTENT_R - MARGIN - kp.measureText(k) - 16f), CONTENT_R, y, vp)
            y += 9f; c.drawLine(MARGIN, y, CONTENT_R, y, stroke(PAPER_LINE)); y += 14f
        }
        if (d.statement.isNotEmpty()) {
            y += 6; c.drawText(d.statementHeader.joinToString("   "), MARGIN, y, paint(9f, PAPER_INK, bold = true)); y += 14
            d.statement.forEach { r -> if (y < PAGE_H - 90) { c.drawText(ellipsize(paint(8.5f, PAPER_MUT), r.joinToString("  "), CONTENT_R - MARGIN), MARGIN, y, paint(8.5f, PAPER_MUT)); y += 13 } }
        }
        receiptFooter(c, d.footer)
    }

    /** A4 PDF in the app's private cache, shared through FileProvider (no storage permission). */
    fun sharePdf(context: Context, shop: ShopInfo, d: ReceiptDoc, chooserTitle: String, fileName: String = "ASL-receipt.pdf") {
        val doc = PdfDocument()
        val page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, 1).create())
        drawReceipt(page.canvas, context, shop, d)
        doc.finishPage(page)
        val file = cacheFile(context, fileName)
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        shareFile(context, file, "application/pdf", chooserTitle)
    }

    /** The same receipt as a crisp PNG image — what "Share" sends, so a customer gets the receipt itself, not text. */
    fun shareReceiptImage(context: Context, shop: ShopInfo, d: ReceiptDoc, chooserTitle: String, fileName: String = "ASL-receipt.png") {
        val scale = 2
        val bmp = Bitmap.createBitmap(PAGE_W * scale, PAGE_H * scale, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.WHITE)
        c.scale(scale.toFloat(), scale.toFloat())
        drawReceipt(c, context, shop, d)
        val file = cacheFile(context, fileName)
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 95, it) }
        bmp.recycle()
        shareFile(context, file, "image/png", chooserTitle)
    }

    private fun cacheFile(context: Context, fileName: String): File {
        val dir = File(context.cacheDir, "receipts").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        return File(dir, fileName)
    }

    /**
     * Flattens a captured slip (its corners/shadow are transparent) onto a white sheet.
     * GraphicsLayer.toImageBitmap() returns a HARDWARE bitmap, which a software Canvas can't draw, so copy it first.
     */
    private fun onWhite(src: Bitmap): Bitmap {
        val soft = if (src.config == Bitmap.Config.HARDWARE) (src.copy(Bitmap.Config.ARGB_8888, false) ?: src) else src
        val out = Bitmap.createBitmap(soft.width, soft.height, Bitmap.Config.ARGB_8888)
        Canvas(out).apply { drawColor(Color.WHITE); drawBitmap(soft, 0f, 0f, null) }
        return out
    }

    /** Shares the exact on-screen receipt slip (captured to a bitmap) as a PNG — what "Share" sends. */
    fun shareReceiptBitmap(context: Context, bitmap: Bitmap, chooserTitle: String, fileName: String = "ASL-receipt.png") {
        val file = cacheFile(context, fileName)
        file.outputStream().use { onWhite(bitmap).compress(Bitmap.CompressFormat.PNG, 95, it) }
        shareFile(context, file, "image/png", chooserTitle)
    }

    /** Places the captured receipt slip on a single A4 page (centered, fit to width) and shares it as a PDF. */
    fun shareReceiptBitmapPdf(context: Context, bitmap: Bitmap, chooserTitle: String, fileName: String = "ASL-receipt.pdf") {
        val src = onWhite(bitmap)
        val doc = PdfDocument()
        val page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, 1).create())
        val margin = 28f
        val scale = minOf((PAGE_W - 2 * margin) / src.width, (PAGE_H - 2 * margin) / src.height)
        val w = src.width * scale; val h = src.height * scale
        val left = (PAGE_W - w) / 2f
        page.canvas.drawColor(Color.WHITE)
        page.canvas.drawBitmap(src, null, RectF(left, margin, left + w, margin + h), Paint().apply { isFilterBitmap = true; isAntiAlias = true })
        doc.finishPage(page)
        val file = cacheFile(context, fileName)
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        shareFile(context, file, "application/pdf", chooserTitle)
    }

    private fun sharePdfFile(context: Context, file: File, chooserTitle: String) = shareFile(context, file, "application/pdf", chooserTitle)

    private fun shareFile(context: Context, file: File, mime: String, chooserTitle: String) {
        val uri = FileProvider.getUriForFile(context, context.packageName + AUTHORITY_SUFFIX, file)
        val send = Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, chooserTitle))
    }

    /**
     * A passbook-style statement over as many A4 pages as needed: shop header, period, opening balance, one line
     * per entry (Date · Description · Debit · Credit · Balance), closing balance. Written to the private receipts
     * cache and shared through FileProvider, like receipts. [rows] must already be safe to print.
     */
    fun writeStatementPdf(context: Context, s: StatementDoc): File {
        val doc = PdfDocument()
        val xDate = MARGIN; val xDesc = 96f; val xDr = 410f; val xCr = 480f; val xBal = CONTENT_R
        var pageNo = 0
        var page: PdfDocument.Page? = null
        var c: Canvas? = null
        var y = 0f

        fun summaryChips() {
            val items = listOf("Opening" to s.opening, "Money in" to s.totalCredit, "Money out" to s.totalDebit, "Closing" to s.closing)
            val gap = 8f; val w = (CONTENT_R - MARGIN - gap * 3) / 4f; val h = 40f
            items.forEachIndexed { i, (label, value) ->
                val l = MARGIN + i * (w + gap); val rr = RectF(l, y, l + w, y + h)
                c!!.drawRoundRect(rr, 9f, 9f, fill(CHIP_BG)); c!!.drawRoundRect(rr, 9f, 9f, stroke(CHIP_LINE))
                c!!.drawText(label.uppercase(), l + 9, y + 15, paint(7f, PAPER_MUT, spacing = 0.05f))
                val vc = when (label) { "Money in" -> GREEN; "Money out" -> RED; else -> PAPER_INK }
                c!!.drawText(ellipsize(paint(11f, vc, bold = true), value ?: "—", w - 16), l + 9, y + 32, paint(11f, vc, bold = true))
            }
            y += h + 18
        }
        fun tableHeader() {
            val cv = c!!
            cv.drawRect(MARGIN - 4, y - 11, CONTENT_R + 4, y + 5, fill(TABLE_HEAD))
            val hp = paint(8f, INK, bold = true, spacing = 0.03f)
            val hr = paint(8f, INK, bold = true, align = Paint.Align.RIGHT, spacing = 0.03f)
            s.header.getOrNull(0)?.let { cv.drawText(it.uppercase(), xDate, y, hp) }
            s.header.getOrNull(1)?.let { cv.drawText(it.uppercase(), xDesc, y, hp) }
            s.header.getOrNull(2)?.let { cv.drawText(it.uppercase(), xDr, y, hr) }
            s.header.getOrNull(3)?.let { cv.drawText(it.uppercase(), xCr, y, hr) }
            s.header.getOrNull(4)?.let { cv.drawText(it.uppercase(), xBal, y, hr) }
            y += 10; cv.drawLine(MARGIN - 4, y, CONTENT_R + 4, y, stroke(TABLE_RULE, 1.5f)); y += 14
        }
        fun pageFooter() {
            val cv = c ?: return
            val fy = PAGE_H - 46f
            cv.drawLine(MARGIN, fy, CONTENT_R, fy, stroke(PAPER_LINE))
            cv.drawText(ellipsize(paint(8f, PAPER_MUT), s.footer, 360f), MARGIN, fy + 15, paint(8f, PAPER_MUT))
            cv.drawText("$BRAND · Page $pageNo", CONTENT_R, fy + 15, paint(8f, PAPER_MUT, align = Paint.Align.RIGHT))
        }
        fun newPage() {
            page?.let { pageFooter(); doc.finishPage(it) }
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create())
            c = page!!.canvas
            if (pageNo == 1) {
                y = band(c!!, context, s.title, s.periodLine, null, tall = true)
                y = retailer(c!!, s.shop, y)
                summaryChips()
            } else {
                y = band(c!!, context, "${s.title} (continued)", s.periodLine, null, tall = false)
            }
            tableHeader()
        }
        fun row(cells: List<String>, strong: Boolean, zebra: Boolean) {
            val cv = c!!
            val top = y - 10f
            if (zebra) cv.drawRect(MARGIN - 4, top, CONTENT_R + 4, y + 5f, fill(ZEBRA))
            if (strong) cv.drawLine(MARGIN - 4, top, CONTENT_R + 4, top, stroke(TABLE_RULE, 1.2f))
            val lp = paint(8.5f, if (strong) INK else PAPER_INK, bold = strong)
            val rp = paint(8.5f, if (strong) INK else PAPER_INK, bold = strong, align = Paint.Align.RIGHT)
            cv.drawText(cells.getOrElse(0) { "" }, xDate, y, lp)
            cv.drawText(ellipsize(lp, cells.getOrElse(1) { "" }, xDr - xDesc - 56f), xDesc, y, lp)
            val dr = cells.getOrElse(2) { "" }; val cr = cells.getOrElse(3) { "" }
            cv.drawText(dr, xDr, y, if (dr.isNotBlank() && !strong) paint(8.5f, RED, align = Paint.Align.RIGHT) else rp)
            cv.drawText(cr, xCr, y, if (cr.isNotBlank() && !strong) paint(8.5f, GREEN, align = Paint.Align.RIGHT) else rp)
            cv.drawText(cells.getOrElse(4) { "" }, xBal, y, rp)
            y += 15f
        }

        newPage()
        s.opening?.let { row(listOf("", s.openingLabel, "", "", it), strong = true, zebra = false) }
        var zeb = false
        s.rows.forEach { r -> if (y > PAGE_H - 92) { newPage(); zeb = false }; row(r, strong = false, zebra = zeb); zeb = !zeb }
        if (y > PAGE_H - 92) { newPage() }
        s.closing?.let { row(listOf("", s.closingLabel, s.totalDebit.orEmpty(), s.totalCredit.orEmpty(), it), strong = true, zebra = false) }
        pageFooter(); doc.finishPage(page!!)
        val file = cacheFile(context, s.fileName)
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
