import com.google.common.hash.Hashing
import com.google.gson.Gson
import com.mashape.unirest.http.HttpResponse
import com.mashape.unirest.http.JsonNode
import com.mashape.unirest.http.Unirest
import java.io.File
import java.io.InputStream
import java.io.Serializable
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset

inline fun InputStream?.readLines(forEachLine: (String) -> Unit) {
    this?.use { inputStream ->
        inputStream.bufferedReader().use {
            it.readLines().forEach { line ->
                forEachLine(line)
            }
        }
    }
}

fun String.decrypt(transformRange: Int): String {
    val sb = StringBuilder()
    val threshold = 'a' + transformRange

    toLowerCase().forEach {
        if (it.isLetter()) {
            if (it < threshold) {
                val offset = (it + 1) - 'a'
                sb.append((('z' - transformRange) + offset))
            } else {
                sb.append(it - transformRange)
            }
        } else {
            sb.append(it)
        }
    }

    return sb.toString()
}

fun String.toSha1(): String {
    val sb = StringBuilder()
    val hashBytes = Hashing.sha1().hashString(this, Charset.defaultCharset()).asBytes()
    hashBytes.forEach {
        sb.append(String.format("%02x", it))
    }

    return sb.toString()
}

data class ResponseModel(
    var numero_casas: Int = 0,
    var token: String? = "",
    var cifrado: String? = "",
    var decifrado: String? = "",
    var resumo_criptografico: String? = ""
): Serializable

class App {
    companion object {
        private const val MY_TOKEN = "435fdbddb13202a6ce7c1e345dc5fd5f0bd7a76f"
        const val TOKEN_URL = "https://api.codenation.dev/v1/challenge/dev-ps/generate-data?token=$MY_TOKEN"
        const val SUBMIT_URL = "https://api.codenation.dev/v1/challenge/dev-ps/submit-solution?token=$MY_TOKEN"
    }

    fun getResponseJsonString(): String {
        return runCatching {
            val url = URL(TOKEN_URL)
            val connection = url.openConnection() as HttpURLConnection
            val sb = StringBuilder()

            connection.inputStream.readLines {
                sb.append(it)
            }

            sb.toString()
        }.getOrDefault("Failed to get json response")
    }

    fun sendPost(file: File) {
        try {
            val jsonResponse: HttpResponse<JsonNode> = Unirest.post(SUBMIT_URL)
                .field("answer", file)
                .asJson()
            println(jsonResponse.body)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

fun parseResponse(responseString: String) : ResponseModel? {
    return runCatching {
        Gson().fromJson(responseString, ResponseModel::class.java)
    }.getOrNull()
}

fun main(args: Array<String>) {
    val app = App()

    println("Retrieving api response...")

    val responseString = app.getResponseJsonString()
    val responseObject = parseResponse(responseString)

    if (responseObject == null) {
        println("Failed to parse response from API!")
    } else {
        println("Got response from api: $responseString")

        println("Transforming object...")
        responseObject.decifrado = responseObject.cifrado?.decrypt(responseObject.numero_casas)
        responseObject.resumo_criptografico = responseObject.decifrado?.toSha1()

        println("Saving file...")
        val answerFile = File("${System.getProperty("user.home")}/answer.json")
        answerFile.writeText(Gson().toJson(responseObject))
        println("File was saved to: $answerFile")
        println("File contents: $responseObject")


        println("Sending back...")
        println(app.sendPost(answerFile))

    }
}


