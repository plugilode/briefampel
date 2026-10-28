package de.briefampel.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import kotlin.math.sqrt

data class Attachment(val id: String, val name: String, val mime: String, val bytes: ByteArray, val pages: Int)
data class LetterInput(val text: String = "", val attachments: List<Attachment> = emptyList()) {
    val hasContent get() = text.isNotBlank() || attachments.isNotEmpty()
    val pages get() = attachments.sumOf { it.pages }.coerceAtLeast(1)
    fun fingerprint(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(text.toByteArray())
        attachments.forEach { digest.update(it.mime.toByteArray()); digest.update(it.bytes) }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
    fun validate() {
        require(hasContent) { "Erst einen Brief hochladen oder Text einfügen." }
        require(text.length <= 60_000) { "Der Text ist zu lang. Maximal 60.000 Zeichen." }
        require(attachments.count { it.mime.startsWith("image/") } <= 10 && attachments.sumOf { it.pages } <= 12) { "Der Brief ist zu lang. Höchstens 12 Seiten und 10 Bilder auswählen." }
        require(attachments.sumOf { it.bytes.size.toLong() } <= 15 * 1024 * 1024) { "Die Dateien sind zu groß. Insgesamt höchstens 15 MB auswählen." }
    }
}

class InputReader(private val context: Context) {
    suspend fun read(uri: Uri): Attachment = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri) ?: ""
        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: "Dokument"
        val bytes = resolver.openInputStream(uri)?.use { stream ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                require(output.size() + read <= 15 * 1024 * 1024) { "Die Datei ist zu groß. Höchstens 15 MB auswählen." }
                output.write(buffer, 0, read)
            }
            output.toByteArray()
        } ?: error("Eine Datei konnte nicht gelesen werden. Andere Datei probieren.")
        require(bytes.isNotEmpty()) { "Die Datei ist leer." }
        when {
            mime == "application/pdf" || bytes.take(5).toByteArray().toString(Charsets.US_ASCII) == "%PDF-" -> {
                val pages = withPdf(bytes) { it.pageCount }
                require(pages in 1..12) { "Der Brief ist zu lang. Höchstens 12 PDF-Seiten auswählen." }
                Attachment(uri.toString(), name, "application/pdf", bytes, pages)
            }
            mime in listOf("image/png", "image/jpeg", "image/webp") -> Attachment(uri.toString(), name, "image/jpeg", normalize(bytes), 1)
            else -> error("Dieses Format wird nicht unterstützt. PDF, PNG, JPEG oder WebP auswählen.")
        }
    }

    private fun normalize(bytes: ByteArray): ByteArray {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Das Bild konnte nicht gelesen werden." }
        var sample = 1
        while (bounds.outWidth.toLong() * bounds.outHeight / (sample.toLong() * sample) > 4_800_000) sample *= 2
        val source = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }))
        val exif = bytes.inputStream().use { ExifInterface(it) }
        val matrix = Matrix().apply {
            if (exif.isFlipped) postScale(-1f, 1f)
            postRotate(exif.rotationDegrees.toFloat())
            val scale = sqrt(1_200_000.0 / (source.width.toLong() * source.height)).coerceAtMost(1.0).toFloat()
            postScale(scale, scale)
        }
        val oriented = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        // JPEG has no alpha channel. Keep text on transparent screenshots legible on white.
        val normalized = Bitmap.createBitmap(oriented.width, oriented.height, Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(normalized).apply {
            drawColor(android.graphics.Color.WHITE)
            drawBitmap(oriented, 0f, 0f, null)
        }
        return ByteArrayOutputStream().use { output ->
            normalized.compress(Bitmap.CompressFormat.JPEG, 85, output)
            normalized.recycle()
            if (oriented !== source) oriented.recycle()
            source.recycle()
            output.toByteArray()
        }
    }

    suspend fun preview(attachment: Attachment, page: Int): Bitmap = withContext(Dispatchers.IO) {
        if (attachment.mime == "application/pdf") withPdf(attachment.bytes) { renderer ->
            renderer.openPage(page).use { p ->
                val scale = 1000f / p.width
                Bitmap.createBitmap(1000, (p.height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888).also {
                    it.eraseColor(android.graphics.Color.WHITE)
                    p.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                }
            }
        } else requireNotNull(BitmapFactory.decodeByteArray(attachment.bytes, 0, attachment.bytes.size))
    }

    private fun <T> withPdf(bytes: ByteArray, action: (PdfRenderer) -> T): T {
        val file = File.createTempFile("letter-", ".pdf", context.cacheDir)
        try {
            file.writeBytes(bytes)
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use(action)
            }
        } finally { file.delete() }
    }
}
