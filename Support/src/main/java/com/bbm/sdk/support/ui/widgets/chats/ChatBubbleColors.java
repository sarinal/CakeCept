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

package com.bbm.sdk.support.ui.widgets.chats;

import android.support.annotation.ColorRes;
import android.support.annotation.DrawableRes;

/**
 * Represents a set of colors used when drawing a chat message bubble
 */
public class ChatBubbleColors {

    /**
     * The drawable resource id of the chat bubble background
     */
    @DrawableRes
    final public int backgroundId;

    /**
     * The color of the background chat bubble
     */
    @ColorRes
    final public int backgroundColor;

    /**
     * Color of normal message text drawn on top of chat bubble background
     */
    @ColorRes
    final public int normalTextColor;

    /**
     * Highlight color typically a darker version of the chat bubble background color
     * used as the text color of the Username
     */
    @ColorRes
    final public int headingColor;

    /**
     * Color of text in chat bubble that should be highlighted
     * (ex: timed, expired and retracted messages.  Linkified URL's)
     */
    @ColorRes
    final public int highlightedColor;

    /**
     * Color of messages used to tell inform the user of special conditions
     * (ex: picture received,
     * sending, glympse expired, etc.)
     */
    @ColorRes
    final public int statusColor;

    /**
     * Color of messages used to inform user of error conditions
     * (ex: picture failed to send, etc.)
     */
    @ColorRes
    final public int errorColor;

    /**
     * Color of messages used to alert the user but isn't regarded as an error
     * (ex: PING!!!, screenshot detected, etc.)
     */
    @ColorRes
    final public int alertColor;

    public ChatBubbleColors(@DrawableRes final int backgroundId,
                            @ColorRes final int backgroundColorId,
                            @ColorRes final int headingColorId,
                            @ColorRes final int normalTextColorId,
                            @ColorRes final int highlightedColorId,
                            @ColorRes final int statusColorId,
                            @ColorRes final int errorColorId,
                            @ColorRes final int alertColorId) {
        this.backgroundId = backgroundId;
        this.backgroundColor = backgroundColorId;
        this.headingColor = headingColorId;
        this.normalTextColor = normalTextColorId;
        this.highlightedColor = highlightedColorId;
        this.statusColor = statusColorId;
        this.errorColor = errorColorId;
        this.alertColor = alertColorId;
    }
}