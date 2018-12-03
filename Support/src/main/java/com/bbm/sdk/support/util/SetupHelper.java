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

package com.bbm.sdk.support.util;

import android.support.annotation.NonNull;

import com.bbm.sdk.BBMEnterprise;
import com.bbm.sdk.bbmds.GlobalSetupState;
import com.bbm.sdk.bbmds.inbound.EndpointDeregisterResult;
import com.bbm.sdk.bbmds.inbound.EndpointUpdateResult;
import com.bbm.sdk.bbmds.inbound.Endpoints;
import com.bbm.sdk.bbmds.inbound.EndpointDeregistered;
import com.bbm.sdk.bbmds.internal.Existence;
import com.bbm.sdk.bbmds.internal.InboundMessage;
import com.bbm.sdk.bbmds.internal.InboundMessageConsumer;
import com.bbm.sdk.bbmds.outbound.EndpointDeregister;
import com.bbm.sdk.bbmds.outbound.EndpointUpdate;
import com.bbm.sdk.bbmds.outbound.EndpointsGet;
import com.bbm.sdk.bbmds.outbound.SetupRetry;
import com.bbm.sdk.reactive.ObservableValue;
import com.bbm.sdk.reactive.SingleshotMonitor;
import com.bbm.sdk.service.InboundMessageObservable;

import java.util.UUID;

/**
 * Helper class with static methods that can help with handing BBM setup.
 */
public final class SetupHelper {

    SetupHelper() {
    }

    /**
     * This will register the current device. This MUST be called when {@link GlobalSetupState} is
     * in the {@link GlobalSetupState.State#NotRequested}. This will pre-register the device with
     * the SDK. Once a {@link com.bbm.sdk.bbmds.outbound.AuthToken} is sent to the SDK, the SDK
     * shall attempt to register the device during setup.
     */
    public static void registerDevice(final @NonNull String name, final @NonNull String description) {

        final ObservableValue<GlobalSetupState> globalSetupState = BBMEnterprise.getInstance().getBbmdsProtocol().getGlobalSetupState();

        // Don't send if setup if the state is not Not Requested.
        if (globalSetupState.get().state != GlobalSetupState.State.NotRequested) {
            return;
        }

        // Create a cookie to track the request.
        final String requestCookie = UUID.randomUUID().toString();

        // Create a one time inbound message observer
        final InboundMessageObservable<EndpointUpdateResult> updateObservable = new InboundMessageObservable<>(
                new EndpointUpdateResult(), requestCookie, BBMEnterprise.getInstance().getBbmdsProtocolConnector());

        // Create a monitor to wait for the response.
        SingleshotMonitor.run(new SingleshotMonitor.RunUntilTrue() {
            @Override
            public boolean run() {
                if (updateObservable.get().getExists() == Existence.MAYBE) {
                    return false;
                }

                EndpointUpdateResult result = updateObservable.get();
                if (result.result == EndpointUpdateResult.Result.Failure) {
                    Logger.e("Unable to register device");
                }

                return true;
            }
        });

        // Send an EndpointUpdate to register this local device.
        EndpointUpdate update = new EndpointUpdate(
                requestCookie,
                description,
                name
        );

        BBMEnterprise.getInstance().getBbmdsProtocol().send(update);
    }

    /**
     * Setup encounters the {@link GlobalSetupState.State#Full}. At this point the current account has been
     * registered on a number of devices, aka endpoints. To allow the user to continue with setup a single
     * registered device needs to be removed. For this example we will remove the first registered device.
     * <p>
     * Note, in practice it is better to get the list of {@link Endpoints} and ask the user to select 1
     * or all registered devices to remove.
     */
    public static void handleFullState() {
        final ObservableValue<GlobalSetupState> globalSetupState = BBMEnterprise.getInstance().getBbmdsProtocol().getGlobalSetupState();

        // Don't send if setup if the state is not Full.
        if (globalSetupState.get().state != GlobalSetupState.State.Full) {
            return;
        }

        // Create a cookie to track the request.
        final String requestCookie = UUID.randomUUID().toString();

        // Create a one time inbound message observer that will have the results.
        final InboundMessageObservable<Endpoints> getObserver = new InboundMessageObservable<>(
                new Endpoints(), requestCookie, BBMEnterprise.getInstance().getBbmdsProtocolConnector());

        // Create a results monitor to wait for the response.
        SingleshotMonitor.run(new SingleshotMonitor.RunUntilTrue() {
            @Override
            public boolean run() {
                // No data yet to wait.
                if (getObserver.get().getExists() == Existence.MAYBE) {
                    return false;
                }

                // Get the results and check for Success/Fail.
                Endpoints result = getObserver.get();
                if (result.result == Endpoints.Result.Success) {

                    // We have a list of Endpoints! Now lets remove one.
                    removeDevice(getObserver.get());
                } else {
                    Logger.e("Unable to get registered devices");
                }
                return true;
            }
        });

        // Activate the monitor and issue the protocol request.
        BBMEnterprise.getInstance().getBbmdsProtocol().send(new EndpointsGet(requestCookie));
    }

