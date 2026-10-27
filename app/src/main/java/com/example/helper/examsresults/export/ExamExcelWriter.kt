package com.example.helper.examsresults.export

import android.content.Context
import com.example.helper.coredata.entity.ExamSubject
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
    ): File {
        val dir = File(context.cacheDir, "exams")
        if (!dir.exists()) dir.mkdirs()
        val safeName = examName.replace("[^A-Za-z0-9]".toRegex(), "_")
        val file = File(dir, "${safeName}_${System.currentTimeMillis()}.xlsx")
        FileOutputStream(file).use { out ->
            out.write(buildWorkbook(subjects, results, config))
        }
        return file
    }

    /** The workbook bytes, without touching the filesystem (unit-tested). */
    internal fun buildWorkbook(
        subjects: List<ExamSubject>,
        results: List<StudentResult>,
        config: ExportConfig,
    ): ByteArray {
        val excel = Excel.createExcel()
        val sheetName = excel.getDefaultSheet() ?: "Results"
        val sheet = excel[sheetName]

        val headers = mutableListOf<CellValue?>()
        headers.add(TextCellValue("Roll No."))
        headers.add(TextCellValue("Student Name"))
        subjects.forEach { subject ->
            headers.add(TextCellValue("${subject.name} (${subject.maxMarks})"))
        }
        headers.add(TextCellValue("Total Obtained"))
        headers.add(TextCellValue("Total Max"))
        headers.add(TextCellValue("Percentage"))
        headers.add(TextCellValue("Result"))
        sheet.appendRow(headers)

        val startRow = 2
        val totalObtainedCol = subjects.size + 3
        val totalMaxCol = subjects.size + 4
        val percentageCol = subjects.size + 5
        val resultCol = subjects.size + 6

        results.forEachIndexed { index, result ->
            val rowNum = startRow + index
            val row = mutableListOf<CellValue?>()
            row.add(TextCellValue(result.student.rollNumber.orEmpty()))
            row.add(TextCellValue(result.student.name))

            val firstMarkCol = 3
            val lastMarkCol = subjects.size + 2
            subjects.forEach { subject ->
                // Unentered marks stay blank: no invented zero in the sheet.
                val obtained = result.marksBySubject[subject.id]
                row.add(obtained?.let(::numberCell))
            }

            row.add(FormulaCellValue("=SUM(${columnLetter(firstMarkCol)}$rowNum:${columnLetter(lastMarkCol)}$rowNum)"))
            row.add(IntCellValue(result.totalMax.toInt()))
            row.add(
                FormulaCellValue(
                    "=IF(${columnLetter(totalMaxCol)}$rowNum=0,0," +
                        "${columnLetter(totalObtainedCol)}$rowNum/${columnLetter(totalMaxCol)}$rowNum*100)",
                ),
            )
            row.add(FormulaCellValue(resultFormula(subjects, firstMarkCol, rowNum)))
            sheet.appendRow(row)
        }

        sheet.setColumnWidth(0, 10.0)
        sheet.setColumnWidth(1, 26.0)
        for (subjectIndex in subjects.indices) {
            sheet.setColumnWidth(subjectIndex + 2, 14.0)
        }
        sheet.setColumnWidth(totalObtainedCol - 1, 15.0)
        sheet.setColumnWidth(totalMaxCol - 1, 11.0)
        sheet.setColumnWidth(percentageCol - 1, 12.0)
        sheet.setColumnWidth(resultCol - 1, 10.0)

        val lastRow = if (results.isEmpty()) 0 else startRow + results.size - 1
        val bytes: ByteArray = excel.encode() ?: throw IOException("Could not encode the workbook")
        val withDimension = XlsxDimension.patch(bytes)
        if (lastRow < startRow) return withDimension
        return ResultColumnColors.patch(
            workbook = withDimension,
            sqref = "${columnLetter(resultCol)}$startRow:${columnLetter(resultCol)}$lastRow",
            passArgb = argb(config.passColor),
            failArgb = argb(config.failColor),
        )
    }

    /**
     * PASS/FAIL as a live formula: among the entered marks, every one must
     * reach its own pass mark. A blank mark is "not entered yet", not a zero,
     * so it is allowed through the checks; if every entered mark passes (or
     * only some are entered), the result is PASS. With no marks at all the
     * Result stays blank. Editing a mark re-evaluates the formula.
     */
    private fun resultFormula(subjects: List<ExamSubject>, firstMarkCol: Int, rowNum: Int): String {
        if (subjects.isEmpty()) return "=\"\""
        val columns = subjects.indices.map { index -> columnLetter(firstMarkCol + index) }
        val passing = subjects.mapIndexed { index, subject ->
            val cell = "${columns[index]}$rowNum"
            "OR($cell=\"\",$cell>=${subject.passMarks})"
        }
        val markRange = "${columns.first()}$rowNum:${columns.last()}$rowNum"
        return "=IF(COUNT($markRange)=0,\"\",IF(AND(${passing.joinToString(",")}),\"PASS\",\"FAIL\"))"
    }

    /** 0xAARRGGBB long → the eight-digit hex kexcel/Excel expect. */
    private fun argb(value: Long): String = value.toString(16).padStart(8, '0').uppercase()

    private fun numberCell(value: Double): CellValue =
        if (value % 1.0 == 0.0) IntCellValue(value.toLong()) else DoubleCellValue(value)

    private fun columnLetter(column: Int): String {
        var result = ""
        var num = column
        while (num > 0) {
            val mod = (num - 1) % 26
            result = ('A' + mod) + result
            num = (num - 1) / 26
        }
        return result
    }
}
