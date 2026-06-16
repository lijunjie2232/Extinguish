package own.moderpach.extinguish.service.hosts

import extinguish.shizuku_service.IEventsListener
import extinguish.shizuku_service.IEventsProvider

abstract class AbstractEventHost(
    protected val service: IEventsProvider
) {
    var isRegister = false
        private set
    var isAwake = false
        protected set

    protected abstract fun createListener(): IEventsListener

    private val listener by lazy { createListener() }

    fun register() {
        if (!isRegister) {
            isRegister = true
            service.registerListener(listener)
            isAwake = true
        }
    }

    fun unregister() {
        if (isRegister) {
            isRegister = false
            service.unregisterListener(listener)
            isAwake = false
        }
    }

    fun sleep() {
        isAwake = false
    }

    fun wake() {
        isAwake = true
    }
}
