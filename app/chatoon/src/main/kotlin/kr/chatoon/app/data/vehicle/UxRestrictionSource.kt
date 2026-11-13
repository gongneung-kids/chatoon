package kr.chatoon.app.data.vehicle

import android.car.Car
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.util.Log

/**
 * 플랫폼 운전 제한(CarUxRestrictions)을 구독한다. Pleos 주행 규제 개발 가이드가 요구하는 방식이다.
 * 권한이 필요 없다. 서비스를 못 얻으면 아무것도 보내지 않는다(호출부는 "모름"으로 다룬다).
 */
class UxRestrictionSource(private val context: Context) {

    private var car: Car? = null
    private var manager: CarUxRestrictionsManager? = null

    fun start(onChange: (requiresOptimization: Boolean) -> Unit) {
        try {
            val connected = Car.createCar(context) ?: return
            car = connected
            val m = connected.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager ?: return
            manager = m
            val deliver = { restrictions: CarUxRestrictions ->
                Log.i(TAG, "Driving restriction: ${restrictions.isRequiresDistractionOptimization}")
                onChange(restrictions.isRequiresDistractionOptimization)
            }
            m.registerListener { deliver(it) }
            m.currentCarUxRestrictions?.let(deliver)
        } catch (e: RuntimeException) {
            Log.w(TAG, "CarUxRestrictions unavailable", e)
        }
    }

    fun stop() {
        runCatching { manager?.unregisterListener() }
        runCatching { car?.disconnect() }
        manager = null
        car = null
    }

    private companion object {
        const val TAG = "Chatoon/UxRestriction"
    }
}
