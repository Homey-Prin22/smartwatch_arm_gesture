package com.example.dataacquisition;

import kotlinx.serialization.Serializable

data class ImuData(
     val accelerometer: SensorData, // 3 values
     val gyroscope: SensorData // 3 values
)

@Serializable
data class SmartObjectData(
     val identifier: IdentifierData,
     val sensorData: DataFromSensors
)

@Serializable
data class IdentifierData(
     //val token: String,
     val id: String,
     val device: String,
     val model: String
)

@Serializable
data class DataFromSensors(
     val timestamp: Long, // 1 value
     val accelerometer: SensorData, // 3 values
     val gyroscope: SensorData, // 3 values
     val magnetometer: SensorData, // 3 values
     val gravity: SensorData, // 3 values
     val compass: SingleSensorData,  // 1 value
     val pose: PoseData, // 7 values
     val humidity: SingleSensorData, // 1 value
     var temperature: SingleSensorData, // 1 value
     var barometer: SingleSensorData, // 1 value
     var altimeter: SingleSensorData, // 1 value
     val lightSensor: SingleSensorData // 1 value
)

@Serializable
data class SensorData (
     val x: Float,
     val y: Float,
     val z: Float
)

@Serializable
data class SingleSensorData (
     val value: Float
)

@Serializable
data class PoseData (
     val x: Float,
     val y: Float,
     val z: Float,
     val qx: Float,
     val qy: Float,
     val qz: Float,
     val qw: Float
)

@Serializable
data class SmartGestureData(
     val identifier: IdentifierData,
     val gestureData: DataFromGesture
)

@Serializable
data class DataFromGesture(
     val timestampGestureData: Long, // 1 value
     val gesture: String
)

