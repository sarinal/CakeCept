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

import com.bbm.sdk.common.IOHelper;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;

public class IOUtils {

    public static HttpURLConnection safeCloseAndSetNull(final HttpURLConnection urlConnection) {
        try {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }

        } catch (final Exception e) {
            // Ignore
        }
        return null;
    }

    /**
     * Helper to copy data from an input stream and return a byte array to the caller.
     *
     * @param in            The input stream to copy data from
     * @param contentLength The default content length to be used. Default size is 1K if negative
     *                      non-zero value is supplied.
     * @return A byte array of the data.
     * @throws IOException
     */
    public static byte[] toByteArray(final InputStream in, final int contentLength) throws IOException {
        // The ByteArrayOutputStream will expand the underlying byte buffer if more data arrives,
        // but it is expensive to do so. Thus try to set a size that is appropriate.
        int size = (contentLength > 1024) ? contentLength : 1024;
        final ByteArrayOutputStream os = new ByteArrayOutputStream(size);
        copy(in, os);
        try {
            return os.toByteArray();
        } finally {
            IOHelper.safeClose(os);
        }
    }

    public static void copy(final InputStream in, final OutputStream out) throws IOException {
        // Transfer bytes from in to out
        final byte[] buffer = new byte[4 * 1024];
        int len;
        while ((len = in.read(buffer)) != -1) {
            out.write(buffer, 0, len);
        }
        out.flush();
    }

    /**
     * Safely close the item
     * @param closeable the item to close
     */
    public static void safeClose(final Closeable closeable) {
        try {
            if (closeable != null) {
                closeable.close();
            }
        } catch (final Exception e) {
            // Ignore
        }
    }

}
