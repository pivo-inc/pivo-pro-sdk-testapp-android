package app.pivo.android.prosdkdemo.camera

import android.media.Image

/**
 * Created by murodjon on 2020/04/10
 */
interface ICameraCallback {
    fun onCameraOpened()
    fun onCameraError()
    fun onCameraDisconnect()
    fun onProcessingFrame(image: Image, width:Int, height:Int, orientation:Int, frontCamera:Boolean)
}