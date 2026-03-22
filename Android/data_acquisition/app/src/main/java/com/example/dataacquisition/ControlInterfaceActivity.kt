package com.example.dataacquisition

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.provider.Settings
import java.util.UUID
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.*
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.slider.Slider
import kotlinx.coroutines.*
import org.tensorflow.lite.Interpreter
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.FloatArray
import kotlin.math.max
import kotlin.math.roundToInt

import org.tensorflow.lite.support.common.FileUtil
import kotlin.math.round


@RequiresApi(Build.VERSION_CODES.S)
class ControlInterfaceActivity : Activity(), SensorEventListener {


    // SHARED PREFERENCES
    private lateinit var androidId: String

    // ID
    private lateinit var uuid: String

    var manufacturer = Build.MANUFACTURER.capitalize()
    var model = Build.MODEL


    // VIEW
    private lateinit var mPlayButton: ImageButton
    private lateinit var mResultView: TextView
    private lateinit var mTimerView: TextView
    private lateinit var backgroundRoot: View

    // FLAG
    private var isPlay = false

    // SENSOR FIELDS
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null
    private var magnetometer: Sensor? = null
    private var pose6dof: Sensor? = null
    private var humidity: Sensor? = null
    private var temperature: Sensor? = null
    private var barometer: Sensor? = null
    private var lightSensor: Sensor? = null
    private var gravity: Sensor? = null
    private var heartRate: Sensor? = null

    private var allData = mutableListOf<DataFromSensors>()
    private var allImuData = mutableListOf<ImuData>()

    private var currentAccelerometerValues: FloatArray? = null
    private var currentGyroscopeValues: FloatArray? = null
    private var currentMagnetometerValues: FloatArray? = null
    private var currentPose6dofValues: FloatArray? = null
    private var currentHumidityValue: Float? = null
    private var currentTemperatureValue: Float? = null
    private var currentBarometerValue: Float? = null
    private var currentAltimeterValue: Float? = null
    private var currentLightSensorValues: Float? = null
    private var currentGravityValues: FloatArray? = null
    private var currentHeartRateValues: Float? = null

    private var count = 0
    private lateinit var interpreter: Interpreter

    private var primaryTimestamp: MutableList<Long> ? = mutableListOf()
    private var accelerometerList: MutableList<SensorData> ? = mutableListOf()
    private var gyroscopeList: MutableList<SensorData> ? = mutableListOf()
    private var magnetometerList: MutableList<SensorData> ? = mutableListOf()
    private var gravityList: MutableList<SensorData> ? = mutableListOf()
    private var compassList: MutableList<SingleSensorData> ? = mutableListOf()
    private var heartRateList: MutableList<SingleSensorData> ? = mutableListOf()
    private var secondaryTimestamp: MutableList<Long> ? = mutableListOf()
    private var humidityList: MutableList<SingleSensorData> ? = mutableListOf()
    private var temperatureList: MutableList<SingleSensorData> ? = mutableListOf()
    private var barometerList: MutableList<SingleSensorData> ? = mutableListOf()
    private var altimeterList: MutableList<SingleSensorData> ? = mutableListOf()
    private var lightSensorList: MutableList<SingleSensorData> ? = mutableListOf()
    private var poseTimestamp: MutableList<Long> ? = mutableListOf()
    private var poseList: MutableList<PoseData> ? = mutableListOf()

    private val gestures = listOf(
        "Blue",                    // 0
        "Green",                   // 1
        "Red",                     // 2
        "Yellow",                  // 3
        "Light Blue",              // 4
        "Purple",                  // 5
        "OFF",                     // 6
        "Brightness low level",    // 7
        "Brightness medium level", // 8
        "Brightness high level",   // 9
        "Brightness max level",    //10
        "Flash Light",             //11
        "Go",                      //12
        "Come",                    //13
        "No",                      //14
        "Stop",                    //15
        "Turn left",               //16
        "Turn right",              //17
        "Stand up",                //18
        "Sit down",                //19
        "Rotate",                  //20
        "Turn over",               //21
        "Hand up",                 //22
        "Hello",                   //23
        "Random (Rejection Class)",//24
        "Hand stopped"             //25
    )

