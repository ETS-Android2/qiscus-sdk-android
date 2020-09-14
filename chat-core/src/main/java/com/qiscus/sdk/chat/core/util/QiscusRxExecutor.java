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

package com.qiscus.sdk.chat.core.util;

import io.reactivex.Observable;;
import io.reactivex.Scheduler;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;
import rx.Subscription;

/**
 * Created on : September 06, 2017
 * Author     : zetbaitsu
 * Name       : Zetra
 * GitHub     : https://github.com/zetbaitsu
 */
public class QiscusRxExecutor {

    public static <T> Subscription execute(Observable<T> Observable, Listener<T> listener) {
        return execute(Observable, Schedulers.io(), AndroidSchedulers.mainThread(), listener);
    }

    public static <T> Subscription execute(Observable<T> Observable, Scheduler subscribeOn,
                                           Scheduler observeOn, Listener<T> listener) {
        return (Subscription) Observable
                .subscribeOn(subscribeOn)
                .observeOn(observeOn)
                .subscribe(listener::onSuccess, listener::onError);
    }

    public interface Listener<T> {
        void onSuccess(T result);

        void onError(Throwable throwable);
    }
}
