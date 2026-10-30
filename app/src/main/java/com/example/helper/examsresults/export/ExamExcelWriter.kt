package com.example.helper.examsresults.export

import android.content.Context
import com.example.helper.coredata.entity.ExamSubject
import com.example.helper.coredata.entity.ExportColumn
import com.example.helper.coredata.entity.ExportColumnKind
import com.example.helper.examsresults.util.StudentResult
import com.example.helper.examsresults.viewmodel.ExportConfig
import com.example.helper.exportbackup.writers.XlsxDimension
import com.gyanoba.kexcel.Excel
import com.gyanoba.kexcel.sheet.CellValue
import com.gyanoba.kexcel.sheet.DoubleCellValue
import com.gyanoba.kexcel.sheet.FormulaCellValue
import com.gyanoba.kexcel.sheet.IntCellValue
import com.gyanoba.kexcel.sheet.TextCellValue
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

object ExamExcelWriter {
    fun write(
        context: Context,
        examName: String,
        subjects: List<ExamSubject>,
        results: List<StudentResult>,
        config: ExportConfig,
        columns: List<ExportColumn> = ExportColumnDefaults.columns(),
    ): File {
        val dir = File(context.cacheDir, "exams")
        if (!dir.exists()) dir.mkdirs()
        val safeName = examName.replace("[^A-Za-z0-9]".toRegex(), "_")
        val file = File(dir, "${safeName}_${System.currentTimeMillis()}.xlsx")
        FileOutputStream(file).use { out ->
            out.write(buildWorkbook(subjects, results, config, columns))
        }
        return file
    }

    /** The workbook bytes, without touching the filesystem (unit-tested). */
    internal fun buildWorkbook(
        subjects: List<ExamSubject>,
        results: List<StudentResult>,
        config: ExportConfig = ExportConfig(),
        columns: List<ExportColumn> = ExportColumnDefaults.columns(),
    ): ByteArray {
        val ordered = ExportColumnDefaults.effective(columns).filter { it.enabled }
        val issues = ExportFormulaEngine.validate(ordered, subjects)
        if (issues.isNotEmpty()) {
            throw IOException(
                issues.joinToString(" ") { issue -> "${issue.columnLabel}: ${issue.message}" },
            )
        }

        val excel = Excel.createExcel()
        val sheetName = excel.getDefaultSheet() ?: "Results"
        val sheet = excel[sheetName]

        val startRow = 2
        val firstMarkCol = 3
        val computedStartCol = firstMarkCol + subjects.size

        val headers = mutableListOf<CellValue?>()
        headers.add(TextCellValue("Roll No."))
        headers.add(TextCellValue("Student Name"))
        subjects.forEach { subject ->
            headers.add(TextCellValue("${subject.name} (${subject.maxMarks})"))
        }
        ordered.forEach { column -> headers.add(TextCellValue(column.label)) }
        sheet.appendRow(headers)

        results.forEachIndexed { index, result ->
            val rowNum = startRow + index
            val row = mutableListOf<CellValue?>()
            row.add(TextCellValue(result.student.rollNumber.orEmpty()))
            row.add(TextCellValue(result.student.name))

            subjects.forEach { subject ->
                // Unentered marks stay blank: no invented zero in the sheet.
                val obtained = result.marksBySubject[subject.id]
                row.add(obtained?.let(::numberCell))
            }

            ordered.forEach { column ->
                row.add(
                    when (column.kind) {
                        ExportColumnKind.TOTAL_MAX -> IntCellValue(result.totalMax.toInt())
                        else -> {
                            val formula = ExportFormulaEngine
                                .resolve(column, ordered, subjects, rowNum)
                                .getOrElse { error ->
                                    throw IOException("${column.label}: ${error.message}")
                                }
                            FormulaCellValue(formula)
                        }
                    },
                )
            }
            sheet.appendRow(row)
        }

        sheet.setColumnWidth(0, 10.0)
        sheet.setColumnWidth(1, 26.0)
        for (subjectIndex in subjects.indices) {
            sheet.setColumnWidth(subjectIndex + 2, 14.0)
        }
        ordered.forEachIndexed { index, column ->
            val width = when (column.kind) {
                ExportColumnKind.TOTAL_OBTAINED -> 15.0
                ExportColumnKind.TOTAL_MAX -> 11.0
                ExportColumnKind.PERCENTAGE -> 12.0
                ExportColumnKind.RESULT -> 10.0
                ExportColumnKind.CUSTOM -> 14.0
            }
            sheet.setColumnWidth(computedStartCol + index - 1, width)
        }

        val lastRow = if (results.isEmpty()) 0 else startRow + results.size - 1
        val bytes: ByteArray = excel.encode() ?: throw IOException("Could not encode the workbook")
        val withDimension = XlsxDimension.patch(bytes)
        if (lastRow < startRow) return withDimension

        val resultIndex = ordered.indexOfFirst { it.kind == ExportColumnKind.RESULT }
        if (resultIndex < 0) return withDimension
        val resultCol = computedStartCol + resultIndex
        return ResultColumnColors.patch(
            workbook = withDimension,
            sqref = "${ExportFormulaEngine.columnLetter(resultCol)}$startRow:" +
                "${ExportFormulaEngine.columnLetter(resultCol)}$lastRow",
            passArgb = argb(config.passColor),
            failArgb = argb(config.failColor),
        )
    }

    /** 0xAARRGGBB long → the eight-digit hex kexcel/Excel expect. */
    private fun argb(value: Long): String = value.toString(16).padStart(8, '0').uppercase()

    private fun numberCell(value: Double): CellValue =
        if (value % 1.0 == 0.0) IntCellValue(value.toLong()) else DoubleCellValue(value)
}
