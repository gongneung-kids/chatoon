package kr.chatoon.app.data.vehicle

import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log

/**
 * 차량 속도(Android Automotive 표준 PERF_VEHICLE_SPEED) 샘플을 받는다(명세 §15·§16).
 * 샘플은 게이트웨이의 급가속·급감속 감지기로만 가고 저장하거나 밖으로 내보내지 않는다.
 *
 * 속도 권한(CAR_SPEED)은 사용자가 허용해야 한다. 없으면 false를 돌려주고, 그 횟수들은 모름이 된다.
 */
internal class CarSpeedSource(private val context: Context) {

    private var car: Car? = null
    private var propertyManager: CarPropertyManager? = null
    private var callback: CarPropertyManager.CarPropertyEventCallback? = null

    val running: Boolean get() = callback != null

    /**
     * 구독을 시작한다. 권한이 없거나 연결할 수 없으면 false. 이미 시작했으면 true.
     * [onSample]: 속도(m/s)와 그 값의 경과 시각(나노초). [onLost]: 신호 오류(이후 샘플이 다시 오면 [onSample]부터 다시).
     */
    fun start(onSample: (metersPerSecond: Float, elapsedNanos: Long) -> Unit, onLost: () -> Unit): Boolean {
        if (callback != null) return true
        if (context.checkSelfPermission(PERMISSION) != PackageManager.PERMISSION_GRANTED) return false
        val connected = Car.createCar(context) ?: run {
            Log.w(TAG, "Car service unavailable; speed-based counts stay unknown")
            return false
        }
        val manager = connected.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager ?: run {
            connected.disconnect()
            return false
        }
        val eventCallback = object : CarPropertyManager.CarPropertyEventCallback {
            override fun onChangeEvent(value: CarPropertyValue<*>) {
                val speed = value.value as? Float ?: return
                onSample(speed, value.timestamp)
            }

            override fun onErrorEvent(propertyId: Int, areaId: Int) {
                Log.w(TAG, "Speed property error")
                onLost()
            }
        }
        val rate = try {
            manager.getCarPropertyConfig(VehiclePropertyIds.PERF_VEHICLE_SPEED)?.maxSampleRate?.takeIf { it > 0f }?.coerceAtMost(SAMPLE_RATE_HZ)
                ?: SAMPLE_RATE_HZ
        } catch (e: SecurityException) {
            Log.w(TAG, "No access to the speed property", e)
            connected.disconnect()
            return false
        }
        val subscribed = try {
            @Suppress("DEPRECATION") // API 34(Pleos Connect v2.1.0)에서는 이 방식이 표준이다.
            manager.registerCallback(eventCallback, VehiclePropertyIds.PERF_VEHICLE_SPEED, rate)
        } catch (e: SecurityException) {
            Log.w(TAG, "Missing permission for PERF_VEHICLE_SPEED", e)
            false
        }
        if (!subscribed) {
            connected.disconnect()
            return false
        }
        car = connected
        propertyManager = manager
        callback = eventCallback
        return true
    }

    fun stop() {
        val manager = propertyManager
        callback?.let { if (manager != null) manager.unregisterCallback(it) }
        car?.disconnect()
        callback = null
        propertyManager = null
        car = null
    }

    companion object {
        const val PERMISSION = "android.car.permission.CAR_SPEED"
        private const val TAG = "Chatoon/Speed"

        /** 1초 변화율을 재기에 충분한 빈도. 차량이 더 낮게만 지원하면 그 값을 쓴다. */
        private const val SAMPLE_RATE_HZ = 10f
    }
}
