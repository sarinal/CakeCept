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

package com.bbm.sdk.support.protect;

import com.bbm.sdk.bbmds.inbound.ChatKeysImportFailure;
import com.bbm.sdk.bbmds.inbound.ProfileKeysImportFailure;
import com.bbm.sdk.bbmds.inbound.UserKeysImportFailure;

public interface KeyImportFailureListener {

    void onProfileKeysImportFailure(ProfileKeysImportFailure profileKeysImportFailure);

    void onUserKeysImportFailure(UserKeysImportFailure userKeysImportFailure);

    void onChatKeysImportFailure(ChatKeysImportFailure chatKeysImportFailure);

}
