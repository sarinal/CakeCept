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

package com.bbm.sdk.support.kms;

import android.support.annotation.NonNull;

import com.bbm.sdk.BBMEnterprise;
import com.bbm.sdk.bbmds.GlobalSetupState;
import com.bbm.sdk.bbmds.GlobalSyncPasscodeState;
import com.bbm.sdk.bbmds.inbound.SyncError;
import com.bbm.sdk.bbmds.inbound.SyncPasscodeChangeResult;
import com.bbm.sdk.bbmds.internal.Existence;
import com.bbm.sdk.bbmds.outbound.SyncPasscodeChange;
import com.bbm.sdk.bbmds.outbound.SyncStart;
import com.bbm.sdk.reactive.ObservableMonitor;
import com.bbm.sdk.reactive.SingleshotMonitor;
import com.bbm.sdk.service.InboundMessageObservable;
import com.bbm.sdk.support.protect.KeySource;
import com.bbm.sdk.support.protect.PasscodeProvider;
import com.bbm.sdk.support.protect.UserChallengePasscodeProvider;

import java.util.UUID;

/**
 * This class listens to BlackBerry KMS events from bbmcore.
 * The PasscodeProvider is prompted for a passcode when required.
 *
 * To use the BlackBerry KMS as the key source set it using {@link com.bbm.sdk.support.util.KeySourceManager#setKeySource(KeySource)}
 */
public class BlackBerryKMSSource implements KeySource {

    private PasscodeProvider mPasscodeProvider;
    private PasscodeProvider.PasscodeError mPreviousError = PasscodeProvider.PasscodeError.None;
    private InboundMessageObservable<SyncError> mSyncErrorObservable;
    private boolean forgotPasscode = false;

    // Observe the global setup state to determine when to prompt the user for a passcode
    private ObservableMonitor mPasscodeMonitor = new ObservableMonitor() {
        @Override
        protected void run() {
            GlobalSetupState setupState = BBMEnterprise.getInstance().getBbmdsProtocol().getGlobalSetupState().get();
            if (setupState.exists == Existence.MAYBE) {
                return;
            }
            //If the setup state is "SyncRequired" then check the SyncPasscodeState, if necessary prompt the user for a passcode
            if (setupState.state == GlobalSetupState.State.SyncRequired) {
                GlobalSyncPasscodeState passcodeState = BBMEnterprise.getInstance().getBbmdsProtocol().getGlobalSyncPasscodeState().get();
                if (passcodeState.exists == Existence.MAYBE || mPasscodeProvider == null) {
                    return;
                }
                switch (passcodeState.value) {
                    case New:
                        //Prompt the user to provide a new passcode
                        mPasscodeProvider.requestNewPasscode(false, mPreviousError);
                        break;
                    case Existing:
                        mPasscodeProvider.provideExistingPasscode(false, mPreviousError);
                        break;
                    case None:
                    case Unspecified:
                        //No action required
                        break;
                }
            } else if (setupState.state == GlobalSetupState.State.Ongoing) {
                //Reset the passcode error.
                mPreviousError = PasscodeProvider.PasscodeError.None;
            }
        }
    };

    //Observe sync errors
    private ObservableMonitor mSyncErrorMonitor = new ObservableMonitor() {
        @Override
        protected void run() {
            SyncError syncError = mSyncErrorObservable.get();
            if (syncError.exists == Existence.MAYBE) {
                return;
            }

            switch (syncError.error) {
                case Failure:
                    mPreviousError = PasscodeProvider.PasscodeError.SyncFailure;
                    break;
                case IncorrectPasscode:
                    mPreviousError = PasscodeProvider.PasscodeError.IncorrectPasscode;
                    break;
                case Timeout:
                    mPreviousError = PasscodeProvider.PasscodeError.SyncTimeout;
                    break;
                case Unspecified:
                default:
                    mPreviousError = PasscodeProvider.PasscodeError.None;
            }
        }
    };

    public BlackBerryKMSSource(@NonNull UserChallengePasscodeProvider passcodeProvider) {
        mPasscodeProvider = passcodeProvider;

        mSyncErrorObservable = new InboundMessageObservable<>(
                new SyncError(),
                BBMEnterprise.getInstance().getBbmdsProtocolConnector()
        );
    }

