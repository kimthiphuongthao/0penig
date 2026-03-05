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

define(["lodash", "i18next", "org/forgerock/openig/ui/admin/routes/AbstractAuthenticationFilterView"], function (_, i18n, AbstractAuthenticationFilterView) {
    return function (_AbstractAuthenticati) {
        _inherits(OpenIDAuthentication, _AbstractAuthenticati);

        function OpenIDAuthentication() {
            _classCallCheck(this, OpenIDAuthentication);

            return _possibleConstructorReturn(this, (OpenIDAuthentication.__proto__ || Object.getPrototypeOf(OpenIDAuthentication)).apply(this, arguments));
        }

        _createClass(OpenIDAuthentication, [{
            key: "initialize",
            value: function initialize(options) {
                this.data = _.clone(options.data);
                this.data.formId = "openid-authentication-form";
                this.filterCondition = { "type": "OAuth2ClientFilter" };
                this.translatePath = "templates.routes.parts.openIDAuthentication";
                this.settingTitle = i18n.t(this.translatePath + ".title");
                this.initializeFilter();
                this.prepareControls();
            }
        }, {
            key: "prepareControls",
            value: function prepareControls() {
                this.data.controls = [{
                    name: "clientFilterGroup",
                    controlType: "group",
                    controls: [{
                        name: "clientEndpoint",
                        value: this.data.filter.clientEndpoint
                    }, {
                        name: "requireHttps",
                        value: this.data.filter.requireHttps,
                        controlType: "slider"
                    }]
                }, {
                    name: "clientRegistrationGroup",
                    controlType: "group",
                    controls: [{
                        name: "clientId",
                        value: this.data.filter.clientId,
                        validator: "required"
                    }, {
                        name: "clientSecret",
                        value: this.data.filter.clientSecret,
                        validator: "required"
                    }, {
                        name: "scopes",
                        value: this.data.filter.scopes,
                        controlType: "multiselect",
                        options: "openid profile email address phone offline_access",
                        delimiter: " ",
                        mandatory: "openid"
                    }, {
                        name: "tokenEndpointUseBasicAuth",
                        value: this.data.filter.tokenEndpointUseBasicAuth,
                        controlType: "slider"
                    }]
                }, {
                    name: "issuerGroup",
                    controlType: "group",
                    controls: [{
                        name: "issuerWellKnownEndpoint",
                        value: this.data.filter.issuerWellKnownEndpoint,
                        validator: "required uri"
                    }]
                }];
            }
        }, {
            key: "createFilter",
            value: function createFilter() {
                return {
                    type: "OAuth2ClientFilter",
                    scopes: "openid",
                    requireHttps: true,
                    tokenEndpointUseBasicAuth: true
                };
            }
        }, {
            key: "partials",
            get: function get() {
                return ["templates/openig/admin/common/form/EditControl.html", "templates/openig/admin/common/form/SliderControl.html", "templates/openig/admin/common/form/GroupControl.html", "templates/openig/admin/common/form/MultiSelectControl.html"];
            }
        }]);

        return OpenIDAuthentication;
    }(AbstractAuthenticationFilterView);
});
//# sourceMappingURL=OpenIDAuthentication.js.map
