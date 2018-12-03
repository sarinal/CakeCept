/*
 * Copyright (c) 2017 BlackBerry.  All Rights Reserved.
 *
 * You must obtain a license from and pay any applicable license fees to
 * BlackBerry before you may reproduce, modify or distribute this
 * software, or any work that includes all or part of this software.
 *
 * This file may contain contributions from others. Please review this entire
 * file for other proprietary rights or license notices.
 */

package com.bbm.sdk.support.reactive;

import com.bbm.sdk.reactive.ObservableHelper;
import com.bbm.sdk.reactive.ObservableValue;
import com.bbm.sdk.reactive.Observer;

public abstract class AbstractObservableValue<V> implements ObservableValue<V> {
    protected ObservableHelper mObservableHelper = new ObservableHelper();

    public AbstractObservableValue() {
        init();
    }

    /**
     * inline subclass implementations can override this since they can't provide a constructor
     */
    protected void init() {
    }

    protected void notifyObservers() {
        mObservableHelper.notifyObservers();
    }
    public void addObserver(final Observer observer) {
        mObservableHelper.addObserver(observer);
    }

    public void removeObserver(final Observer observer) {
        mObservableHelper.removeObserver(observer);
    }
}
