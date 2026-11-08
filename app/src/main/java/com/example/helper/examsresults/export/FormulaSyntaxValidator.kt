package com.example.helper.examsresults.export

/**
 * A small Excel-expression check for the formula builder. It is deliberately
 * strict about structure (values, operators, commas, brackets, known kinds of
 * token) but not a calculation engine: Excel still does the maths.
 *
 * Template formulas may only call the functions in the palette. A raw pasted
 * formula (one starting with `=`) may call any function name, because advanced
 * users can legitimately paste something outside the palette; bare words and
 * malformed expressions are still rejected.
 */
internal object FormulaSyntaxValidator {
    private val FUNCTIONS = setOf(
        "SUM", "AVERAGE", "MIN", "MAX", "ROUND", "IF",
        "AND", "OR", "NOT", "COUNT", "COUNTA", "COUNTIF", "ABS",
        "ISBLANK", "N", "RANK",
    )

    private const val MAX_DEPTH = 50

    /** `null` when the formula is usable, otherwise a plain-language reason. */
    fun error(formula: String, allowAnyFunction: Boolean): String? {
        val body = formula.trim().removePrefix("=").trim()
        if (body.isEmpty()) return "Formula is empty."
        return try {
            Parser(body, allowAnyFunction).parse()
            null
        } catch (failure: FormulaSyntaxException) {
            failure.message
        }
    }

