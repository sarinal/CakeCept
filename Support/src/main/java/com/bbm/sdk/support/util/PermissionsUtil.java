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


import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;

import com.bbm.sdk.support.R;

import java.lang.ref.WeakReference;

/**
 * Utility methods used for new permissions checks added in Android Marshmallow release.
 */
public final class PermissionsUtil {

    /**
     * Lets calling activity get notified when user does something other than take the
     * positive action (continue, ask again...).
     * Useful when activity needs to clean up UI that won't work (finish, hide button...)
     * when the user chooses not to let us prompt for permission.
     */
    public interface OnCancelListener {
        void onCancel();
    }

    public static final OnCancelListener sEmptyOnCancelListener = new OnCancelListener() {
        @Override
        public void onCancel() {
        }
    };

    /**
     * keep a static ref to the last/current showing dialog used for permission checks so we can avoid stacking more than 1 at a time.
     * This should be cleaned up, but keep as a weak reference in case of a crash or something while the dialog is showing which
     * could result in the reference staying and the dialog isShowing() might always return true and no permission dialog could
     * be shown until force stop or device reset. With a weak reference it would get cleared eventually.
     */
    private static WeakReference<AlertDialog> sDialog = new WeakReference<>(null);


    // List of constants used to request runtime permission. Used to uniquely identify each
    // request. If you add new ones, please add a new number, respecting the order.
    //NOTE: These need to be a value from 0-255 otherwise it will cause a IllegalArgumentException: Can only use lower 8 bits for requestCode
    public static final int PERMISSION_IGNORE_REQUEST = 0; //use when you don't care about response
    public static final int PERMISSION_RECORD_AUDIO_FOR_VOICE_CALL = 20;
    public static final int PERMISSION_CAMERA_REQUEST = 23;
    public static final int PERMISSION_CAMERA_FOR_VIDEO_CALL = 24;

    //need multiple request code definitions for some permissions when activity needs to take different actions
    //if permission is granted depending on what user action triggered it
    public static final int PERMISSION_WRITE_EXTERNAL_STORAGE_REQUEST_TO_ATTACH_PICTURES = 27;
    public static final int PERMISSION_WRITE_EXTERNAL_STORAGE_REQUEST_TO_ATTACH_FILES = 28;
    public static final int PERMISSION_WRITE_EXTERNAL_STORAGE_REQUEST_TO_ATTACH_CAMERA = 29;

    private static synchronized void setDialog(final AlertDialog newDialog) {
        sDialog = new WeakReference<>(newDialog);
    }

    /**
     * Helper to determine if the permission is granted.
     *
     * @param grantResults List of granted results, must be non null
     * @param index        The index of the array to use.
     * @return true if granted.
     */
    public static boolean isGranted(final @NonNull int[] grantResults, final int index) {
        return grantResults.length >= (index + 1) && (grantResults[index] == PackageManager.PERMISSION_GRANTED);
    }

