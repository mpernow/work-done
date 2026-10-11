package com.mpernow.workdone

import android.app.Application
import com.mpernow.workdone.di.AppContainer

class WorkDoneApplication : Application() {
    /** Created lazily so nothing touches the database until a screen needs it. */
    val container: AppContainer by lazy { AppContainer(this) }
}
