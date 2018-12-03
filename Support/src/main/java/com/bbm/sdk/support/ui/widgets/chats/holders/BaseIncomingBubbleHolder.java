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

package com.bbm.sdk.support.ui.widgets.chats.holders;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.bbm.sdk.BBMEnterprise;
import com.bbm.sdk.bbmds.User;
import com.bbm.sdk.bbmds.internal.Existence;
import com.bbm.sdk.reactive.ObservableValue;
import com.bbm.sdk.reactive.Observer;
import com.bbm.sdk.support.R;
import com.bbm.sdk.support.identity.user.AppUser;
import com.bbm.sdk.support.identity.user.UserManager;
import com.bbm.sdk.support.util.BbmUtils;
import com.bbm.sdk.support.ui.widgets.chats.DecoratedMessage;

/**
 * A base for an incoming message bubble. The message specific content should be injected via {@link #setContentSpecificView(LayoutInflater, int)}
 */
public class BaseIncomingBubbleHolder extends BaseBubbleHolder {

    private final TextView mSenderTextView;
    private final ImageView mMessagePhoto;

    protected DecoratedMessage mDecoratedMessage;

    private ObservableValue<User> mUser;
    private ObservableValue<AppUser> mAppUser;

    private final Observer mObserver = new Observer() {
        @Override
        public void changed() {

            if (mDecoratedMessage == null) {
                return;
            }

            final String userUri = mDecoratedMessage.getSenderUri();
            if(!TextUtils.isEmpty(userUri)) {
                final User sender = BBMEnterprise.getInstance().getBbmdsProtocol().getUser(userUri).get();

                // Make sure we have valid user data before moving forward.
                if (sender.exists != Existence.YES) {
                    return;
                }

                if (mAppUser == null) {
                    mAppUser = UserManager.getInstance().getUser(sender.regId);
                    mAppUser.addObserver(mAppUserObserver);
                }
                mAppUserObserver.changed();
            }
        }
    };

    private final Observer mAppUserObserver = new Observer() {
        @Override
        public void changed() {
            if (mAppUser == null) {
                return;
            }
            AppUser appUser = mAppUser.get();

            if (appUser.getExists() == Existence.MAYBE) {
                return;
            }

            if (mDecoratedMessage.isConference()) {
                mSenderTextView.setVisibility(View.VISIBLE);
                mSenderTextView.setText(BbmUtils.getAppUserName(mAppUser.get()));
            }

            if (mDecoratedMessage.showAvatar() && mMessagePhoto != null) {
                mMessagePhoto.setVisibility(View.VISIBLE);
                if (!TextUtils.isEmpty((appUser.getAvatarUrl()))) {
                    //Todo - load avatar
                    mMessagePhoto.setImageResource(R.drawable.default_avatar);
                } else {
                    mMessagePhoto.setImageResource(R.drawable.default_avatar);
                }
            } else if (mMessagePhoto != null) {
                mMessagePhoto.setVisibility(View.GONE);
            }
        }
    };


    public BaseIncomingBubbleHolder(final LayoutInflater layoutInflater, final ViewGroup parent) {
        super(layoutInflater, parent, R.layout.chat_bubble_base_incoming_holder);

        mSenderTextView = (TextView) getRootView().findViewById(R.id.message_sender);
        mMessagePhoto = (ImageView) getRootView().findViewById(R.id.message_photo);
    }


    /**
     * Update the incoming generic message bubble components including:
     * - date
     * - status icon
     * - priority icon
     * - message photo
     * - sender name
     *
     * @param decoratedMessage the message to update
     */
    @Override
    public void updateGeneric(DecoratedMessage decoratedMessage) {
        //priority and status are set in the super
        super.updateGeneric(decoratedMessage);

        mDecoratedMessage = decoratedMessage;

        if (decoratedMessage.shouldMergeBefore() && mMessagePhoto != null) {
            mMessagePhoto.setVisibility(View.GONE);
        } else {
            // lazy load the app user.
            final String userUri = decoratedMessage.getSenderUri();
            if (mAppUser == null && !TextUtils.isEmpty(userUri)) {
                mUser = BBMEnterprise.getInstance().getBbmdsProtocol().getUser(userUri);
                mUser.addObserver(mObserver);
            }
            mObserver.changed();
        }

        if(decoratedMessage.getColors() != null) {
            mSenderTextView.setTextColor(getContext().getResources().getColor(decoratedMessage.getColors().headingColor));
        }
    }

    @Override
    public void onRecycled() {
        super.onRecycled();
        mSenderTextView.setText(null);

        if (mUser != null) {
            mUser.removeObserver(mObserver);
            mUser = null;
        }

        if (mAppUser != null) {
            mAppUser.removeObserver(mObserver);
            mAppUser = null;
        }

        mDecoratedMessage = null;
    }
}
