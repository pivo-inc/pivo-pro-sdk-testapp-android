# Pivo Pro SDK Android test application

This is an android test application for Pro Pivo SDK. This application will allow to control the Pivo rotator.

## Before you begin

Please visit [Pivo developer website](https://developer.pivo.app/) and generate the license file to include it into your project. 

## Installation

1- In your project-level `build.gradle` file, add the Maven url in allprojects:
```
allprojects {
    repositories {
         maven {
            url  "https://dl.bintray.com/pivopod/PivoProSDK"
        }
    }
```
2- In your app-level `build.gradle` file, add dependencies for PivoBasicSdk

```groovy
dependencies {
 implementation 'app.pivo.android.prosdk:PivoProSdk:1.0.0-alpha3'
 implementation 'com.polidea.rxandroidble:rxandroidble:1.5.0'
 /**
  * RxJava dependencies
  */
 implementation 'io.reactivex.rxjava2:rxandroid:2.1.1'
 implementation 'io.reactivex.rxjava2:rxjava:2.2.17'
 implementation 'com.trello:rxlifecycle:1.0'
 implementation 'com.trello:rxlifecycle-components:1.0
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
## License
MIT License

Copyright (c) 2020 pivo-inc
Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
