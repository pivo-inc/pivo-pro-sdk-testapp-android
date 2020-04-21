package app.pivo.android.prosdkdemo

import android.app.Application
import app.pivo.android.prosdk.PivoProSdk
import app.pivo.android.prosdk.tracking.FrameMetadata
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader

/**
 * Created by murodjon on 2020/04/21
 */
class App:Application() {

    override fun onCreate() {
        super.onCreate()

        //initialize PivoSdk
//        PivoSdk.init(this)


//        PivoSdk.getInstance().unlockWithLicenseKey(getLicenseContent())

        //initialize PivoProSDK
        PivoProSdk.init(this)

        PivoProSdk.getInstance().unlockWithLicenseKey("{\"owner\":\"Pivo\",\"version\":\"1.0.1\",\"platform\":\"android\",\"plan\":\"basic\",\"appIdentifier\":\"com.pivo.android.basicsdkdemo\",\"createdAt\":1585894418,\"expiredAt\":1648966418," +
                "\"signature\":\"o+1O4c2TkXpbTGKZy9SS0vV92StVHIaFgvylq280KkkQmP/RZk0PIXpifwV1B/SLSecclyoiO9m2Q3vxcmWoDS8diDMMpJOfFYbaIEAKtFko/cAgGGbZpxdRy5f/5wde6xmhjkSavtKiw2O27GsTxRcGy/fR91G/m5xjM7pwCMCFp1Fo+h8ufIv3Qz/uVZnzAE2V5cVMeQEPinYa5nhpExOLRLPwp2nM/qEzO+yGti/mPKaszzlKh8jEhl1pUG3gpktAmoeH50JdVZo67e8JJJgLU8LbW82SkYdF45RWKQhFC1Q4z2kN5SwrKUsFWM2kr0T8cAK/LE+S6Btp4B5SVA==\"}")

    }

    private fun getLicenseContent():String?{
        var inputStream = assets.open("licenceKey.json")
        return readFile(inputStream)
    }

    @Throws(IOException::class)
    fun readFile(inputStream: InputStream?): String? {
        val str = StringBuilder()
        val br: BufferedReader
        br = BufferedReader(InputStreamReader(inputStream))
        var line: String?=null
        while (br.readLine().also({ line = it }) != null) {
            str.append(line)
        }
        br.close()
        return str.toString()
    }
}