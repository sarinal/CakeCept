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

import com.bbm.sdk.bbmds.internal.lists.IncrementalListObserver;
import com.bbm.sdk.bbmds.internal.lists.ObservableList;
import com.bbm.sdk.reactive.ObservableListHelper;
import com.bbm.sdk.reactive.Observer;

public abstract class AbstractObservableList<T> implements ObservableList<T> {
    private final ObservableListHelper mObservableListHelper = new ObservableListHelper();

    public AbstractObservableList() {
        init();
    }

    protected void init() {
    }

    @Override

    public void addIncrementalListObserver(IncrementalListObserver listObserver) {
        mObservableListHelper.addIncrementalListObserver(listObserver);
    }

    @Override
    public void removeIncrementalListObserver(IncrementalListObserver listObserver) {
        mObservableListHelper.removeIncrementalListObserver(listObserver);
    }

    @Override
    public void dataSetChanged() {
        mObservableListHelper.dataSetChanged();
    }

    @Override
    public void itemsChanged(int fromPosition, int itemCount) {
        mObservableListHelper.itemsChanged(fromPosition, itemCount);
    }

    @Override
    public void itemsInserted(int fromPosition, int itemCount) {
        mObservableListHelper.itemsInserted(fromPosition, itemCount);
    }

    @Override
    public void itemsRemoved(int fromPosition, int itemCount) {
        mObservableListHelper.itemsRemoved(fromPosition, itemCount);
    }

    @Override
    public void addObserver(Observer observer) {
        mObservableListHelper.addObserver(observer);
    }

    @Override
    public void removeObserver(Observer observer) {
        mObservableListHelper.removeObserver(observer);
    }

    public void notifyObservers() {
        mObservableListHelper.notifyObservers();
    }
}
