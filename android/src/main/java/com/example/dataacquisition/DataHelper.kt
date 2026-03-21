package com.example.dataacquisition

import kotlin.math.abs

class DataHelper() {
    val calibration: Double = 0.1

    /*fun convertMeasurementsToListOfFloatArray(data: List<DataFromSensors>): Array<FloatArray> {
        return data.map { d ->
            floatArrayOf(
                d.timestamp.value,
                d.accelerometer.x, d.accelerometer.y, d.accelerometer.z,
                d.gyroscope.x, d.gyroscope.y, d.gyroscope.z,
                d.magnetometer.x, d.magnetometer.y, d.magnetometer.z,
                d.pose.x,d.pose.y,d.pose.z,d.pose.qx,d.pose.qy,d.pose.qz,d.pose.qw,
                d.humidity.value,
                d.temperature.value,
                d.barometer.value,
                d.altimeter.value,
                d.lightSensor.value,
                d.gravity.x, d.gravity.y, d.gravity.z
            )
        }.toTypedArray()
    }*/

    fun convertImuToListOfFloatArray(data: List<ImuData>): Array<FloatArray> {
        return data.map { d ->
            floatArrayOf(
                d.accelerometer.x, d.accelerometer.y, d.accelerometer.z,
                d.gyroscope.x, d.gyroscope.y, d.gyroscope.z
            )
        }.toTypedArray()
    }

    fun getHandStoppedCounter(data: Array<FloatArray>, currentCount: Int): Int {
        var c = currentCount
        for (j in 0 until data.size - 1) {
            val diffArray = data[data.size - (j + 1)].zip(data[data.size - (j + 2)], Float::minus)
            if (diffArray.map { abs(it) }.all { it <= calibration }) {
                c++
            } else {
                if (c > 0) {
                    c--
                }
            }
        }
        return c
    }
}