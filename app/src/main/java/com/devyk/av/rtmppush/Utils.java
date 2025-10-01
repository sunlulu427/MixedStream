package com.devyk.av.rtmppush;

import android.app.Application;
import android.content.Context;

class Utils {
    private static Application sApp;

    public static Context getApp() {
        return sApp;
    }

    public static void init(Application application) {
        sApp = application;
    }

}
