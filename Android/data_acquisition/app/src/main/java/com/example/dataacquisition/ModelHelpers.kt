package com.example.dataacquisition

import android.util.Log
import com.google.gson.GsonBuilder
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.round
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST

import okhttp3.OkHttpClient
import okhttp3.Interceptor
import okhttp3.Request
import retrofit2.http.Header
import retrofit2.http.Headers

///////// API /////////
data class SaveAPIRequestBody(val data: String)
data class YourResponse(val response: String)

/*interface ApiService {
    @POST("smart_watch_acquisition")
    fun postAcquireSensorsData(
        @Body body: SaveAPIRequestBody
    ): Call<YourResponse> // YourResponse is your expected response model

    @POST("smart_watch_gesture")
    fun postAcquireGestureData(@Body body: SaveAPIRequestBody): Call<YourResponse> // YourResponse is your expected response model
}
*/
interface ApiService {
    @Headers(
        "Content-Type:application/json"
    )
    @POST("smart_watch_acquisition")
    fun postAcquireSensorsData(
        @Body body: SaveAPIRequestBody
    ): Call<YourResponse>

    @Headers(
        "Content-Type:application/json"
    )
    @POST("smart_watch_gesture")
    fun postAcquireGestureData(
        @Body body: SaveAPIRequestBody
    ): Call<YourResponse>
}

val token = "Bearer 123456789abcdef"

val okHttpClient = OkHttpClient.Builder()
    .addInterceptor { chain ->
        val request = chain.request().newBuilder()
            .addHeader("Content-Type", "application/json") // Aggiunta manuale dell'header
            .build()
        chain.proceed(request)
    }
    .build()

// before was http://192.168.1.155:8080/
val retrofit = Retrofit.Builder()
    .baseUrl("http://193.205.129.120:63435/publish/")
    .client(okHttpClient)
    .addConverterFactory(GsonConverterFactory.create(GsonBuilder().setLenient().create()))
    .build()

/*val headerInterceptor = Interceptor { chain ->
    val original = chain.request()
    val request = original.newBuilder()
        .header("Authorization", "Bearer 123456789abcdef")
        .build()
    chain.proceed(request)
}

val client = OkHttpClient.Builder()
    .addInterceptor(headerInterceptor)
    .build()

val retrofit = Retrofit.Builder()
    .baseUrl("http://193.205.129.120:63435/publish/")
    .client(client)
    .addConverterFactory(GsonConverterFactory.create(GsonBuilder().setLenient().create()))
    .build()*/

//// Create an instance of your API interface
val apiService: ApiService = retrofit.create(ApiService::class.java)


///////// API /////////

object ModelHelpers {
    private var alpha: Double = 0.06

    fun processData(
        X_test_raw: Array<FloatArray>,
        startData: Long,
        dataLen: Int,
        interpreter: Interpreter
    ): String {
        println("HAND STOPPED")
        println("stop")

        val X_test = Array(1) { Array(dataLen) { FloatArray(6) } }
        val X_test_struct = mutableListOf<Map<Int, Array<FloatArray>>>()

        val subArray = X_test_raw.copyOfRange(0, dataLen)
        val mapEntry = mapOf(1 to subArray)
        X_test_struct.add(mapEntry)
        val numCol = X_test[0][0].size
        // Low pass filter
        for (i in 0 until dataLen) {
            for (j in 0 until numCol) {
                X_test[0][i][j] =
                    (((1 - alpha) * X_test[0][i][j]) + (alpha * (X_test_struct[0][1]?.get(i)
                        ?.get(j)!!))).toFloat()
            }
        }
        println("time take to load and filter ${(System.currentTimeMillis() - startData) / 1000.0} sec")
        // Interpolation
        val samps = 500
        val X_test_sampl = Array(1) { Array(samps) { FloatArray(6) } }

        for (j in 0 until numCol) {
            val resampledColumn =
                resampleByInterpolation(X_test[0].map { it[j] }.toFloatArray(), dataLen, samps)
            for (i in 0 until samps) {
                X_test_sampl[0][i][j] = resampledColumn[i]
            }
        }
        // Normalization
        val minDataset = FloatArray(numCol)
        val maxDataset = FloatArray(numCol)
        for (i in 0 until numCol) {
            val columnValues = X_test_sampl[0].map { it[i] }
            minDataset[i] = columnValues.minOrNull() ?: 0.0f
            maxDataset[i] = columnValues.maxOrNull() ?: 0.0f
        }
        for (i in 0 until numCol) {
            for (j in X_test_sampl[0].indices) {
                val filtered =
                    (X_test_sampl[0][j][i] - minDataset[i]) / (maxDataset[i] - minDataset[i])
                X_test_sampl[0][j][i] = if (filtered.isNaN()) 0.0f else filtered
            }
        }


        return predict(X_test_sampl, interpreter)

    }

