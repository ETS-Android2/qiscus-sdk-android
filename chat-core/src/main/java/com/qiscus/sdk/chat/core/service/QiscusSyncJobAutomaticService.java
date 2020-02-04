/*
 * Copyright (c) 2016 Qiscus.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.qiscus.sdk.chat.core.service;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.qiscus.sdk.chat.core.QiscusCore;
import com.qiscus.sdk.chat.core.data.local.QiscusEventCache;
import com.qiscus.sdk.chat.core.data.remote.QiscusApi;
import com.qiscus.sdk.chat.core.data.remote.QiscusPusherApi;
import com.qiscus.sdk.chat.core.event.QiscusSyncEvent;
import com.qiscus.sdk.chat.core.event.QiscusUserEvent;
import com.qiscus.sdk.chat.core.util.QiscusErrorLogger;
import com.qiscus.sdk.chat.core.util.QiscusLogger;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.util.Timer;
import java.util.TimerTask;

import androidx.annotation.RequiresApi;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

/**
 * Created on : November 23, 2018
 * Author     : adicatur
 * Name       : Catur Adi Nugroho
 * GitHub     : https://github.com/adicatur
 */
@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class QiscusSyncJobAutomaticService extends JobService {

    private static final String TAG = QiscusSyncJobAutomaticService.class.getSimpleName();
    private Timer timer;

    public void syncJob(Context context) {
        QiscusLogger.print(TAG, "syncJob...");

        stopSync();

        timer = new Timer();
        timer.schedule(new TimerTask() {
            public void run() {
                newSchedule(context);
            }
        }, QiscusCore.getAutomaticHeartBeat());

    }

    private void newSchedule(Context context) {
        QiscusLogger.print(TAG, "Job Automatic started...");

        if (QiscusCore.hasSetupUser() && QiscusPusherApi.getInstance().isConnected()) {
            scheduleSync();
        }

        syncJob(context);
    }


    @Override
    public void onCreate() {
        super.onCreate();

        QiscusLogger.print(TAG, "Creating...");
        if (!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this);
        }

        if (QiscusCore.hasSetupUser()) {
            syncJob(this);
        }

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    private void scheduleSync() {
        if (QiscusCore.isOnForeground()) {
            syncComments();
            syncEvents();
        }
    }

    private void syncEvents() {
        QiscusApi.getInstance().synchronizeEvent(QiscusEventCache.getInstance().getLastEventId())
                .subscribeOn(Schedulers.io())
                .subscribe(events -> {
                }, QiscusErrorLogger::print);
    }

    private void syncComments() {
        QiscusApi.getInstance().sync()
                .doOnSubscribe(() -> {
                    EventBus.getDefault().post((QiscusSyncEvent.STARTED));
                    QiscusLogger.print("Sync automatic started...");
                })
                .doOnCompleted(() -> {
                    EventBus.getDefault().post((QiscusSyncEvent.COMPLETED));
                    QiscusLogger.print("Sync automatic completed...");
                })
                .subscribeOn(Schedulers.io())
                .subscribe(QiscusPusherApi::handleReceivedComment, throwable -> {
                    QiscusErrorLogger.print(throwable);
                    EventBus.getDefault().post(QiscusSyncEvent.FAILED);
                    QiscusLogger.print("Sync automatic failed...");
                });
    }

    private void stopSync() {
        if (timer != null) {
            timer.cancel();
        }
    }

    @Subscribe
    public void onUserEvent(QiscusUserEvent userEvent) {
        switch (userEvent) {
            case LOGIN:
                syncJob(this);
                break;
            case LOGOUT:
                stopSync();
                break;
        }
    }

    @Override
    public void onDestroy() {
        QiscusLogger.print(TAG, "Destroying...");
        EventBus.getDefault().unregister(this);
        super.onDestroy();
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        QiscusLogger.print(TAG, "Job Automatic started...");

        if (QiscusCore.hasSetupUser() && QiscusPusherApi.getInstance().isConnected()) {
            scheduleSync();
        }

        syncJob(this);

        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        QiscusLogger.print(TAG, "Job stopped...");

        return true;
    }

}