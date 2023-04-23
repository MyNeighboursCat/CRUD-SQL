/*
 * Copyright (c) 2023 Colin Walters.  All rights reserved.
 */

package com.crudsql;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Global executor pools for the whole application.
 * <p>
 * Grouping tasks like this avoids the effects of task starvation (e.g. disk reads don't wait behind
 * webservice requests).
 *
 * @author Colin Walters
 * @version 1.0, 21/04/2023
 */
public final class AppExecutors {
    /**
     * {@link Executor} which executes {@link Runnable} local tasks on a background thread.
     */
    private final Executor mBackgroundThreadExecutor;
    /**
     * {@link Executor} which executes {@link Runnable} network tasks on background threads.
     */
    private final Executor mNetworkThreadExecutor;
    /**
     * {@link Executor} which executes {@link Runnable} local tasks on the main thread.
     */
    private final Executor mMainThreadExecutor;

    /**
     * Sets up an {@link Executor} for each of the local background, network background and main
     * threads.
     *
     * @param backgroundThreadExecutor the {@link Executor} for background thread use.
     * @param networkThreadExecutor    the {@link Executor} for network background thread use.
     * @param mainThreadExecutor       the {@link Executor} for main thread use.
     */
    private AppExecutors(final Executor backgroundThreadExecutor,
                         final Executor networkThreadExecutor, final Executor mainThreadExecutor) {
        mBackgroundThreadExecutor = backgroundThreadExecutor;
        mNetworkThreadExecutor = networkThreadExecutor;
        mMainThreadExecutor = mainThreadExecutor;
    }

    /**
     * Sets up an {@link Executor} for each of the local background, network background and main
     * threads.  The local background {@link Executor} has a single thread while the network
     * background {@link Executor} has 3 threads.
     */
    public AppExecutors() {
        this(Executors.newSingleThreadExecutor(), Executors.newFixedThreadPool(3),
                new MainThreadExecutor());
    }

    /**
     * Returns the local background thread {@link Executor}.
     *
     * @return the local background thread {@link Executor}
     */
    public Executor getBackgroundThreadExecutor() {
        return mBackgroundThreadExecutor;
    }

    // The network background thread executor is not used in this app.
    /**
     * Returns the network background thread {@link Executor}.
     *
     * @return the network background thread {@link Executor}
     */
    @SuppressWarnings({"unused", "RedundantSuppression"})
    public Executor getNetworkThreadExecutor() {
        return mNetworkThreadExecutor;
    }

    /**
     * Returns the main thread {@link Executor}.
     *
     * @return the main thread {@link Executor}
     */
    public Executor getMainThreadExecutor() {
        return mMainThreadExecutor;
    }

    /**
     * An {@link Executor} which uses a {@link Handler} to run a {@link Runnable} on the main
     * thread.
     */
    private static class MainThreadExecutor implements Executor {
        private final Handler mainThreadHandler = new Handler(Looper.getMainLooper());

        @Override
        public void execute(@NonNull Runnable command) {
            mainThreadHandler.post(command);
        }
    }
}
