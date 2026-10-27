package com.example.helper.motion

/**
 * Stable shared-element keys (Room ids, never list positions).
 */
object MotionKeys {
    fun student(id: String) = "student:$id"
    fun classId(id: String) = "class:$id"
    fun homework(id: String) = "homework:$id"
    fun attachment(id: String) = "attachment:$id"
}
