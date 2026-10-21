package com.example.helper.exportbackup.writers

import com.example.helper.exportbackup.summarize.MonthlySummary
import com.example.helper.exportbackup.summarize.PERCENTAGE_RULE
import java.io.OutputStream
import java.io.OutputStreamWriter
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVPrinter

/**
 * CSV writer over the one summary model (`export-backup` card: commons-csv,
 * the same library roster parses with — now writing). Teacher-readable in
 * Excel, Sheets, or any text editor; the header states the percentage rule so
 * the number is never ambiguous.
 */
object SummaryCsvWriter {

    private val HEADER = arrayOf(
        "Class",
        "Student",
        "Roll no.",
        "Present",
        "Absent",
        "Late",
        "Excused",
        "Days tracked",
        "Present %",
    )

    fun write(summary: MonthlySummary, monthLabel: String, scopeLabel: String, output: OutputStream) {
        val writer = OutputStreamWriter(output, Charsets.UTF_8)
        // Comment lines echo the report header every other format shows.
        writer.write("# Attendance summary — $monthLabel\n")
        writer.write("# Scope: $scopeLabel\n")
        writer.write("# $PERCENTAGE_RULE\n")
        writer.write("# \"No records\" means attendance was never marked that month.\n")

        val format = CSVFormat.DEFAULT.builder()
            .setHeader(*HEADER)
            .get()
        CSVPrinter(writer, format).use { printer ->
            summary.classes.forEach { classSummary ->
                classSummary.students.forEach { student ->
                    printer.printRecord(
                        classSummary.className,
                        student.studentName,
                        student.rollNumber.orEmpty(),
                        student.present,
                        student.absent,
                        student.late,
                        student.excused,
                        student.trackedRecords,
                        percentLabel(student.presentPercentage),
                    )
                }
                printer.printRecord(
                    classSummary.className,
                    "CLASS TOTAL",
                    "",
                    classSummary.present,
                    classSummary.absent,
                    classSummary.late,
                    classSummary.excused,
                    classSummary.trackedDays,
                    percentLabel(classSummary.presentPercentage),
                )
            }
            printer.printRecord(
                "ALL CLASSES",
                "TOTAL",
                "",
                summary.present,
                summary.absent,
                summary.late,
                summary.excused,
                summary.trackedDays,
                percentLabel(summary.presentPercentage),
            )
        }
    }
}

/** "82%" or "no records" — never a fabricated zero (card build note). */
internal fun percentLabel(percentage: Int?): String =
    if (percentage == null) "no records" else "$percentage%"