    private fun resampleByInterpolation(signal: FloatArray, inputFs: Int, outputFs: Int): FloatArray {
        val scale: Float = outputFs.toFloat() / inputFs.toFloat()
        val n = round(signal.size * scale).toInt()

        val resampledSignal = FloatArray(n) { 0.0f }

        for (i in 0 until n) {
            val position = i.toFloat() / n.toFloat()
            val knownPosition = position * signal.size.toFloat()

            val lower = knownPosition.toInt()
            val upper = lower + 1

            if (upper >= signal.size) {
                resampledSignal[i] = signal[lower]
            } else {
                val fraction = knownPosition - lower.toFloat()
                resampledSignal[i] = signal[lower] * (1.0f - fraction) + signal[upper] * fraction
            }
        }

        return resampledSignal
    }

    private fun predict(xTestSampl: Array<Array<FloatArray>>, interpreter: Interpreter): String {
        val outputShape = intArrayOf(1, 25)
        // Flatten the input array to a flat FloatArray
        val flatInput = xTestSampl.flatMap { innerArray ->
            innerArray.flatMap { floatArray -> floatArray.toList() }
        }.toFloatArray()

        // Prepare input and output buffers
        val inputBuffer = ByteBuffer.allocateDirect(flatInput.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(flatInput)
        inputBuffer.position(0)

        val outputBuffer = ByteBuffer.allocateDirect(outputShape.reduce { acc, i -> acc * i } * 4)
            .order(ByteOrder.nativeOrder())

        // Run inference
        val startPredict = System.currentTimeMillis()
        interpreter.run(inputBuffer, outputBuffer)
        println("Time taken to predict: ${System.currentTimeMillis() - startPredict} ms")

        // Extract output
        val outputArray = FloatArray(outputShape.reduce { acc, i -> acc * i })
        outputBuffer.rewind()
        outputBuffer.asFloatBuffer().get(outputArray)
        val output = argmax(outputArray)
        println("The prediction for unlabel X_test is: $output")

        return output.toString()
    }

    private fun argmax(array: FloatArray): Int {
        var maxIndex = 0
        for (i in 1 until array.size) {
            if (array[i] > array[maxIndex]) {
                maxIndex = i
            }
        }
        return maxIndex
    }
        //////////// API /////////////
        fun saveStringToAPI(dataString: String, topic: String) {
            val requestBody = SaveAPIRequestBody(dataString)

            val call: Call<YourResponse> = when (topic) {
                "smart_watch_acquisition" -> apiService.postAcquireSensorsData(requestBody)
                "smart_watch_gesture" -> apiService.postAcquireGestureData(requestBody)
                else -> return
            }

            // Stampiamo manualmente gli headers definiti nelle funzioni API
            val headers = listOf("Content-Type: application/json").joinToString("; ")

            Log.d("API", "Sending Request - Headers: $headers; Body: ${dataString}")

            call.enqueue(object : Callback<YourResponse> {
                override fun onResponse(call: Call<YourResponse>, response: Response<YourResponse>) {
                    Log.d("API", "Data sent successfully: ${response.body()?.response}")
                }

                override fun onFailure(call: Call<YourResponse>, t: Throwable) {
                    Log.e("API", "Error: ${t.localizedMessage}")
                }
            })
        }


    /*private fun printFloatArray(array: SensorDataPacket): String {
        val stringBuilder = StringBuilder()

        stringBuilder.append(array.deviceId)
        stringBuilder.append(",")
        stringBuilder.append(array.timestamp)
        stringBuilder.append(",")

        for (subArray in array.data) {
            for ((index, value) in subArray.withIndex()) {
                stringBuilder.append(value)
                if (index != subArray.size - 1) {
                    stringBuilder.append(",")
                }
            }
            stringBuilder.append("\n")
        }

        return stringBuilder.toString().trim()
    }*/
//        //////////// API /////////////
}