    private var threshold_delta: Float = 10F

    private lateinit var dataHelpers: DataHelper
    private var handler: Handler? = null
    private var runnable: Runnable? = null
    private var sensorReadingInterval = 5L // 5L  // intervallo di lettura in millisecondi

    private val REQUEST_BODY_SENSORS_PERMISSION = 1001

    private var initialTimestamp = System.currentTimeMillis()
    private var flagOfInitialAcquisition = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val mlmodel = FileUtil.loadMappedFile(this,"model.tflite")
        interpreter = Interpreter(mlmodel)

        // Identifiation
        androidId = getAndroidId(this)
        uuid = getOrCreateUUID(this)

        checkAndRequestBodySensorPermission()

        // view initializations
        setContentView(R.layout.control_interface)
        mPlayButton = findViewById(R.id.playPause);
        mPlayButton.setBackgroundResource(android.R.drawable.presence_offline);
        mResultView = findViewById(R.id.resultView);
        mTimerView = findViewById(R.id.timerView)
        val background = findViewById<View>(R.id.mainView)

        // Find the root view
        backgroundRoot = background.rootView

        // Sensor manager
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        dataHelpers = DataHelper()

        // Registration of Sensors
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        pose6dof = sensorManager.getDefaultSensor(Sensor.TYPE_POSE_6DOF)
        humidity = sensorManager.getDefaultSensor(Sensor.TYPE_RELATIVE_HUMIDITY)
        temperature = sensorManager.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE)
        barometer = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
        gravity = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
        heartRate = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)

        // ============ New lines ============
        mPlayButton.setBackgroundResource(android.R.drawable.ic_media_play)
        mPlayButton.setOnClickListener(mTogglePlayButton)

        backgroundRoot.setBackgroundColor(resources.getColor(android.R.color.black))
        mResultView.setTextColor(resources.getColor(android.R.color.white))
        mTimerView.setTextColor(resources.getColor(android.R.color.white))

        val slider = findViewById<Slider>(R.id.slider_interval)

        slider.addOnChangeListener { _, value, _ ->
            threshold_delta = value
        }

    }

    // Start a thread that read the measurements
    private fun startSensorReading() {
        handler = Handler(Looper.getMainLooper())
        flagOfInitialAcquisition = true
        runnable = object : Runnable {
            override fun run() {
                // Leggi i valori dell'accelerometro e del giroscopio
                if(isPlay) {
                    readSensorValues()
                }
                // Esegui la lettura ogni 5 ms
                handler?.postDelayed(this, sensorReadingInterval)
            }
        }
        handler?.post(runnable!!)
    }

    // read the data sensors
    private fun readSensorValues() {

        // read last values from sensors
        val accelerometerValues = currentAccelerometerValues ?: floatArrayOf(0f, 0f, 0f)
        val gyroscopeValues = currentGyroscopeValues ?: floatArrayOf(0f, 0f, 0f)
        val magnetometerValues = currentMagnetometerValues ?: floatArrayOf(0f, 0f, 0f)
        val poseValues = currentPose6dofValues ?: floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f)
        val humidityValue = currentHumidityValue ?: 0f
        val temperatureValue = currentTemperatureValue ?: 0f
        val barometerValue = currentBarometerValue ?: 0f
        val altimeterValue = currentAltimeterValue ?: 0f
        val lightSensorValue = currentLightSensorValues ?: 0f
        val gravityValues = currentGravityValues ?: floatArrayOf(0f, 0f, 0f)
        val heartRateValue = currentHeartRateValues ?: 0f

        var compassDirection: Float = 190f   // TODO INFORMARE DI QUESTA SOLUZIONE IL PROF.
        if(currentGravityValues!= null && currentMagnetometerValues!= null) {
            val R = FloatArray(9)
            val I = FloatArray(9)
            if (SensorManager.getRotationMatrix(R, I, gravityValues, magnetometerValues)) {
                val orientation = FloatArray(3)
                SensorManager.getOrientation(R, orientation)
                compassDirection = Math.toDegrees(orientation[0].toDouble()).toFloat()
            }
        }

        if (flagOfInitialAcquisition) {
            initialTimestamp = System.currentTimeMillis()
            flagOfInitialAcquisition = false
        }
        val istant = System.currentTimeMillis()
        val deltaTimeSeconds: Float = (istant - initialTimestamp) / 1000f
        val timestampAcquisition = SingleSensorData(deltaTimeSeconds)

        val accelerometerData = SensorData(accelerometerValues[0], accelerometerValues[1], accelerometerValues[2])
        val gyroscopeData = SensorData(gyroscopeValues[0], gyroscopeValues[1], gyroscopeValues[2])
        val magnetometerData = SensorData(magnetometerValues[0], magnetometerValues[1], magnetometerValues[2])
        val gravityData = SensorData(gravityValues[0], gravityValues[1], gravityValues[2])
        val compassData = SingleSensorData(compassDirection)
        val poseData = PoseData(poseValues[0],poseValues[1],poseValues[2],poseValues[3],poseValues[4],poseValues[5],poseValues[6])
        val humidityData = SingleSensorData(humidityValue)
        val temperatureData = SingleSensorData(temperatureValue)
        val barometerData = SingleSensorData(barometerValue)
        val altimeterData = SingleSensorData(altimeterValue)
        val lightSensorData = SingleSensorData(lightSensorValue)

        val heartRateData =  SingleSensorData(heartRateValue)


        val dataFromSensors = DataFromSensors(
            istant,
            accelerometerData,
            gyroscopeData,
            magnetometerData,
            gravityData,
            compassData,
            poseData,
            humidityData,
            temperatureData,
            barometerData,
            altimeterData,
            lightSensorData
        )

        val imuData = ImuData(
            accelerometerData,
            gyroscopeData
        )


        allData.add(dataFromSensors)

        allImuData.add(imuData)

        var deviceId = "$androidId|$uuid"
        var device = getDeviceCategory(this)

        /*val organizedSensorData = SmartObjectData(
            identifier = IdentifierData(
                token = "example_token",     // Sostituisci con la tua variabile se esiste
                id = deviceId,
                device = device,
                model = "$manufacturer|$model"
            ),
            sensorData = dataFromSensors
        )*/

        val remainingTime = max(0, (threshold_delta - deltaTimeSeconds).roundToInt())
        mTimerView.text = "Gesture Timer: $remainingTime"


        if(deltaTimeSeconds>=threshold_delta){//allData.size >= 500) {
            val bufferStop: Array<FloatArray>?
            //val startData = System.currentTimeMillis()
            //TODO RIMAPPARE LA FUNZIONE convertImuToListOfFloatArray
            val X_test_raw: Array<FloatArray> = dataHelpers.convertImuToListOfFloatArray(allImuData);
            bufferStop = X_test_raw.takeLast(50).toTypedArray()
            count = dataHelpers.getHandStoppedCounter(bufferStop, count)
             // TODO: Cosa vuol dire 37 ?
            if(count >= 37) {
                //pauseMonitoring()
                var gestureRecognized = ModelHelpers.processData(
                    X_test_raw,
                    istant,
                    X_test_raw.size,
                    interpreter
                )

                var gestureDataObtained = DataFromGesture(
                    istant,
                    gestureRecognized
                )

                //println(istant)

                val gestureData = SmartGestureData(
                    identifier = IdentifierData(
                        //token = "example_token",     // Sostituisci con la tua variabile se esiste
                        id = deviceId,
                        device = device,
                        model = "$manufacturer|$model"
                    ),
                    gestureData = gestureDataObtained
                )

                val jsonString = Json.encodeToString(gestureData)
                println(jsonString)

                //val file = File(this.filesDir, "prova.json")
                //file.writeText(jsonString)

                ModelHelpers.saveStringToAPI(jsonString,"smart_watch_gesture")

                ///////// USED ONLY WHEN DATA ARE SAVED AS DEBUG
                // ModelHelpers.saveDataToAPI(X_test_raw)
                // ModelHelpers.saveDataToAPI(packet) #### commented

                // Clean variable imu
                count = 0

                allData.clear()

                // the first time that the gesture is registered another interval time is started
                flagOfInitialAcquisition = true

                val tempGesture:String = gestures[gestureRecognized.toInt()]
                mResultView.text = "Gesture: $tempGesture"//"Data transfer completed"

                vibratePhone(VibrationEffect.Composition.PRIMITIVE_SPIN)
            }
        }
    }

    // sampling data from sensor
    private fun startMonitoring() {
        //super.onResume()
        // sampling every 5 millisecondi from the last data
        accelerometer?.let { sensorManager.registerListener(this, it, 50000) }
        gyroscope?.let { sensorManager.registerListener(this, it, 50000) }
        magnetometer?.let { sensorManager.registerListener(this, it, 50000) }
        gravity?.let { sensorManager.registerListener(this, it, 50000) }
        heartRate?.let { sensorManager.registerListener(this, it, 50000) }
        pose6dof?.let { sensorManager.registerListener(this, it, 50000) }
        barometer?.let { sensorManager.registerListener(this, it, 1000000) }
        temperature?.let { sensorManager.registerListener(this, it, 1000000) }
        humidity?.let { sensorManager.registerListener(this, it, 1000000) }
        lightSensor?.let { sensorManager.registerListener(this, it, 1000000) }


        startSensorReading()
        mPlayButton.setBackgroundResource(android.R.drawable.ic_media_pause)

        // Set the color
        backgroundRoot.setBackgroundColor(resources.getColor(android.R.color.holo_red_light))
        mResultView.setTextColor(resources.getColor(android.R.color.black))
        mTimerView.setTextColor(resources.getColor(android.R.color.black))
        //magnetometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
    }

    // pause the acquisition
    private fun pauseMonitoring() {
        // Deregistra i listener del sensore
        //super.onPause()
        handler?.removeCallbacksAndMessages(runnable)
        handler = null
        runnable = null
        isPlay = false
        sensorManager.unregisterListener(this)

        mPlayButton.setBackgroundResource(android.R.drawable.ic_media_play);
        vibratePhone(VibrationEffect.Composition.PRIMITIVE_SPIN)
        backgroundRoot.setBackgroundColor(resources.getColor(android.R.color.black))
        mResultView.setTextColor(resources.getColor(android.R.color.white))
        mTimerView.setTextColor(resources.getColor(android.R.color.white))
        mResultView.text="Tap to fetch gestures"
        mTimerView.text="Set Gesture Timer:"
    }

    // change value based on event
    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_LINEAR_ACCELERATION -> {
                currentAccelerometerValues = event.values
            }
            Sensor.TYPE_GYROSCOPE -> {
                currentGyroscopeValues = event.values
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                currentMagnetometerValues = event.values
            }
            Sensor.TYPE_PRESSURE -> {
                currentBarometerValue = event.values.getOrNull(0)
                currentAltimeterValue = SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, currentBarometerValue ?: 0f)
            }
            Sensor.TYPE_GRAVITY -> {
                currentGravityValues = event.values
            }
            Sensor.TYPE_HEART_RATE -> {
                currentHeartRateValues = event.values.getOrNull(0)
            }
            Sensor.TYPE_AMBIENT_TEMPERATURE -> {
                currentTemperatureValue = event.values.getOrNull(0)
            }
            Sensor.TYPE_LIGHT -> {
                currentLightSensorValues = event.values.getOrNull(0)
            }
            // Sensor.IR not integrated
            Sensor.TYPE_POSE_6DOF -> { // non disponibile su Wear OS.
                currentPose6dofValues = event.values
            }
            Sensor.TYPE_RELATIVE_HUMIDITY -> {
                currentHumidityValue = event.values.getOrNull(0)
            }
        }
    }

    // Alerts for change of accuracy. Actually no alerts.
    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {

    }

    @OptIn(DelicateCoroutinesApi::class)
    private val mTogglePlayButton = View.OnClickListener { v ->
        // change your button background
        if(!isPlay) {
            v.isEnabled = false // Disable the button temporarily to prevent multiple clicks
            mResultView.text = "Wait"
            mTimerView.text = "Setting Timer:"
            // Delay execution by 3 seconds
            startMonitoring()
            GlobalScope.launch(Dispatchers.Main) {
                delay(2000) // Delay for 2 seconds
                // Vibrate the smartwatch
                vibratePhone(VibrationEffect.Composition.PRIMITIVE_SPIN)
                v.isEnabled = true // Enable the button after the delay

                // Perform button action after the delay
                isPlay = !isPlay
                v.setBackgroundResource(android.R.drawable.ic_media_pause)
                mResultView.text = "Analyzing"
                mResultView.setTextColor(resources.getColor(android.R.color.black))
                mTimerView.setTextColor(resources.getColor(android.R.color.black))
                backgroundRoot.setBackgroundColor(resources.getColor(android.R.color.holo_green_dark))

            }
        } else {
            isPlay = !isPlay
            v.setBackgroundResource(android.R.drawable.ic_media_play)
            pauseMonitoring()
        }

    }

    private val vibratorManager: VibratorManager by lazy {
        getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
    }

    private fun vibratePhone(effectId: Int) {
        if (vibratorManager.defaultVibrator.areAllPrimitivesSupported(effectId)) {
            vibratorManager.vibrate(
                CombinedVibration.createParallel(
                    VibrationEffect.startComposition()
                        .addPrimitive(effectId)
                        .compose()
                )
            )
        }
    }

    // ID METHODS
    // obtain ANDROID_ID
    fun getAndroidId(context: Context): String {
        return Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        )
    }

    // obtain or generate UUID persistent saving it in SharedPreferences
    fun getOrCreateUUID(context: Context): String {
        val sharedPrefs = context.getSharedPreferences("device_prefs", MODE_PRIVATE)
        var uuid = sharedPrefs.getString("unique_uuid", null)

        if (uuid == null) {
            uuid = UUID.randomUUID().toString()
            sharedPrefs.edit().putString("unique_uuid", uuid).apply()
        }

        return uuid
    }

    private fun checkAndRequestBodySensorPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.BODY_SENSORS),
                REQUEST_BODY_SENSORS_PERMISSION
            )
        } else {
            // ✅ Permission already granted, proceed with sensor access
            // startHeartRateSensor()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_BODY_SENSORS_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // ✅ Permission granted
                //startHeartRateSensor()
            } else {
                // ❌ Permission denied
                Toast.makeText(this, "Heart rate permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun getDeviceCategory(context: Context): String {
        val packageManager = context.packageManager

        // Check for smartwatch
        if (packageManager.hasSystemFeature("android.hardware.type.watch")) {
            return "smartwatch"
        }

        // Check for TV
        if (packageManager.hasSystemFeature("android.hardware.type.television")) {
            return "tv"
        }

        // Check for automotive
        if (packageManager.hasSystemFeature("android.hardware.type.automotive")) {
            return "auto"
        }

        // Check for tablet (based on screen size)
        val screenLayout = context.resources.configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK
        if (screenLayout >= Configuration.SCREENLAYOUT_SIZE_LARGE) {
            return "tablet"
        }

        // Default to phone
        return "smartphone"
    }

}