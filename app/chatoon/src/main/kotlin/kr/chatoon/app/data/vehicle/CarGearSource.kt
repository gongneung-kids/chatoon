package kr.chatoon.app.data.vehicle

import android.car.Car
import android.car.VehicleGear
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.os.Build
import android.util.Log
import kr.chatoon.app.domain.vehicle.Gear

/**
 * 기어 상태를 Android Automotive 표준 속성(GEAR_SELECTION)으로 받는다.
 *
 * Pleos Vehicle SDK의 기어 API는 사용 중단 예정이며 SDK가 이 표준 속성을 쓰라고 안내한다.
 */
internal class CarGearSource(private val context: Context) {

    private var car: Car? = null
    private var propertyManager: CarPropertyManager? = null
    private var callback: CarPropertyManager.CarPropertyEventCallback? = null

    /** 구독을 시작한다. 이미 시작했으면 아무 일도 하지 않는다. */
    fun start(onGear: (Gear?) -> Unit) {
        if (callback != null) return
        val connected = Car.createCar(context) ?: run {
            Log.w(TAG, "Car service unavailable; gear state stays unknown")
            return
        }
        val manager = connected.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager ?: run {
            Log.w(TAG, "CarPropertyManager unavailable; gear state stays unknown")
            connected.disconnect()
            return
        }
        val eventCallback = object : CarPropertyManager.CarPropertyEventCallback {
            override fun onChangeEvent(value: CarPropertyValue<*>) {
                onGear(mapGear(value.value as? Int))
            }

            override fun onErrorEvent(propertyId: Int, areaId: Int) {
                Log.w(TAG, "Gear property error: area=$areaId")
                onGear(null)
            }
        }
        try {
            subscribe(manager, eventCallback)
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing permission for GEAR_SELECTION", e)
            connected.disconnect()
            return
        }
        car = connected
        propertyManager = manager
        callback = eventCallback
    }

    fun stop() {
        val manager = propertyManager
        callback?.let { if (manager != null) unsubscribe(manager, it) }
        car?.disconnect()
        callback = null
        propertyManager = null
        car = null
    }

    // Android 15(API 35)부터 구독 함수가 바뀌었다. Pleos Connect v2.1.0은 API 34라 두 방식을 모두 지원한다.
    private fun subscribe(manager: CarPropertyManager, cb: CarPropertyManager.CarPropertyEventCallback) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            manager.subscribePropertyEvents(VehiclePropertyIds.GEAR_SELECTION, CarPropertyManager.SENSOR_RATE_ONCHANGE, cb)
        } else {
            @Suppress("DEPRECATION")
            manager.registerCallback(cb, VehiclePropertyIds.GEAR_SELECTION, CarPropertyManager.SENSOR_RATE_ONCHANGE)
        }
    }

    private fun unsubscribe(manager: CarPropertyManager, cb: CarPropertyManager.CarPropertyEventCallback) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            manager.unsubscribePropertyEvents(cb)
        } else {
            @Suppress("DEPRECATION")
            manager.unregisterCallback(cb)
        }
    }

    private fun mapGear(raw: Int?): Gear? = when (raw) {
        null, VehicleGear.GEAR_UNKNOWN -> null
        VehicleGear.GEAR_PARK -> Gear.PARK
        VehicleGear.GEAR_REVERSE -> Gear.REVERSE
        VehicleGear.GEAR_NEUTRAL -> Gear.NEUTRAL
        // D와 수동 단(1~9)은 모두 전진 주행으로 본다.
        else -> Gear.DRIVE
    }

    private companion object {
        const val TAG = "Chatoon/Gear"
    }
}
