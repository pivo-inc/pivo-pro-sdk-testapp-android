package app.pivo.android.prosdkdemo

import android.app.Application
import app.pivo.android.prosdk.PivoProSdk
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

        //initialize PivoProSDK
        PivoProSdk.init(this)

        PivoProSdk.getInstance().unlockWithLicenseKey(getLicenseContent())
    }

    private fun getLicenseContent(): String {
        val inputStream = assets.open("pivoProLicense.json")
        return readFile(inputStream)
    }

    @Throws(IOException::class)
    fun readFile(inputStream: InputStream?): String {
        val str = StringBuilder()
        val br = BufferedReader(InputStreamReader(inputStream))
        var line: String?
        while (br.readLine().also { line = it } != null) {
            str.append(line)
        }
        br.close()
        return str.toString()
    }
}