    /**
     * Removes the first registered endpoint from a list of {@link Endpoints} and sends a SetupRetry message to bbmcore
     *
     * @param endpoints A list of endpoints. Must not be null.
     */
    public static void removeDevice(@NonNull final Endpoints endpoints) {
        final String endpointId = endpoints.registeredEndpoints.get(0).endpointId;
        final String requestCookie = UUID.randomUUID().toString();

        // Create a one time inbound message observer for the remove results.
        final InboundMessageObservable<EndpointDeregisterResult> removeObserver = new InboundMessageObservable<>(
                new EndpointDeregisterResult(), requestCookie, BBMEnterprise.getInstance().getBbmdsProtocolConnector());

        // Create a monitor to wait for the response.
        SingleshotMonitor.run(new SingleshotMonitor.RunUntilTrue() {
            @Override
            public boolean run() {
                if (removeObserver.get().getExists() == Existence.MAYBE) {
                    return false;
                }

                //
                EndpointDeregisterResult result = removeObserver.get();
                if (result.result == EndpointDeregisterResult.Result.Success) {

                    // Now that a registered endpoint was removed tell the SDK to continue
                    // with setup.
                    BBMEnterprise.getInstance().getBbmdsProtocol().send(new SetupRetry());
                } else {
                    Logger.e("Unable to remove a registered device");
                }

                return true;
            }
        });

        BBMEnterprise.getInstance().getBbmdsProtocol().send(new EndpointDeregister(requestCookie, endpointId));
    }


    /**
     * This listens for the {@link EndpointDeregistered} message.
     * Need to keep hard reference so it doesn't get garbage collected.
     */
    private static InboundMessageConsumer<EndpointDeregistered> mDeregisterMessageConsumer;

    /**
     * Defines a listener that will be triggered when the Bbmds message "EndpointDeregistered"
     * arrives.
     */
    public interface EndpointDeregisteredListener {
        void onEndpointDeregistered();
    }

    /**
     * Add a listener for an 'EndpointDeregistered' event. The 'EndpointDeregistered' event indicates that this endpoint has been deregistered.
     * The application must restart the BBM Enterprise SDK service and re-authenticate to continue.
     */
    public static synchronized void listenForAndHandleDeregistered(@NonNull final EndpointDeregisteredListener listener) {
        mDeregisterMessageConsumer = new InboundMessageConsumer<EndpointDeregistered>(new EndpointDeregistered()) {
            @Override
            public void onInboundMessage(EndpointDeregistered inboundMessage) {
                listener.onEndpointDeregistered();
            }
        };

        BBMEnterprise.getInstance().getBbmdsProtocolConnector().addMessageConsumer(mDeregisterMessageConsumer);
    }

    public interface DeregisterFailedCallback {
        void deregisterFailed();
    }

    /**
     * Deregister the users active endpoint. This will cause the
     * @param callback
     */
    public static void deregisterCurrentEndpoint(@NonNull DeregisterFailedCallback callback) {
        // Create a cookie to track the request.
        final String requestCookie = UUID.randomUUID().toString();

        // Create a one time inbound message observer that will have the results.
        final InboundMessageObservable<Endpoints> endpointsObservable = new InboundMessageObservable<>(
                new Endpoints(), requestCookie, BBMEnterprise.getInstance().getBbmdsProtocolConnector());

        SingleshotMonitor.run(new SingleshotMonitor.RunUntilTrue() {
            @Override
            public boolean run() {
                Endpoints endpoints = endpointsObservable.get();
                if (endpoints.exists == Existence.MAYBE) {
                    return false;
                }

                if (endpoints.result == Endpoints.Result.Failure) {
                    callback.deregisterFailed();
                    return true;
                }

                //Find the "current" endpoint
                for (Endpoints.RegisteredEndpoints registeredEndpoint : endpoints.registeredEndpoints) {
                    if (registeredEndpoint.isCurrent) {
                        //Leave the current endpoint
                        BBMEnterprise.getInstance().getBbmdsProtocol().send(
                                new EndpointDeregister(requestCookie, registeredEndpoint.endpointId)
                        );

                        // Create a one time inbound message observer that will have the results.
                        final InboundMessageObservable<EndpointDeregisterResult> deregisterObservable = new InboundMessageObservable<>(
                                new EndpointDeregisterResult(),
                                null,
                                BBMEnterprise.getInstance().getBbmdsProtocolConnector()
                        );

                        //Observe the deregistration result
                        SingleshotMonitor.run(new SingleshotMonitor.RunUntilTrue() {
                            @Override
                            public boolean run() {
                                EndpointDeregisterResult result = deregisterObservable.get();
                                if (result.exists == Existence.MAYBE) {
                                    return false;
                                } else {
                                    if (result.result == EndpointDeregisterResult.Result.Failure) {
                                        callback.deregisterFailed();
                                    }
                                    return true;
                                }
                            }
                        });
                        break;
                    }
                }
                return true;
            }
        });

        // Activate the monitor and issue the protocol request to get the endpoints
        BBMEnterprise.getInstance().getBbmdsProtocol().send(new EndpointsGet(requestCookie));
    }

}
