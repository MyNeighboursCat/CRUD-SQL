/*
 * Copyright (c) 2023 Colin Walters.  All rights reserved.
 */

package com.crudsql;

import android.app.Application;

/**
 * Android Application class. Used for accessing singletons.
 *
 * @author Colin Walters
 * @version 1.0, 21/04/2023
 */
public final class BasicApplication extends Application {
    /**
     * Global executor pools for the whole application.
     */
    public static final AppExecutors APP_EXECUTORS = new AppExecutors();
}
