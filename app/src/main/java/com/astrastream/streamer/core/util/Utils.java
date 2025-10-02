package com.astrastream.streamer.core.util;

import android.app.Application;
import android.content.Context;

public class Utils {
    private static Application sApp;

    public static Context getApp() {
        return sApp;
    }

    public static void init(Application application) {
        sApp = application;
    }

}
