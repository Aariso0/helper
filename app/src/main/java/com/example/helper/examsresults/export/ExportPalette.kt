package com.example.helper.examsresults.export

/**
 * Colours measured from the teacher's printed result sheet. The workbook and
 * the PDF use the same tokens so the two outputs read as one document.
 */
internal object ExportPalette {
    const val BANNER_BLUE = "#D9E1F2"
    const val HEADER_YELLOW = "#FFFF00"
    const val SUBJECT_CYAN = "#66FFFF"
    const val TITLE_CREAM = "#FFF2CC"
    const val BAND_BLUE = "#DDEBF7"
    const val BAND_GREY = "#DBDBDB"
    const val BAND_GREEN = "#C6E0B4"
    const val BAND_AMBER = "#FFE699"
    // Soft rank highlights (rank cell only): gold, silver, bronze.
    const val RANK_1 = "#FFF3CD"
    const val RANK_2 = "#E9ECEF"
    const val RANK_3 = "#F8D7C4"
}