    private void sendSyncPasscodeChange(String passcode) {
        //Setup a consumer to listen for the SyncPasscodeChangeResult
        String cookie = UUID.randomUUID().toString();
        InboundMessageObservable<SyncPasscodeChangeResult> changeResult =
                new InboundMessageObservable<>(
                        new SyncPasscodeChangeResult(),
                        cookie,
                        BBMEnterprise.getInstance().getBbmdsProtocolConnector()
                );


        SingleshotMonitor.run(() -> {
            if (changeResult.get().exists == Existence.MAYBE) {
                return false;
            }

            switch (changeResult.get().result) {
                case Success:
                    //We're done
                    break;
                case TemporaryFailure:
                    //Re-prompt but include the temporary failure error
                    mPasscodeProvider.requestNewPasscode(true, PasscodeProvider.PasscodeError.TemporaryFailure);
                    break;
                default:
                    break;
            }

            return true;
        });

        //Send a request to change the passcode
        SyncPasscodeChange changePasscode = new SyncPasscodeChange(passcode);
        BBMEnterprise.getInstance().getBbmdsProtocol().send(changePasscode);
    }

    /**
     * Start monitoring GlobalPasscodeState and monitor for sync errors.
     */
    @Override
    public void start() {
        mPasscodeMonitor.activate();
        mSyncErrorMonitor.activate();
    }

    /**
     * Stop monitoring the GlobalPasscodeState and monitoring for sync errors.
     */
    @Override
    public void stop() {
        mPasscodeMonitor.dispose();
        mSyncErrorMonitor.dispose();
    }

    @Override
    public void retryFailedEvents() {
        //Nothing retry of key storage requests is completed by bbmcore
    }

    /**
     * Request a new passcode from the user.
     * After the passcode is retrieved it will be provided to bbmcore via {@link #sendSyncPasscodeChange}
     */
    @Override
    public void changePasscode() {
        mPasscodeProvider.requestNewPasscode(true, PasscodeProvider.PasscodeError.None);
    }

    /**
     * Provide the supplied passcode to bbmcore.
     * If the GlobalPasscodeState is 'Existing' or 'New' the passcode is sent via {@link SyncStart}
     * If the GlobalPasscodeState is 'None' the passcode is sent via {@link SyncPasscodeChange}
     * @param passcode the passcode provided by the user.
     */
    public void setPasscode(@NonNull String passcode) {
        SingleshotMonitor.run(() -> {
            GlobalSyncPasscodeState passcodeState = BBMEnterprise.getInstance().getBbmdsProtocol().getGlobalSyncPasscodeState().get();
            if (passcodeState.exists == Existence.MAYBE) {
                return false;
            }
            if (forgotPasscode) {
                //Force state to 'New' to reset the passcode
                passcodeState.value = GlobalSyncPasscodeState.State.New;
            }
            switch (passcodeState.value) {
                case New:
                    //Send the new passcode to bbmcore to complete setup
                    SyncStart syncStart = new SyncStart(passcode).action(SyncStart.Action.New);
                    BBMEnterprise.getInstance().getBbmdsProtocol().send(syncStart);
                    break;
                case Existing:
                    //Send the existing passcode to bbmcore complete setup.
                    syncStart = new SyncStart(passcode).action(SyncStart.Action.Existing);
                    BBMEnterprise.getInstance().getBbmdsProtocol().send(syncStart);
                    break;
                case None:
                    //We aren't setting up so change the passcode
                    sendSyncPasscodeChange(passcode);
                    break;
                default:
                    //Fall out and return true
            }
            return true;
        });
    }

    /**
     * Retrieve a new passcode, after the passcode is retrieved it will be provided to bbmcore via
     * {@link SyncStart} with action 'New'.
     */
    @Override
    public void forgotPasscode() {
        //If the user forgets their passcode for the key storage we can send a syncStart with action 'New'
        forgotPasscode = true;
        mPasscodeProvider.requestNewPasscode(false, PasscodeProvider.PasscodeError.None);
    }

}
