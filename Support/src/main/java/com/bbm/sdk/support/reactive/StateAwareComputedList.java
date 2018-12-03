/*
 * Copyright (c) 2018 BlackBerry.  All Rights Reserved.
 *
 * You must obtain a license from and pay any applicable license fees to
 * BlackBerry before you may reproduce, modify or distribute this
 * software, or any work that includes all or part of this software.
 *
 * This file may contain contributions from others. Please review this entire
 * file for other proprietary rights or license notices.
 */

package com.bbm.sdk.support.reactive;

import com.bbm.sdk.reactive.ComputedList;
import com.bbm.sdk.reactive.StateAware;

/**
 * A computed list which also includes a pending state.
 */
public abstract class StateAwareComputedList<T> extends ComputedList<T> implements StateAware {

}