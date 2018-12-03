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

import com.bbm.sdk.bbmds.internal.WeakReferenceSet;
import com.bbm.sdk.reactive.Observable;
import com.bbm.sdk.reactive.Observer;

import java.util.HashMap;
import java.util.Set;

/**
 * Helper class to help keep a hard reference to observers so that they are not garbage collected before they
 * are called (since the observables only keep weak references to observers).
 * To ensure that the memory for the observer is not kept longer than needed the owner of this instance should
 * either call the remove() method when the observer is no longer needed or an instance of this class should be
 * kept as a member variable of any activity, fragment, or other object that owns the observer.
 */
public class ObserveConnector {
    private static final ObserveConnector sSharedInstance = new ObserveConnector();

    /**
     * Keep strong reference of the observers until the owner of this object is garbage collected.
     * The observer implementation would need to keep a strong reference (final local...) to the observable
     * if needed.
     * This only keeps weak reference to the observables since they are only needed to be able to
     * remove the observer from them if needed later, but if they are garbage collect then no action would be
     * needed, and we don't want to prevent them from being garbage collected due to this class.
     */
    private final HashMap<Observer, WeakReferenceSet<Observable>> mObservers = new HashMap();

    /**
     * Get a shared instance.
     * Activities and fragments should normally not use this and should create their own instance and store as
     * a member variable so their observers are automatically garbage collected when no longer needed.
     * This is only for static use that should persist for the life of the application (class static variables).
     */
    public static ObserveConnector getsSharedInstance() {
        return sSharedInstance;
    }


    /**
     * Helper method to add specified observer to observables.
     * Note that this does NOT keep a reference to either observer or observables like other methods in this class do.
     * This method is just a convience for classes defining observer member variables and adding to observables in a simple way.
     * This returns the observer so that it can be created, connected, and assigned in 1 statement.
     *
     * @param observables the observables to be notified of changes to
     * @param observer the observer implementation to be notified when the observer changes.
     * @return the specified observer
     */
    public static Observer addObserver(Observer observer, final  Observable... observables) {
       return addObserver(observer, false, observables);
    }


    /**
     * Helper method to add specified observer to observables.
     * Note that this does NOT keep a reference to either observer or observables like other methods in this class do.
     * This method is just a convience for classes defining observer member variables and adding to observables in a simple way.
     * This returns the observer so that it can be created, connected, and assigned in 1 statement.
     *
     * @param observables the observerables to be notified of changes to
     * @param observer the observer implementation to be notified when the observer changes.
     * @param callChangedImmediately if true this will immediately call observer.changed().
     *                               This is a convience for implementations that have code in the changed method that
     *                               needs to run right away (update UI...).  It avoids duplicating code that is already in the changed
     *                               method that would otherwise be need to called immediately after this method.
     * @return the specified observer
     */
    public static Observer addObserver(Observer observer, final  boolean callChangedImmediately, Observable... observables) {
        for (int i=0;i<observables.length;++i) {
            observables[i].addObserver(observer);
        }
        if (callChangedImmediately) {
            observer.changed();
        }
        return observer;
    }


    /**
     * Connect the observer to the observable and keep a hard reference to the observer so that it isn't
     * garbage collected.
     * This assumes that the caller or the observer will keep a hard reference to the observable and
     * this method does not do that.
     * This method will also call observable.addObserver(observer)
     *
     * @param observable the observer to be notified of changes to
     * @param observer the observer implementation to be notified when the observer changes.
     */
    public void connect(Observable observable, Observer observer) {
        connect(observable, observer, false);
    }

    /**
     * Connect the observer to the observable and keep a hard reference to the observer so that it isn't
     * garbage collected.
     * This assumes that the caller or the observer will keep a hard reference to the observable and
     * this method does not do that.
     * This method will also call observable.addObserver(observer)
     *
     * @param observable the observer to be notified of changes to
     * @param observer the observer implementation to be notified when the observer changes.
     * @param callChangedImmediately if true this will immediately call observer.changed().
     *                               This is a convience for implementations that have code in the changed method that
     *                               needs to run right away (update UI...).  It avoids duplicating code that is already in the changed
     *                               method that would otherwise be need to called immediately after this method.
     */
    public void connect(Observable observable, Observer observer, final  boolean callChangedImmediately) {
        add(observer, observable);
        observable.addObserver(observer);
        if (callChangedImmediately) {
            observer.changed();
        }
    }

    /**
     * Connect the observer to the observables and keep a hard reference to the observer so that it isn't
     * garbage collected.
     * This assumes that the caller or the observer will keep a hard reference to the observables and
     * this method does not do that.
     * This method will also call observable.addObserver(observer)
     *
     * @param observables the observers to be notified of changes to
     * @param observer the observer implementation to be notified when the observer changes.
     * @param callChangedImmediately if true this will immediately call observer.changed().
     *                               This is a convience for implementations that have code in the changed method that
     *                               needs to run right away (update UI...).  It avoids duplicating code that is already in the changed
     *                               method that would otherwise be need to called immediately after this method.
     */
    public void connectMultiple(Observer observer, final  boolean callChangedImmediately, Observable... observables) {
        for (int i=0;i<observables.length;++i) {
            observables[i].addObserver(observer);
            add(observer, observables[i]);
        }
        if (callChangedImmediately) {
            observer.changed();
        }
    }

    private void add(Observer observer, Observable observable) {
        WeakReferenceSet observables = mObservers.get(observer);
        if (observables == null) {
            observables = new WeakReferenceSet();
            mObservers.put(observer, observables);
        }
        observables.add(observable);
    }

    /**
     * Remove the hard reference to this observer.
     * This does not remove this observer from the observable since the observable only keeps a weak reference to the observer
     * which will be cleaned up automatically when the observer is garbage collected.
     */
    public boolean remove(Observer observer) {
        return mObservers.remove(observer) != null;
    }

    /**
     * Remove all observers hard references which will allow them to be automatically removed from any
     * observables that only keep weak references
     */
    public void removeObservers() {
        //if we just cleared the map the observers would still be connected for some time until garbage collection ran
        //which would allow for potential unexpected behaviour of old observers still being notified
        //so get all the observers and remove them from each of their observables
        Set<Observer> observers = mObservers.keySet();
        for (Observer observer : observers) {
            WeakReferenceSet<Observable> observables = mObservers.get(observer);
            if (observables != null && observables.size() > 0) {
                for (Observable observable : observables) {
                    if (observable != null) {
                        observable.removeObserver(observer);
                    }
                }
            }
        }

        //now we can just simply clear it
        mObservers.clear();
    }
}
