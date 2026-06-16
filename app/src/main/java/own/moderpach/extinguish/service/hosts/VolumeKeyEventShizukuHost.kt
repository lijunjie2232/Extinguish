package own.moderpach.extinguish.service.hosts

import android.content.Context
import android.util.Log
import extinguish.ipc.result.EventResult
import extinguish.shizuku_service.IEventsListener
import extinguish.shizuku_service.IEventsProvider
import own.moderpach.extinguish.BuildConfig

private const val TAG = "VolumeKeyEventShizukuHost"

class VolumeKeyEventShizukuHost(
    private val owner: Context,
    service: IEventsProvider,
    var onKeyEvent: () -> Unit = {},
) : AbstractEventHost(service) {

    override fun createListener(): IEventsListener {
        return object : IEventsListener.Stub() {
            override fun onEvent(event: EventResult) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "get event - $event")
                }
                if (isAwake && event.v0 == EVENT_TYPE_INPUT && (event.v1 == EVENT_VOLUME_DOWN || event.v1 == EVENT_VOLUME_UP) && event.v2 == EVENT_VALUE_RELEASE) {
                    onKeyEvent()
                }
            }
        }
    }

}
