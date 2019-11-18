package org.electrum.electrum

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Handler
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import org.acra.ACRA
import org.acra.annotation.AcraCore
import org.acra.annotation.AcraDialog


val DEFAULT_CHANNEL = "default"

lateinit var app: App
lateinit var mainHandler: Handler



val py by lazy {
    Python.start(AndroidPlatform(app))
    Python.getInstance()
}
fun libMod(name: String) = py.getModule("electrum.$name")!!
fun guiMod(name: String) = py.getModule("electrum_gui.android.$name")!!
val libNetworks by lazy { libMod("networks") }

val testDeamo by lazy { guiMod("daemon") }

// Not using reportFields: it doesn't noticably reduce response time.
@AcraCore(reportSenderFactoryClasses = [CrashhubSenderFactory::class])
@AcraDialog(reportDialogClass = CrashhubDialog::class, resTitle = R.string.sorry,
            resCommentPrompt = R.string.please_briefly, resPositiveButtonText = R.string.send)
class App : Application() {

    override fun attachBaseContext(base: Context?) {
        // Set these variables as early as possible, in case ACRA.init tries to send a
        // saved crash report.
        app = this
        mainHandler = Handler()

        super.attachBaseContext(base)
        ACRA.init(this)
    }

    override fun onCreate() {
        super.onCreate()
        System.out.println("app.onCreate() in......====================")
        testDeamo.callAttr("test")

            if (Build.VERSION.SDK_INT >= 26) {
                getSystemService(NotificationManager::class).createNotificationChannel(
                        NotificationChannel(DEFAULT_CHANNEL, "Default",
                                NotificationManager.IMPORTANCE_DEFAULT))
            }

            System.out.println("app.onCreate() in11111111......================")
            // The rest of this method should run in the main process only.
            if (ACRA.isACRASenderServiceProcess()) return

            System.out.println("app.onCreate() in22222......================")
            if (BuildConfig.testnet) {
                libNetworks.callAttr("set_testnet")
            }


            System.out.println("app.onCreate() 4444.....================")
//            initSettings()
//            initDaemon()
//            initNetwork()
//            initExchange()
        }
}
fun runOnUiThread(r: () -> Unit) { runOnUiThread(Runnable { r() }) }

fun runOnUiThread(r: Runnable) {
    if (onUiThread()) {
        r.run()
    } else {
        mainHandler.post(r)
    }
}

fun onUiThread() = Thread.currentThread() == mainHandler.looper.thread