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

import android.os.Looper;
import android.util.Log;

/**
 * A wrapper around the android logcat that is used by BBM SDK example apps.
 * This simplifies changing source code from BBM SDK example apps to output to another logging utility.
 */
public class Logger {
    public static final String TAG = "com.bbm.example";
    public static final String GESTURE_TAG = TAG + ".gesture";
    public static final String USER_TAG = TAG + ".USER";
    public static final String PRIVATE_TAG = TAG + ".priv";

    /**
     * This will add the class name and method name that called the logging method
     * this has a performance cost, so should normally be off
     */
    private static final boolean LOG_CALLER_CLASS_METHOD_NAME = false;

    //add the thread name to log message
    private static final boolean LOG_THREAD_INFO = false;

    private static final boolean LOG_PRIVATE_INFO = false;

    /**
     * The code that actually writes to logging output.  Default implementation goes to android Logcat
     */
    private static LogWriter sLogWriter = new LogWriter() {
        @Override
        public void log(int priority, Throwable t, String str, String tag) {
            Log.println(priority, tag, formatMessage(t, str));
        }
    };

    /**
     * Set the log writer implementation.
     * This allows writing to a different output other than android logcat.
     */
    public static synchronized void setLogWriter(LogWriter logWriter) {
        sLogWriter = logWriter;
    }

    public static void e(final Throwable t) {
        log(Log.ERROR, t, null, null);
    }

    public static void e(final String str) {
        log(Log.ERROR, null, str);
    }

    public static void e(final Throwable t, final String str) {
        log(Log.ERROR, t, str);
    }

    public static void w(final Throwable t) {
        log(Log.WARN, t, null, null);
    }

    public static void w(final String str) {
        log(Log.WARN, null, str);
    }

    public static void w(final Throwable t, final String str) {
        log(Log.WARN, t, str);
    }

    public static void i(final Throwable t) {
        log(Log.INFO, t, null, null);
    }

    public static void i(final String str) {
        log(Log.INFO, null, str);
    }

    public static void i(final String str, final Class cls) {
        log(Log.INFO, null, cls.getName() + ": " + str);
    }

    public static void i(final Throwable t, final String str) {
        log(Log.INFO, t, str);
    }

    public static void d(final Throwable t) {
        log(Log.DEBUG, t, null, null);
    }

    public static void d(final String str) {
        log(Log.DEBUG, null, str);
    }

    public static void d(final String str, final Class cls) {
        log(Log.DEBUG, null, cls.getName() + ": " + str);
    }


    public static void d(final Throwable t, final String str) {
        log(Log.DEBUG, t, str);
    }


    public static void v(final Throwable t) {
        log(Log.VERBOSE, t, null, null);
    }

    public static void v(final String str) {
        log(Log.VERBOSE, null, str);
    }

    public static void v(final Throwable t, final String str) {
        log(Log.VERBOSE, t, str);
    }

    /**
     * Log data that is potentially private data for the user and shouldn't be logged.
     * This checks the value of LOG_PRIVATE_INFO and does nothing if it is false.
     * The rules for this would be app specific and any application taking code from sample SDK apps should
     * check all existing logging statements and adjust to use this method or not as appropriate.
     *
     * @param t optional throwable to log the message and stack trace for
     * @param str the message with potentially private data to log
     */
    public static void priv(final Throwable t, final String str) {
        if (LOG_PRIVATE_INFO) {
            log(Log.DEBUG, t, str, PRIVATE_TAG);
        }
    }

    /**
     * Log data that is potentially private data for the user and shouldn't be logged.
     * This checks the value of LOG_PRIVATE_INFO and does nothing if it is false.
     * The rules for this would be app specific and any application taking code from sample SDK apps should
     * check all existing logging statements and adjust to use this method or not as appropriate.
     *
     * @param t the throwable with potentially private data to log
     */
    public static void priv(final Throwable t) {
        if (LOG_PRIVATE_INFO) {
            log(Log.DEBUG, t, null, PRIVATE_TAG);
        }
    }

