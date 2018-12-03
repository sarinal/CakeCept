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

package com.bbm.sdk.support.ui.widgets.recycler;


import android.view.View;

import java.util.List;

/**
 * ViewHolders can implement this interface to list any views which should be registered with the activity to trigger a context menu action
 */
public interface ContextMenuAwareHolder {
    List<View> getContextMenuAwareView();
}
