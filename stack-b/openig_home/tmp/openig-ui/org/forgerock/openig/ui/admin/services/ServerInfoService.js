"use strict";

var _createClass = function () { function defineProperties(target, props) { for (var i = 0; i < props.length; i++) { var descriptor = props[i]; descriptor.enumerable = descriptor.enumerable || false; descriptor.configurable = true; if ("value" in descriptor) descriptor.writable = true; Object.defineProperty(target, descriptor.key, descriptor); } } return function (Constructor, protoProps, staticProps) { if (protoProps) defineProperties(Constructor.prototype, protoProps); if (staticProps) defineProperties(Constructor, staticProps); return Constructor; }; }();

function _classCallCheck(instance, Constructor) { if (!(instance instanceof Constructor)) { throw new TypeError("Cannot call a class as a function"); } }

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

define(["jquery", "org/forgerock/openig/ui/admin/services/ServerUrls", "org/forgerock/commons/ui/common/main/AbstractDelegate"], function ($, serverUrls, AbstractDelegate) {
    var ServerInfoService = function () {
        function ServerInfoService() {
            _classCallCheck(this, ServerInfoService);

            this.infoDelegate = new AbstractDelegate(serverUrls.apiPath + "/info");
        }

        _createClass(ServerInfoService, [{
            key: "getInfo",
            value: function getInfo() {
                var _this = this;

                var deferred = $.Deferred();
                if (this.dataCache) {
                    return deferred.resolve(this.dataCache);
                }
                this.infoDelegate.serviceCall({}).done(function (data) {
                    _this.dataCache = data;
                    deferred.resolve(data);
                }).fail(function () {
                    console.log("ServerInfoService - no info data");
                    deferred.resolve();
                });
                return deferred;
            }
        }]);

        return ServerInfoService;
    }();

    return new ServerInfoService();
});
//# sourceMappingURL=ServerInfoService.js.map
