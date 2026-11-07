package com.sshacker18.ps2restudio

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.nio.ByteBuffer
import java.nio.ByteOrder

class MainActivity : Activity() {
    private lateinit var output: TextView
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (20 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(0xFF090D18.toInt())
        }
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, pad)
        }
        fun label(text: String, size: Float, color: Int, bold: Boolean = false): TextView =
            TextView(this).apply {
                this.text = text
                textSize = size
                setTextColor(color)
                if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(0, pad / 2, 0, pad / 2)
            }

        content.addView(label("PS2 RE STUDIO", 13f, 0xFF83B7FF.toInt(), true))
        content.addView(label("Offline ELF Analyzer", 29f, 0xFFEDF2FF.toInt(), true))
        content.addView(label("Inspect PS2 homebrew executables directly on your phone. No hosting or server required.", 15f, 0xFFAAB8D0.toInt()))
        content.addView(Button(this).apply {
            text = "CHOOSE ELF FILE"
            setOnClickListener { chooseFile() }
        })
        status = label("Ready. Select dungeon_game.elf to begin.", 14f, 0xFF9FC5FF.toInt())
        content.addView(status)
        output = label("Your analysis report will appear here.", 14f, 0xFFEDF2FF.toInt()).apply {
            setBackgroundColor(0xFF111A2B.toInt())
            setPadding(pad, pad, pad, pad)
        }
        content.addView(output, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        content.addView(label("Milestone 01 - ELF header and load-segment inspection", 12f, 0xFF8FA4C4.toInt()))
        scroll.addView(content)
        root.addView(scroll)
        setContentView(root)
    }

    private fun chooseFile() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        startActivityForResult(intent, 100)
    }

    @Deprecated("Uses the platform document picker")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 100 || resultCode != RESULT_OK) return
        val uri: Uri = data?.data ?: return
        try {
            val bytes = contentResolver.openInputStream(uri)?.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var total = 0
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    total += n
                    require(total <= 32 * 1024 * 1024) { "File exceeds the 32 MB limit." }
                    out.write(buffer, 0, n)
                }
                out.toByteArray()
            } ?: error("Unable to read selected file.")
            val name = uri.lastPathSegment ?: "selected.elf"
            output.text = analyze(bytes, name)
            status.text = "Analysis complete - " + bytes.size + " bytes"
        } catch (e: Exception) {
            status.text = "Analysis failed"
            output.text = e.message ?: "Could not analyze this file."
        }
    }

    private fun analyze(data: ByteArray, filename: String): String {
        require(data.size >= 52) { "File is too small to contain an ELF header." }
        require(data[0] == 0x7f.toByte() && data[1] == 69.toByte() &&
            data[2] == 76.toByte() && data[3] == 70.toByte()) {
            "This file does not have a valid ELF signature."
        }
        val elfClass = data[4].toInt() and 0xff
        val encoding = data[5].toInt() and 0xff
        require(elfClass == 1 || elfClass == 2) { "Unsupported ELF class." }
        require(encoding == 1 || encoding == 2) { "Unsupported ELF byte order." }
        if (elfClass == 2) require(data.size >= 64) { "Truncated ELF64 header." }
        val b = ByteBuffer.wrap(data).order(if (encoding == 1) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN)
        val type = b.getShort(16).toInt() and 0xffff
        val machine = b.getShort(18).toInt() and 0xffff
        val entry = if (elfClass == 1) u32(b, 24) else b.getLong(24)
        val phoff = if (elfClass == 1) u32(b, 28) else b.getLong(32)
        val shoff = if (elfClass == 1) u32(b, 32) else b.getLong(40)
        val flags = if (elfClass == 1) u32(b, 36) else b.getInt(48).toLong() and 0xffffffffL
        val phentsize = b.getShort(if (elfClass == 1) 42 else 54).toInt() and 0xffff
        val phnum = b.getShort(if (elfClass == 1) 44 else 56).toInt() and 0xffff
        val shentsize = b.getShort(if (elfClass == 1) 46 else 58).toInt() and 0xffff
        val shnum = b.getShort(if (elfClass == 1) 48 else 60).toInt() and 0xffff
        require(phnum == 0 || phoff + phentsize.toLong() * phnum <= data.size) { "Program header table is outside the file." }
        require(shnum == 0 || shoff + shentsize.toLong() * shnum <= data.size) { "Section header table is outside the file." }

        val machineName = when (machine) {
            8 -> "MIPS"; 3 -> "Intel 80386"; 40 -> "ARM"; 62 -> "x86-64"; 183 -> "AArch64"
            else -> "Unknown (" + machine + ")"
        }
        val typeName = when (type) {
            0 -> "NONE"; 1 -> "REL"; 2 -> "EXEC"; 3 -> "DYN"; 4 -> "CORE"
            else -> "Unknown (" + type + ")"
        }
        val ps2 = elfClass == 1 && encoding == 1 && machine == 8
        val report = StringBuilder()
        report.append("FILE: ").append(filename).append('\n')
        report.append("SIZE: ").append(data.size).append(" bytes\n")
        report.append("FORMAT: ELF").append(if (elfClass == 1) 32 else 64).append('\n')
        report.append("ARCHITECTURE: ").append(machineName).append(" (machine ").append(machine).append(")\n")
        report.append("BYTE ORDER: ").append(if (encoding == 1) "Little-endian" else "Big-endian").append('\n')
        report.append("TYPE: ").append(typeName).append('\n')
        report.append("ENTRY POINT: 0x").append(java.lang.Long.toHexString(entry).uppercase()).append('\n')
        report.append("FLAGS: 0x").append(java.lang.Long.toHexString(flags).uppercase()).append('\n')
        report.append("PROGRAM HEADERS: ").append(phnum).append('\n')
        report.append("SECTION HEADERS: ").append(shnum).append("\n\n")
        report.append(if (ps2) "RESULT: PS2 FORMAT CANDIDATE\n" else "RESULT: ELF parsed; PS2 compatibility not established\n")
        report.append("MIPS ELF32 little-endian is consistent with a common PS2 executable format, but this check alone cannot prove compatibility.\n\n")
        report.append("LOAD SEGMENTS\n")
        if (phnum == 0) report.append("No program headers.\n")
        for (i in 0 until minOf(phnum, 128)) {
            val off = phoff.toInt() + i * phentsize
            val pType = u32(b, off)
            val vaddr = if (elfClass == 1) u32(b, off + 8) else b.getLong(off + 16)
            val fileSize = if (elfClass == 1) u32(b, off + 16) else b.getLong(off + 32)
            val memSize = if (elfClass == 1) u32(b, off + 20) else b.getLong(off + 40)
            val pFlags = if (elfClass == 1) u32(b, off + 24) else b.getInt(off + 4).toLong() and 0xffffffffL
            report.append("#").append(i).append(" type=").append(pType)
                .append(" vaddr=0x").append(java.lang.Long.toHexString(vaddr))
                .append(" file=").append(fileSize).append(" mem=").append(memSize)
                .append(" flags=0x").append(java.lang.Long.toHexString(pFlags)).append('\n')
        }
        return report.toString()
    }

    private fun u32(buffer: ByteBuffer, offset: Int): Long =
        buffer.getInt(offset).toLong() and 0xffffffffL
}
