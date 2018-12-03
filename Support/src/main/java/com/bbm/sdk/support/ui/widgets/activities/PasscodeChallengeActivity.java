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

package com.bbm.sdk.support.ui.widgets.activities;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.EditText;
import android.widget.TextView;

import com.bbm.sdk.support.R;
import com.bbm.sdk.support.protect.KeySource;
import com.bbm.sdk.support.protect.PasscodeProvider;
import com.bbm.sdk.support.util.KeySourceManager;

/**
 * Challenge a user to provide a password.
 */
public class PasscodeChallengeActivity extends Activity {

    public static final String EXTRA_ALLOW_CANCEL = "allow_cancel";
    public static final String EXTRA_ACTION_NEW_PASSWORD = "new_password";
    public static final String EXTRA_PREVIOUS_ERROR = "previous_error_type";

    private KeySource mKeySource;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().requestFeature(Window.FEATURE_NO_TITLE);
        boolean allowCancel = getIntent().getBooleanExtra(EXTRA_ALLOW_CANCEL, false);
        if (getIntent().getBooleanExtra(EXTRA_ACTION_NEW_PASSWORD, false)) {
            newPasscodeChallenge(allowCancel);
        } else {
            existingPasscodeChallenge(allowCancel);
        }
        mKeySource = KeySourceManager.getKeySource();
        if (mKeySource == null) {
            finish();
        }
    }

    public void onSetPasscode(@NonNull String passcode) {
        mKeySource.setPasscode(passcode);
    }

    public void onForgotPasscode() {
        mKeySource.forgotPasscode();
    }

    private void newPasscodeChallenge(boolean allowCancel) {
        LayoutInflater inflater = LayoutInflater.from(this);
        @SuppressLint("InflateParams") View view = inflater.inflate(R.layout.dialog_password_prompt, null);

        EditText passwordTextView = view.findViewById(R.id.password_input);
        EditText passwordConfTextView = view.findViewById(R.id.password_confirmation_input);
        TextView errorTextView = view.findViewById(R.id.password_error_message);
        passwordTextView.setHint(R.string.new_password_hint);
        passwordTextView.setVisibility(View.VISIBLE);
        passwordConfTextView.setVisibility(View.VISIBLE);
        TextView titleView = view.findViewById(R.id.password_prompt_title);
        titleView.setText(R.string.new_password_text);
        titleView.setVisibility(View.VISIBLE);

        AlertDialog alertDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.provide_new_password)
                .setPositiveButton(R.string.OK, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                    }
                })
                .setView(view)
                .setCancelable(allowCancel)
                .create();
        alertDialog.show();
        alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String password = passwordTextView.getText().toString().trim();
                String passwordConf = passwordConfTextView.getText().toString().trim();
                if (password.equals(passwordConf) && meetsPasswordRules(password)) {
                    onSetPasscode(passwordConf);
                    alertDialog.dismiss();
                } else {
                    errorTextView.setVisibility(View.VISIBLE);
                    if (!meetsPasswordRules(password)) {
                        errorTextView.setText(R.string.password_not_valid);
                    } else {
                        errorTextView.setText(R.string.passwords_do_not_match);
                    }
                }
            }
        });
        alertDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialogInterface) {
                finish();
            }
        });
        alertDialog.setOnKeyListener(new DialogInterface.OnKeyListener() {
            @Override
            public boolean onKey(DialogInterface dialogInterface, int i, KeyEvent keyEvent) {
                if (keyEvent.getKeyCode() == KeyEvent.KEYCODE_ENTER) {
                    alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).callOnClick();
                    return true;
                }
                return false;
            }
        });
        setErrorText(errorTextView);

        TextWatcher watcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void afterTextChanged(Editable editable) {
                String password = passwordTextView.getText().toString().trim();
                String passwordConf = passwordConfTextView.getText().toString().trim();
                if (meetsPasswordRules(password)) {
                    passwordTextView.setCompoundDrawablesWithIntrinsicBounds(0,0, R.drawable.ic_check, 0);
                    if (password.equals(passwordConf)) {
                        passwordConfTextView.setCompoundDrawablesWithIntrinsicBounds(0,0, R.drawable.ic_check, 0);
                    } else {
                        passwordConfTextView.setCompoundDrawablesWithIntrinsicBounds(0,0, R.drawable.ic_clear, 0);
                    }
                } else {
                    passwordTextView.setCompoundDrawablesWithIntrinsicBounds(0,0, R.drawable.ic_clear, 0);
                }
            }
        };

        passwordTextView.addTextChangedListener(watcher);
        passwordConfTextView.addTextChangedListener(watcher);
    }

    private void setErrorText(TextView errorTextView) {
        //Display an error string if one was provided.
        String errorString = getIntent().getStringExtra(EXTRA_PREVIOUS_ERROR);
        PasscodeProvider.PasscodeError errorType = PasscodeProvider.PasscodeError.valueOf(errorString);
        switch (errorType) {
            case IncorrectPasscode:
                errorTextView.setText(R.string.incorrect_password);
                break;
            case SyncFailure:
                errorTextView.setText(R.string.sync_failure);
                break;
            case SyncTimeout:
                errorTextView.setText(R.string.sync_timeout);
                break;
            case TemporaryFailure:
                errorTextView.setText(R.string.key_change_temp_failure);
                break;
            case None:
            default:
                break;
        }
        errorTextView.setVisibility(errorType != PasscodeProvider.PasscodeError.None ? View.VISIBLE : View.GONE);
    }

    private void existingPasscodeChallenge(boolean allowCancel) {

        LayoutInflater inflater = LayoutInflater.from(this);
        @SuppressLint("InflateParams") View view = inflater.inflate(R.layout.dialog_password_prompt, null);

        EditText passwordTextView = view.findViewById(R.id.password_input);
        TextView errorTextView = view.findViewById(R.id.password_error_message);
        final boolean[] forgotPasswordClicked = {false};

        AlertDialog alertDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.provide_existing_password)
                .setPositiveButton(R.string.OK, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                    }
                })
                .setNegativeButton(R.string.forgot_password, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        forgotPasswordClicked[0] = true;
                        showForgot(allowCancel);
                    }
                })
                .setView(view)
                .setCancelable(allowCancel)
                .create();
        alertDialog.show();
        alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String password = passwordTextView.getText().toString().trim();
                onSetPasscode(password);
                alertDialog.dismiss();
            }
        });
        alertDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialogInterface) {
                if (!forgotPasswordClicked[0]) {
                    finish();
                }
            }
        });
        alertDialog.setOnKeyListener(new DialogInterface.OnKeyListener() {
            @Override
            public boolean onKey(DialogInterface dialogInterface, int i, KeyEvent keyEvent) {
                if (keyEvent.getKeyCode() == KeyEvent.KEYCODE_ENTER) {
                    alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).callOnClick();
                    return true;
                }
                return false;
            }
        });
        setErrorText(errorTextView);
    }

    private void showForgot(boolean allowCancel) {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setMessage(R.string.forgot_password_warning)
                .setPositiveButton(R.string.button_continue, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        onForgotPasscode();
                    }
                })
                .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        existingPasscodeChallenge(allowCancel);
                    }
                })
                .create();
        dialog.show();
    }

    private boolean meetsPasswordRules(String password) {
        if (password.length() >= 8) {
            return true;
        }

        return false;
    }

}
