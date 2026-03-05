"use strict";

var _createClass = function () { function defineProperties(target, props) { for (var i = 0; i < props.length; i++) { var descriptor = props[i]; descriptor.enumerable = descriptor.enumerable || false; descriptor.configurable = true; if ("value" in descriptor) descriptor.writable = true; Object.defineProperty(target, descriptor.key, descriptor); } } return function (Constructor, protoProps, staticProps) { if (protoProps) defineProperties(Constructor.prototype, protoProps); if (staticProps) defineProperties(Constructor, staticProps); return Constructor; }; }();

function _classCallCheck(instance, Constructor) { if (!(instance instanceof Constructor)) { throw new TypeError("Cannot call a class as a function"); } }

/*
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

define(["org/forgerock/commons/ui/common/util/URIUtils"], function (URIUtils) {
    var ServerUrls = function () {
        function ServerUrls(pathname) {
            _classCallCheck(this, ServerUrls);

            // Infer base path from URL
            var index = pathname.indexOf("/studio");
            this.context = pathname.substring(0, index);
        }

        _createClass(ServerUrls, [{
            key: "apiPath",
            get: function get() {
                return this.context + "/api";
            }
        }, {
            key: "systemObjectsPath",
            get: function get() {
                return this.apiPath + "/system/objects";
            }
        }]);

        return ServerUrls;
    }();

    return new ServerUrls(URIUtils.getCurrentPathName());
});
//# sourceMappingURL=ServerUrls.js.map
