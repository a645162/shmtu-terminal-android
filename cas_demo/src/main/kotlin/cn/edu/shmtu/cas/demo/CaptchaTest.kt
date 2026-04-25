package cn.edu.shmtu.cas.demo

import cn.edu.shmtu.cas.captcha.Captcha
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Paths
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.imageio.ImageIO

class CaptchaTest {

    companion object {

        fun readImageFromFile(fileName: String): ByteArray {
            val imageFile = File(fileName)
            val image = ImageIO.read(imageFile)

            val baos = ByteArrayOutputStream()
            ImageIO.write(image, "png", baos)
            val imageBytes = baos.toByteArray()
            return imageBytes
        }

        fun saveImageToFile(imageData: ByteArray, directoryPath: String = ".") {
            val currentDateTime = LocalDateTime.now()
            val formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
            val fileName = "captcha_${currentDateTime.format(formatter)}.png"
            val filePath = Paths.get(directoryPath, fileName).toString()
            java.io.FileOutputStream(filePath).use { fos ->
                fos.write(imageData)
            }
            println("Image saved to file: $fileName")
        }

        fun testLocalTcpServerOcr(
            ip: String = "127.0.0.1",
            port: Int = 21601,
        ) {
            println("识别验证码 Test")
            val resultCaptcha =
                Captcha.getImageDataFromUrlUsingGet()

            if (resultCaptcha == null) {
                println("获取验证码失败")
                return
            }

            val imageData = resultCaptcha.first
            println(resultCaptcha.second)

            if (imageData == null) {
                println("获取验证码失败")
                return
            }

            val startTime = System.currentTimeMillis()
            val validateCode =
                Captcha.ocrByRemoteTcpServerAutoRetry(
                    ip, port,
                    imageData
                )
            val endTime = System.currentTimeMillis()
            val executionTime = endTime - startTime
            println("OCR执行时间: $executionTime 毫秒")

            val exprResult =
                Captcha.getExprResultByExprString(validateCode)
            println(validateCode)
            println(exprResult)

            saveImageToFile(imageData)
        }

        fun testLocalTcpServerOcrMultiThread(times: Int = 10) {
            val threads = List(times) {
                Thread {
                    testLocalTcpServerOcr()
                }
            }

            threads.forEach { it.start() }

            threads.forEach { it.join() }

            println("All threads have finished execution.")
        }

    }

}
