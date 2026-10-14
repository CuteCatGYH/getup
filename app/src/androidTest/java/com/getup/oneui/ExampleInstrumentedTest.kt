package com.getup.oneui

import android.util.Log
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.window.area.WindowAreaCapability
import androidx.window.area.WindowAreaController
import androidx.window.area.WindowAreaInfo
import androidx.window.area.WindowAreaPresentationSessionCallback
import androidx.window.area.WindowAreaSessionPresenter
import androidx.window.core.ExperimentalWindowApi
import com.getup.oneui.model.SecondaryDisplayConfig
import com.getup.oneui.ui.presentation.PresentationScreen
import com.getup.oneui.ui.theme.双屏并发Theme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    private val TAG = "DualScreenTest"

    @OptIn(ExperimentalWindowApi::class)
    @Test
    fun testWindowAreaComposePresentation() {
        runBlocking(Dispatchers.Default) {
            val scenario = ActivityScenario.launch(MainActivity::class.java)

            val controller = WindowAreaController.getOrCreate()
            val infos = controller.windowAreaInfos.first()
            val rearInfo = infos.firstOrNull { it.type == WindowAreaInfo.Type.TYPE_REAR_FACING }
            Log.d(TAG, "rearInfo: $rearInfo")
            val cap = rearInfo?.getCapability(WindowAreaCapability.Operation.OPERATION_PRESENT_ON_AREA)

            if (rearInfo != null && cap?.status == WindowAreaCapability.Status.WINDOW_AREA_STATUS_AVAILABLE) {
                scenario.onActivity { activity ->
                    val callback = object : WindowAreaPresentationSessionCallback {
                        override fun onSessionStarted(session: WindowAreaSessionPresenter) {
                            Log.d(TAG, "WindowAreaPresentationSessionCallback: onSessionStarted! session=$session")
                            val composeView = ComposeView(session.context).apply {
                                setViewTreeLifecycleOwner(activity)
                                setViewTreeSavedStateRegistryOwner(activity)
                                setViewTreeViewModelStoreOwner(activity)
                                setContent {
                                    双屏并发Theme {
                                        PresentationScreen(config = SecondaryDisplayConfig())
                                    }
                                }
                            }
                            session.setContentView(composeView)
                            Log.d(TAG, "session.setContentView(ComposeView) successfully attached!")
                        }

                        override fun onSessionEnded(t: Throwable?) {
                            Log.d(TAG, "WindowAreaPresentationSessionCallback: onSessionEnded, throwable=$t")
                        }

                        override fun onContainerVisibilityChanged(isVisible: Boolean) {
                            Log.d(TAG, "WindowAreaPresentationSessionCallback: onContainerVisibilityChanged, isVisible=$isVisible")
                        }
                    }

                    controller.presentContentOnWindowArea(
                        token = rearInfo.token,
                        activity = activity,
                        executor = activity.mainExecutor,
                        windowAreaPresentationSessionCallback = callback
                    )
                }
            }

            Thread.sleep(4000)
            scenario.close()
        }
    }
}
