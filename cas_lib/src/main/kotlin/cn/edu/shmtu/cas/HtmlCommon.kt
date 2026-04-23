package com.khm.shmtu.cas

import java.io.File

class HtmlCommon {

    companion object {
        fun saveStringToFile(text: String, filePath: String): Boolean {
            val file = File(filePath)
            return try {
                file.writeText(text)
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }

        fun readFile(path: String): String {
            val file = File(path)
            return file.readText()
        }
    }

}
