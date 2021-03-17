package com.getbouncer.scan.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Size
import androidx.lifecycle.LifecycleOwner
import com.getbouncer.scan.framework.TrackedImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow

/**
 * A flow for scanning something. This manages the callbacks and lifecycle of the flow.
 */
interface ScanFlow {

    /**
     * Start the image processing flow for scanning a card.
     *
     * @param context: The context used to download analyzers if needed
     * @param imageStream: The flow of images to process
     * @param previewSize: The size of the preview frame where the view finder is located
     * @param viewFinder: The location of the view finder in the previewSize
     * @param lifecycleOwner: The activity that owns this flow. The flow will pause if the activity
     * is paused
     * @param coroutineScope: The coroutine scope used to run async tasks for this flow
     */
    fun startFlow(
        context: Context,
        imageStream: Flow<TrackedImage<Bitmap>>,
        previewSize: Size,
        viewFinder: Rect,
        lifecycleOwner: LifecycleOwner,
        coroutineScope: CoroutineScope
    )

    /**
     * In the event that the scan cannot complete, halt the flow to halt analyzers and free up CPU and memory.
     */
    fun cancelFlow()
}
