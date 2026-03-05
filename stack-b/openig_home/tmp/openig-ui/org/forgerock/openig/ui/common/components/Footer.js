"use strict";

var _createClass = function () { function defineProperties(target, props) { for (var i = 0; i < props.length; i++) { var descriptor = props[i]; descriptor.enumerable = descriptor.enumerable || false; descriptor.configurable = true; if ("value" in descriptor) descriptor.writable = true; Object.defineProperty(target, descriptor.key, descriptor); } } return function (Constructor, protoProps, staticProps) { if (protoProps) defineProperties(Constructor.prototype, protoProps); if (staticProps) defineProperties(Constructor, staticProps); return Constructor; }; }();

function _classCallCheck(instance, Constructor) { if (!(instance instanceof Constructor)) { throw new TypeError("Cannot call a class as a function"); } }

function _possibleConstructorReturn(self, call) { if (!self) { throw new ReferenceError("this hasn't been initialised - super() hasn't been called"); } return call && (typeof call === "object" || typeof call === "function") ? call : self; }

function _inherits(subClass, superClass) { if (typeof superClass !== "function" && superClass !== null) { throw new TypeError("Super expression must either be null or a function, not " + typeof superClass); } subClass.prototype = Object.create(superClass && superClass.prototype, { constructor: { value: subClass, enumerable: false, writable: true, configurable: true } }); if (superClass) Object.setPrototypeOf ? Object.setPrototypeOf(subClass, superClass) : subClass.__proto__ = superClass; }

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

define(["org/forgerock/commons/ui/common/components/Footer", "org/forgerock/openig/ui/admin/services/ServerInfoService", "i18next", "xdate"], function (Footer, ServerInfoService, i18n, XDate) {
    var InfoFooter = function (_Footer) {
        _inherits(InfoFooter, _Footer);

        function InfoFooter() {
            _classCallCheck(this, InfoFooter);

            return _possibleConstructorReturn(this, (InfoFooter.__proto__ || Object.getPrototypeOf(InfoFooter)).apply(this, arguments));
        }

        _createClass(InfoFooter, [{
            key: "getVersion",
            value: function getVersion() {
                return ServerInfoService.getInfo().then(function (data) {
                    if (!data) {
                        return;
                    }
                    var date = new XDate(data.timestamp).toString(i18n.t("common.footer.versionDate"));
                    return i18n.t("common.footer.versionInfo", {
                        version: data.version,
                        revision: data.revision.slice(0, 10),
                        date: date
                    });
                });
            }
        }, {
            key: "showVersion",
            value: function showVersion() {
                return true;
            }
        }]);

        return InfoFooter;
    }(Footer);

    return new InfoFooter();
});
//# sourceMappingURL=Footer.js.map