    private class Parser(
        private val text: String,
        private val allowAnyFunction: Boolean,
    ) {
        private var index = 0

        fun parse() {
            expression(depth = 0)
            skipWhitespace()
            if (index < text.length) {
                fail("Unexpected text after the formula: “${text.substring(index)}”.")
            }
        }

        private fun expression(depth: Int) {
            if (depth > MAX_DEPTH) fail("Formula is nested too deeply.")
            comparison(depth)
        }

        private fun comparison(depth: Int) {
            concatenation(depth)
            while (true) {
                skipWhitespace()
                when {
                    match("<>") || match("<=") || match(">=") ||
                        match("=") || match("<") || match(">") -> concatenation(depth)
                    else -> return
                }
            }
        }

        private fun concatenation(depth: Int) {
            additive(depth)
            while (true) {
                skipWhitespace()
                if (!match("&")) return
                additive(depth)
            }
        }

        private fun additive(depth: Int) {
            multiplicative(depth)
            while (true) {
                skipWhitespace()
                when {
                    match("+") || match("-") -> multiplicative(depth)
                    else -> return
                }
            }
        }

        private fun multiplicative(depth: Int) {
            power(depth)
            while (true) {
                skipWhitespace()
                when {
                    match("*") || match("/") -> power(depth)
                    else -> return
                }
            }
        }

        private fun power(depth: Int) {
            unary(depth)
            while (true) {
                skipWhitespace()
                if (!match("^")) return
                unary(depth)
            }
        }

        private fun unary(depth: Int) {
            skipWhitespace()
            if (match("+") || match("-")) {
                unary(depth)
                return
            }
            postfix(depth)
        }

        private fun postfix(depth: Int) {
            primary(depth)
            while (true) {
                skipWhitespace()
                if (!match("%")) return
            }
        }

        private fun primary(depth: Int) {
            skipWhitespace()
            if (index >= text.length) fail("Expected a value at the end of the formula.")
            when (text[index]) {
                '"' -> stringValue()
                '(' -> {
                    index++
                    expression(depth + 1)
                    skipWhitespace()
                    if (!match(")")) fail("Missing a closing bracket.")
                }
                '\'' -> quotedSheetReference()
                '{' -> braces()
                '$' -> cellRange()
                ';' -> fail("Use commas between arguments (Excel writes formulas in en-US syntax).")
                '#' -> fail("Replace Excel error values (like #REF!) with a real value.")
                else -> {
                    val char = text[index]
                    when {
                        char.isDigit() || (char == '.' && index + 1 < text.length && text[index + 1].isDigit()) ->
                            number()
                        char.isLetter() || char == '_' -> nameReferenceOrFunction(depth)
                        else -> fail("Unexpected character “$char”.")
                    }
                }
            }
        }

        private fun stringValue() {
            index++ // opening quote
            while (index < text.length) {
                if (text[index] == '"') {
                    if (index + 1 < text.length && text[index + 1] == '"') {
                        index += 2
                        continue
                    }
                    index++
                    return
                }
                index++
            }
            fail("A text value is missing its closing quote.")
        }

        private fun number() {
            val start = index
            while (index < text.length && text[index].isDigit()) index++
            if (index < text.length && text[index] == '.') {
                index++
                while (index < text.length && text[index].isDigit()) index++
            }
            if (index == start) fail("Expected a number.")
            if (index < text.length && (text[index] == 'e' || text[index] == 'E')) {
                index++
                if (index < text.length && (text[index] == '+' || text[index] == '-')) index++
                val expStart = index
                while (index < text.length && text[index].isDigit()) index++
                if (index == expStart) fail("Expected a number after the exponent.")
            }
        }

        /**
         * `{…}` can only be an Excel array constant here. Field tokens are
         * substituted before this check, so a word inside braces means the
         * teacher pasted a field into a raw formula.
         */
        private fun braces() {
            if (!allowAnyFunction) {
                fail("A stray “{”. Fields are written once, like {Math}.")
            }
            index++ // {
            val contentStart = index
            while (index < text.length && text[index] != '}') index++
            if (index >= text.length) fail("A {…} list is missing its closing brace.")
            val content = text.substring(contentStart, index)
            index++ // }
            if (content.any { it.isLetter() }) {
                fail(
                    "{${content.trim()}} is a field, and a pasted formula can’t use fields. " +
                        "Remove the “=” so the field is filled in for you.",
                )
            }
        }

        private fun quotedSheetReference() {
            index++ // opening quote
            while (index < text.length) {
                if (text[index] == '\'') {
                    if (index + 1 < text.length && text[index + 1] == '\'') {
                        index += 2
                        continue
                    }
                    index++
                    skipWhitespace()
                    if (!match("!")) fail("A quoted sheet name must be followed by “!”.")
                    skipWhitespace()
                    cellRange()
                    return
                }
                index++
            }
            fail("A sheet name is missing its closing quote.")
        }

        private fun nameReferenceOrFunction(depth: Int) {
            val start = index
            while (index < text.length && isNameChar(text[index])) index++
            val name = text.substring(start, index)
            skipWhitespace()

            if (match("!")) {
                skipWhitespace()
                cellRange()
                return
            }

            if (match("(")) {
                if (!allowAnyFunction && name.uppercase() !in FUNCTIONS) {
                    fail("Unknown function “$name”. Pick one from the list, or switch on “Paste Excel formula”.")
                }
                skipWhitespace()
                if (match(")")) return
                while (true) {
                    if (text.getOrNull(index) == ',') {
                        fail("A function argument is missing.")
                    }
                    expression(depth + 1)
                    skipWhitespace()
                    when {
                        match(",") -> {
                            skipWhitespace()
                            if (text.getOrNull(index) == ')') fail("A function argument is missing.")
                        }
                        match(")") -> return
                        else -> fail("Expected “,” or “)” after a function argument.")
                    }
                }
            }

            if (name.equals("TRUE", ignoreCase = true) || name.equals("FALSE", ignoreCase = true)) return

            if (isCellReference(name)) {
                cellRangeAfter(name)
                return
            }

            fail("Unknown name “$name”. Use a {Field} token or a number.")
        }

        private fun cellRange() {
            skipWhitespace()
            val name = readCellReference()
                ?: fail("Expected a cell reference like C2.")
            cellRangeAfter(name)
        }

        private fun cellRangeAfter(first: String) {
            skipWhitespace()
            if (match(":")) {
                skipWhitespace()
                readCellReference() ?: fail("A range needs a cell reference after “:”.")
            }
            // `first` is only read to validate the left side; the range is
            // otherwise opaque to this checker.
            check(first.isNotEmpty())
        }

        private fun readCellReference(): String? {
            val start = index
            if (index < text.length && text[index] == '$') index++
            val lettersStart = index
            while (index < text.length && text[index].isLetter()) index++
            if (index - lettersStart !in 1..3) {
                index = start
                return null
            }
            if (index < text.length && text[index] == '$') index++
            val digitsStart = index
            while (index < text.length && text[index].isDigit()) index++
            if (index == digitsStart) {
                index = start
                return null
            }
            return text.substring(start, index)
        }

        private fun isCellReference(name: String): Boolean {
            if (!name.first().isLetter()) return false
            val letters = name.takeWhile { it.isLetter() }
            val digits = name.drop(letters.length)
            return letters.length in 1..3 && digits.isNotEmpty() && digits.all { it.isDigit() }
        }

        private fun isNameChar(char: Char): Boolean =
            char.isLetterOrDigit() || char == '_' || char == '.'

        private fun match(token: String): Boolean {
            if (!text.startsWith(token, index)) return false
            index += token.length
            return true
        }

        private fun skipWhitespace() {
            while (index < text.length && text[index].isWhitespace()) index++
        }

        private fun fail(message: String): Nothing = throw FormulaSyntaxException(message)
    }
}

private class FormulaSyntaxException(message: String) : Exception(message)
