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

import com.bbm.sdk.reactive.ObservableTracker;
import com.bbm.sdk.reactive.TrackedGetter;

import java.util.ArrayList;
import java.util.List;

/**
 * An implementation of ObservableList that is backed by an internal ArrayList.
 * WARNING: This implementation isn't thread safe, avoid modifying and accessing
 *           from multiple threads concurrently without synchronization.
 * WARNING: The methods that modify the contents (add, remove, set) will automatically
 *           notify the observers in the callers thread.
 *           Normally observers expect to be notified in the main UI thread, if that is the
 *           case then this should only be modified in that thread.
 *           Also, a simple action like calling add() could be slower than normal if an
 *           observer of this list takes further action.
 *           You should also avoid having an observer of this list that directly or indirectly
 *           modifies this list as a result of its changed().
 *
 * @param <T>
 */
public class ArrayObservableList<T> extends AbstractObservableList<T> {
    private ArrayList<T> mList = new ArrayList<>();

    private boolean mPending;

    public T set(int index, T element) {
        T old = mList.set(index, element);
        //calling the itemsChanged (or the other ObservableList *Changed methods) will also call the notifyObservers()
        itemsChanged(index, 1);
        return old;
    }

    public void add(T element) {
        mList.add(element);
        itemsInserted(size() - 1, 1);
    }

    public void add(int index, T element) {
        mList.add(index, element);
        itemsInserted(index, 1);
    }

    public T remove(int index) {
        T old = mList.remove(index);
        itemsRemoved(index, 1);
        return old;
    }

    public boolean remove(Object o) {
        boolean found = mList.remove(o);
        itemsRemoved(size(), 1);
        return found;
    }

    public void clear() {
        mList.clear();
        dataSetChanged();
    }

    /**
     * Returns the number of elements in this list.
     *
     * This is a {@link TrackedGetter}
     *
     * @return the number of elements in this list.
     * @trackedgetter This method is a {@link TrackedGetter}
     */
    @TrackedGetter
    @Override
    public int size() {
        ObservableTracker.getterCalled(this);
        return mList.size();
    }

    /**
     * Returns the element at the specified position in this list.
     *
     * This is a {@link TrackedGetter}
     *
     * @param index The index of the element to return.
     * @return the element at the specified position in this list.
     * @trackedgetter This method is a {@link TrackedGetter}
     */
    @TrackedGetter
    @Override
    public T get(int index) {
        ObservableTracker.getterCalled(this);
        return mList.get(index);
    }

    /**
     * Returns true if this list is pending.
     * For this implementation it defaults to false and will only change if the owner of the list
     * changes it based on its usage of the list and its knowledge of the pending state of the data.
     *
     * @trackedgetter This method is a {@link TrackedGetter}
     */
    @TrackedGetter
    @Override
    public boolean isPending() {
        ObservableTracker.getterCalled(this);
        return mPending;
    }

    /**
     * Change the pending value, this will notify observers.
     * WARNING: Only the owner of this list should call this method!
     *
     * @param pending the new pending state
     */
    public void setPending(boolean pending) {
        if (mPending != pending) {
            mPending = pending;
            notifyObservers();
        }
    }

    /**
     * Returns a copy of the list contents that the caller can use and modify.
     * WARNING: this is a relatively expensive call since it copies the contents and
     * doesn't return them directly.
     * In many cases it is more efficient to call get(int) and size() on this class directly
     * instead of calling this method and using that list.
     *
     * @return The current value of the observable.
     * @trackedgetter This method is a {@link TrackedGetter}
     */
    @TrackedGetter
    @Override
    public List<T> get() {
        ObservableTracker.getterCalled(this);
        //don't allow caller to possibly directly modify contents
        return new ArrayList<>(mList);
    }
}
