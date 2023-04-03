# Pivo Pro SDK Android test application

This is an android test application for Pro Pivo SDK. This application will allow to control the Pivo rotator.

## Before you begin

Please visit [Pivo developer website](https://developer.pivo.app/) and generate the license file to include it into your project. 

## Installation

1- In your project-level `build.gradle` file, add the Maven url in allprojects:
```
allprojects {
    repositories {
         mavenCentral()
    }
```
2- In your app-level `build.gradle` file, add dependencies for PivoBasicSdk

```groovy
dependencies {
    /**
     * Tensorflow module
     */
    implementation 'org.tensorflow:tensorflow-lite:1.13.1'

    /**
     * Pivo pod controller dependencies
     */
    implementation 'app.pivo.android.basicsdk:basicsdk:1.3.1'

    /**
     * Pivo Pro SDK dependencies
     */
    implementation 'app.pivo:prosdk:1.3.1'

    /**
     * RxJava dependencies
     */
    implementation "io.reactivex.rxjava2:rxjava:2.2.19"
    implementation 'io.reactivex.rxjava2:rxandroid:2.1.1'
    implementation 'io.reactivex.rxjava2:rxkotlin:2.4.0'

    /**
     * RxLifecycle.
     * This library allows one to automatically complete sequences based on a second lifecycle stream.
     */
    implementation 'com.trello:rxlifecycle:1.0'
    implementation 'com.trello:rxlifecycle-components:1.0'

    /**
     * RxAndroidBle is a powerful painkiller for Android's Bluetooth Low Energy headaches
     * https://github.com/Polidea/RxAndroidBle
     */
    implementation 'com.polidea.rxandroidble2:rxandroidble:1.17.2'
    implementation 'com.jakewharton.rx2:replaying-share-kotlin:2.2.0'

    /**
     * Coroutine dependencies
     */
    implementation "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.2"
    implementation "org.jetbrains.kotlinx:kotlinx-coroutines-android:1.6.2"
}
```

## Usage

In your custom application class

```kotlin
//...
import app.pivo.android.basicsdk.PivoSdk

class App: Application()
{
    override fun onCreate() {
        super.onCreate()
        PivoSdk.init(this) // initialize Pivo SDK
        PivoSdk.getInstance().unlockWithLicenseKey("License Contents")
    }

//...
}
```
### Action/Object Tracking:

In order to start tracking a desired target on the screen.

First, you need to select the region on interest then
```kotlin
override fun onProcessingFrame (image: Image, width:Int, height:Int,
 orientation:Int, frontCamera:Boolean) {
//...
if (region!=null && !trackingStarted){
    PivoProSdk.getInstance().startActionTracking(metadata, region, image, sensitivity, actionTrackerListener)
    region = null
    trackingStarted = true
 }else{
  if (trackingStarted){
    PivoProSdk.getInstance().updateTrackingFrame(image, metadata)
  }else{
    image.close()
  }
 }
//...
}
```
or
```kotlin
override fun onProcessingFrame (image: ByteArray, width:Int, height:Int,
 orientation:Int, frontCamera:Boolean) {
//...
if (region!=null && !trackingStarted){
    PivoProSdk.getInstance().startActionTracking(metadata, region, image, sensitivity, actionTrackerListener)
    region = null
    trackingStarted = true
 }else{
  if (trackingStarted){
    PivoProSdk.getInstance().updateTrackingFrame(image, metadata)
  }
 }
//...
}
```

### Person/Horse Tracking:

```kotlin
override fun onProcessingFrame (image: Image, width:Int, height:Int,
 orientation:Int, frontCamera:Boolean) {
//...
if (!trackingStarted){
//PivoProSdk.getInstance().starPersonTracking(metadata, image, sensitivity , aiTrackerListener) // For person tracking
  PivoProSdk.getInstance().startHorseTracking(metadata, image,sensitivity, aiTrackerListener) // For horse tracking
  region = null
  trackingStarted = true
  }else {
    PivoProSdk.getInstance().updateTrackingFrame(image, metadata)
    }
  }
//...
}
```
or 

```kotlin
override fun onProcessingFrame (image: ByteArray, width:Int, height:Int,
 orientation:Int, frontCamera:Boolean) {
//...
if (!trackingStarted){
//PivoProSdk.getInstance().starPersonTracking(metadata, image, sensitivity , aiTrackerListener) // For person tracking
  PivoProSdk.getInstance().startHorseTracking(metadata, image,sensitivity, aiTrackerListener) // For horse tracking
  region = null
  trackingStarted = true
  }else {
    PivoProSdk.getInstance().updateTrackingFrame(image, metadata)
    }
  }
//...
}
```
Once you set the listener for the tracking, now you are able to get the bounding box information:

### Action/Object Tracking

```kotlin
private val actionTrackerListener: ITrackingListener = object : ITrackingListener {
        override fun onTracking(x: Int, y: Int, width: Int, height: Int, frameWidth: Int, frameHeight: Int) {
 val rect = Rect(x, y, x + width, y + height)
// then you can draw the rectangle on the screen
        }

        override fun onClear() {}
    }
```

### Person/Horse Tracking

```kotlin
private val aiTrackerListener: ITrackingListener = object : ITrackingListener {
        override fun onTracking(x: Int, y: Int, width: Int, height: Int, frameWidth: Int, frameHeight: Int) {
            // clear graphic overlay
            tracking_graphic_overlay.clear()
            // being tracked object
            val rect = Rect(x, y, x + width, y + height)
            // you can draw now
        }

        override fun onTracking(rect: Rect?) { // Optional
            tracking_graphic_overlay.clear()

            if (rect!=null)
            {
                // you can draw rect
            }else{
                Log.e("Camera", "update onTracking")
            }

        }

        override fun onClear() {}
    }
```

## Report
If you encounter an issue during setting up the sdk, please contact us at app@3i.ai or open an issue.