    /**
     * Log data that is potentially private data for the user and shouldn't be logged.
     * This checks the value of LOG_PRIVATE_INFO and does nothing if it is false.
     * The rules for this would be app specific and any application taking code from sample SDK apps should
     * check all existing logging statements and adjust to use this method or not as appropriate.
     *
     * @param str the message with potentially private data to log
     */
    public static void priv(final String str) {
        if (LOG_PRIVATE_INFO) {
            log(Log.DEBUG, null, str, PRIVATE_TAG);
        }
    }

    /**
     * This should only be rarely used in the case where the message could be displayed to the user.
     * The default implementation is to just write this to the normal log, but a sample or protype app could
     * choose to handle these by displaying as a toast to the user.
     * This just provides the ability for common code to write messages under certain situations that the
     * app could display to the developer or tester.
     */
    public static void user(final String userMessage) {
        log(Log.WARN, null, userMessage, USER_TAG);
    }

    public static void user(final Throwable t, final String userMessage) {
        log(Log.WARN, t, userMessage, USER_TAG);
    }

    public static void gesture(final String gesture, final Class activity) {
        log(Log.INFO, null, gesture + " in " +activity, GESTURE_TAG);
    }

    public static String formatMessage(final Throwable t, final String str) {
        if (t == null && !LOG_CALLER_CLASS_METHOD_NAME && !LOG_THREAD_INFO) {
            //avoid cost of creating buffer
            return str;
        }

        StringBuilder msg = new StringBuilder();

        if (LOG_THREAD_INFO) {
            final Thread thread = Thread.currentThread();
            msg.append('[').append(thread.getName());

            //This is the "UI" thread, or the main thread if running in another process
            if (Looper.getMainLooper().getThread() == thread) {
                msg.append("(UI)");
            }
            msg.append(']');
        }

        if (LOG_CALLER_CLASS_METHOD_NAME) {
            final StackTraceElement[] stack = Thread.currentThread().getStackTrace();
            if (stack != null && stack.length > 2) {
                //check class names up the stack until find this one (top will be Thread and system classes), then one different than this
                String currentClass = Logger.class.getName();
                String logWriterClass = null;
                if (sLogWriter != null) {
                    logWriterClass = sLogWriter.getClass().getName();
                }
                boolean foundThis = false;
                for (int i = 0; i < stack.length; ++i) {
                    final String stackClassName = stack[i].getClassName();
                    if (currentClass.equals(stackClassName) || (logWriterClass != null && logWriterClass.equals(stackClassName))) {
                        foundThis = true;
                    } else if (foundThis) {
                        //we found a caller outside this class after this class
                        //don't use toString, just append short version
                        final StackTraceElement element = stack[i];
                        //we could shorten further by just adding class name without package, or just file name, but this is more unique

                        String className = element.getClassName();
                        int lastDot = className.lastIndexOf('.');
                        if (lastDot > 0) {
                            //display the class name with leading dot, but without the package name
                            className = className.substring(lastDot);
                        }


                        msg.append('[').append(className).append('.').append(element.getMethodName())
                                .append(':').append(element.getLineNumber()).append(']');
                        break;
                    }
                }
            }
        }

        if (LOG_THREAD_INFO || LOG_CALLER_CLASS_METHOD_NAME) {
            msg.append(' ');
        }

        msg.append(str);

        if (t != null) {
            msg.append('\n').append(Log.getStackTraceString(t));
        }

        return msg.toString();
    }

    protected static void log(final int priority, final Throwable t, final String str) {
        log(priority, t, str, TAG);
    }

    protected static void log(final int priority, final Throwable t, final String str, final String tag) {
        if (sLogWriter != null) {
            sLogWriter.log(priority, t, str, tag);
        }
    }

    public interface LogWriter {
        void log(final int priority, final Throwable t, final String str, final String tag);
    }
}