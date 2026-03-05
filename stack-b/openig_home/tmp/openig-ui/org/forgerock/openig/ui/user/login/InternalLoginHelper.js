"use strict";

/**
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2016 ForgeRock AS.
 */

define(["jquery", "org/forgerock/commons/ui/common/main/AbstractConfigurationAware", "org/forgerock/commons/ui/common/main/Configuration"], function ($, AbstractConfigurationAware, conf) {
    var loginHelper = new AbstractConfigurationAware();

    loginHelper.login = function () {
        return $.Deferred().resolve(conf.loggedUser);
    };

    loginHelper.logout = function (successCallback) {
        if (successCallback) {
            successCallback();
        }
        return $.Deferred().resolve();
    };

    loginHelper.getLoggedUser = function (successCallback, errorCallback) {
        if (conf.loggedUser && successCallback) {
            successCallback(conf.loggedUser);
        } else if (errorCallback) {
            errorCallback();
        }
    };

    return loginHelper;
});
//# sourceMappingURL=InternalLoginHelper.js.map