    /**
     * If this is on a Marshmallow or higher device this will check if the specified permission is granted.
     * This will not prompt the user for the permission.
     *
     * @param permission the permission to check
     * @return true if this is lower than M (we don't bother checking),
     * true if the permission is granted,
     * false if the permission is denied (M or higher)
     */
    public static boolean checkSelfPermission(@NonNull Context context, @NonNull String permission) {
        if (isMarshmallowOrHigher()) {
            if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
                Logger.d("PermissionsUtil.checkSelfPermission: granted for: " + permission);
                return true;
            } else {
                //log at info level in case user complains about broken functionality and doesn't realize they denied it
                Logger.i("PermissionsUtil.checkSelfPermission: DENIED for: " + permission);
                return false;
            }
        } else {
            //this is lower than M so don't check, just return true
            return true;
        }
    }

    public static boolean checkOrPromptSelfPermission(@NonNull Activity activity, @NonNull String permission,
                                                      final int requestCode, @StringRes int rationaleResId) {
        //let user cancel, but don't take any extra actions for this activity
        return !isMarshmallowOrHigher() || checkOrPromptSelfPermission(activity, permission, requestCode, rationaleResId, sEmptyOnCancelListener);
    }

    public static boolean checkOrPromptSelfPermission(@NonNull Activity activity, @NonNull String permission,
                                                      final int requestCode, @StringRes int rationaleResId,
                                                      OnCancelListener onCancelListener) {
        return checkOrPromptSelfPermission(activity, null, permission, requestCode, rationaleResId, onCancelListener);
    }

    /**
     * Call this to check for a permission and prompt user for it if not.
     *
     * If a fragment is included the permission request result will be returned to the fragment.
     *
     * @return true if we have the permission,
     * false if not and one of the following will happen:
     * 1) The user will be displayed the rationale, and asked for the permission if they choose
     * 2) The user will be asked for the permission
     * 3) The user will not be asked and we will be denied immediately (happens if user selected "Never ask again")
     * 4) Another dialog for this util class is already showing so this method just returns
     * since we will take action from the 1st dialog
     * For 1-3 the calling activity can handle the user action with its onRequestPermissionsResult()
     * or through the onCancelListener
     */
    public static boolean checkOrPromptSelfPermission(@NonNull Activity activity, @Nullable Fragment fragment, @NonNull String permission,
                                                      final int requestCode, @StringRes int rationaleResId,
                                                      OnCancelListener onCancelListener) {
        if (checkSelfPermission(activity, permission)) {
            return true;
        }

        Logger.d("PermissionsUtil.checkOrPromptSelfPermission: permission=" + permission + " requestCode=" + requestCode + " activity=" + activity);

        final AlertDialog alertDialog = sDialog.get();
        if (alertDialog != null && alertDialog.isShowing()) {
            Logger.d("PermissionsUtil.checkOrPromptSelfPermission: dialog already showing");
            return false;
        }

        if (ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)) {
            //we have already previously asked for the permission and the user hasn't selected "Never ask again" yet
            //so explain to them why we want this permission
            Logger.d("PermissionsUtil.checkOrPromptSelfPermission: displaying Permission Rationale for: " + permission);
            PermissionsUtil.displayPermissionRationale(activity, fragment,
                    activity.getResources().getString(R.string.permission_request_title),
                    activity.getResources().getString(rationaleResId),
                    permission,
                    requestCode,
                    onCancelListener);
        } else {
            //either we have never asked the user for this permission yet or the user selected "Never ask again"
            //so just ask them. If the user selected "Never ask again" then they will not see anything but the
            //activity onRequestPermissionsResult will be immediately called with denied
            Logger.d("PermissionsUtil.checkOrPromptSelfPermission: requesting Permissions for: " + permission);
            if (fragment != null) {
                //Request permission via the fragment to get the onRequestPermissionResult callback in the fragment
                fragment.requestPermissions(new String[]{permission}, requestCode);
            } else {
                ActivityCompat.requestPermissions(activity, new String[]{permission}, requestCode);
            }
        }

        Logger.d("PermissionsUtil.checkOrPromptSelfPermission: done ");
        return false;
    }

    public static void displayCanNotContinue(@NonNull final Activity activity,
                                             @NonNull final String permission,
                                             final int reasonId,
                                             final int requestCode) {
        displayCanNotContinue(activity, permission, reasonId, requestCode, sEmptyOnCancelListener);
    }
    /**
     * An activity should call this when a permission was denied and the action the user requested can not be done
     * unless they change their mind and give us that permission.
     * This will display a dialog with and OK button that calls the onCancelListener, and either:
     * 1) A "ASK AGAIN" button that will open the google dialog prompting the user for the permission again
     * 2) A "SETTINGS" button that will open the system settings to the BBM app settings.
     * From this point the user would then need to know they need to click the "Permissions" item
     * (could be different on some devices) then they need to flip the toggle for the permission
     * (normally named differently, like "Storage" in the settings app, but
     * "photos, media, and files" in google permission dialog).
     * This happens when the user selected "Never ask again" previously.
     */
    public static void displayCanNotContinue(@NonNull final Activity activity,
                                             @NonNull final String permission,
                                             final int reasonId,
                                             final int requestCode,
                                             final OnCancelListener onCancelListener) {
        Logger.d("PermissionsUtil.displayCanNotContinue: permission=" + permission + " requestCode=" + requestCode + " activity=" + activity);

        final AlertDialog old = sDialog.get();
        if (old != null && old.isShowing()) {
            Logger.d("PermissionsUtil.displayCanNotContinue: dialog already showing");
            return;
        }

        // Show dialog explaining need for permission.
        AlertDialog.Builder redirectBuilder = new AlertDialog.Builder(activity, R.style.AppSplashDialog);

        redirectBuilder.setTitle(activity.getResources().getString(R.string.permission_denied_title));
        redirectBuilder.setMessage(activity.getResources().getString(reasonId));

        //this doesn't exactly mean we can ask again, but is close enough
        final boolean canAskAgain = ActivityCompat.shouldShowRequestPermissionRationale(activity, permission);

        if (canAskAgain) {
            redirectBuilder.setPositiveButton(activity.getResources().getString(R.string.ask_again_button),
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            Logger.d("PermissionsUtil.displayCanNotContinue: will ask again");
                            ActivityCompat.requestPermissions(activity, new String[]{permission}, requestCode);
                        }
                    });
        } else {
            redirectBuilder.setPositiveButton(activity.getResources().getString(R.string.settings),
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            Logger.d("PermissionsUtil.displayCanNotContinue: opening settings");
                            Intent intent = new Intent();
                            intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                            intent.setData(Uri.fromParts("package", activity.getPackageName(), null));
                            activity.startActivity(intent);
                            dialog.dismiss();
                            setDialog(null);
                        }
                    });
        }

        if (onCancelListener != null) {
            redirectBuilder.setOnCancelListener(new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                    Logger.d("PermissionsUtil.displayCanNotContinue: canceled");
                    onCancelListener.onCancel();
                    setDialog(null);
                }
            });
            redirectBuilder.setNeutralButton(R.string.do_not_ask_now_button, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    Logger.d("PermissionsUtil.displayCanNotContinue: neutral button clicked");
                    onCancelListener.onCancel();
                    setDialog(null);
                }
            });
        }

        final AlertDialog alertDialog = redirectBuilder.create();
        setDialog(alertDialog);
        alertDialog.show();

        Logger.d("PermissionsUtil.displayCanNotContinue: showing dialog for canAskAgain=" + canAskAgain);
    }

    /**
     * WARNING: Only call this from the Activity onRequestPermissionsResult() if your permission is
     * denied (which only happens after calling a check for permission method).
     * Otherwise if you call this first, and the user has never been asked for the permission then
     * the google API we use returns the same value as if the user selected "Never ask again"
     * which would prevent the activity from ever asking.
     * <p/>
     * The Activity should call this when the user requested an action but denied the permission
     * and the action can not continue without the permission but there is no further action needed.
     * This should be called to handle the situation where the user previously selected "Never ask again"
     * so none of the util methods here can actually ever ask the user so we need to just display a dialog
     * explaining to the user that we can't do what they ask without the permission, and unfortunately since
     * we can't ask them again all we can do is show a "Settings" button to let them try to figure out how
     * to enable it from the device settings...
     */
    public static void displayCanNotContinueIfCanNotAsk(@NonNull final Activity activity,
                                                        @NonNull final String permission,
                                                        final int reasonId) {
        final boolean canAskAgain = ActivityCompat.shouldShowRequestPermissionRationale(activity, permission);
        Logger.d("PermissionsUtil.displayCanNotContinueIfCanNotAsk: canAskAgain=" + canAskAgain + " permission=" + permission + " activity=" + activity);
        if (!canAskAgain) {
            //this will just display the settings version so we don't need requestCode or listener
            displayCanNotContinue(activity, permission, reasonId, PERMISSION_IGNORE_REQUEST, sEmptyOnCancelListener);
        }
    }

    private static void displayPermissionRationale(@NonNull final Activity activity, final Fragment fragment, @NonNull final String title,
                                                  @NonNull final String reason, @NonNull final String permission,
                                                  final int requestCode,
                                                  final OnCancelListener onCancelListener) {

        final AlertDialog old = sDialog.get();
        if (old != null && old.isShowing()) {
            Logger.d("PermissionsUtil.displayPermissionRationale: dialog already showing");
            return;
        }

        // Show dialog explaining need for permission.
        AlertDialog.Builder rationaleBuilder = new AlertDialog.Builder(activity, R.style.AppSplashDialog);

        rationaleBuilder.setTitle(title);
        rationaleBuilder.setMessage(reason);

        if (onCancelListener != null) {
            //allow user to cancel
            rationaleBuilder.setOnCancelListener(new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                    Logger.d("PermissionsUtil.displayPermissionRationale: canceled");
                    onCancelListener.onCancel();
                    setDialog(null);
                }
            });
            rationaleBuilder.setNeutralButton(R.string.cancel, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    Logger.d("PermissionsUtil.displayPermissionRationale: neutral button clicked");
                    onCancelListener.onCancel();
                    setDialog(null);
                }
            });
        } else {
            //caller didn't provider cancel listener, don't let user cancel
            rationaleBuilder.setCancelable(false);
        }

        rationaleBuilder.setPositiveButton(activity.getResources().getString(R.string.button_continue), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (fragment != null) {
                    fragment.requestPermissions(new String[]{permission}, requestCode);
                } else {
                    ActivityCompat.requestPermissions(activity, new String[]{permission}, requestCode);
                }
                dialog.dismiss();
                setDialog(null);
            }
        });

        final AlertDialog alertDialog = rationaleBuilder.create();
        setDialog(alertDialog);
        alertDialog.show();

        Logger.d("PermissionsUtil.displayPermissionRationale: showing dialog");
    }

    /**
     * Helper when logging results for Activity onRequestPermissionsResult() to format for logging
     */
    public static String resultsToString(@NonNull String[] permissions, @NonNull int[] grantResults) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < permissions.length && i < grantResults.length; ++i) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(permissions[i]).append('=');
            if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                sb.append("granted");
            } else {
                sb.append("DENIED");
            }
        }
        return sb.toString();
    }

    private static boolean isMarshmallowOrHigher() {
        return Build.VERSION.SDK_INT >= 23;
    }
}
