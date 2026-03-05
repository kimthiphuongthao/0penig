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

define(["jquery", "lodash", "i18next", "org/forgerock/openig/ui/admin/routes/AbstractRouteView", "org/forgerock/commons/ui/common/main/EventManager", "org/forgerock/commons/ui/common/util/Constants", "org/forgerock/commons/ui/common/main/Router", "org/forgerock/openig/ui/admin/models/RoutesCollection", "org/forgerock/openig/ui/admin/routes/parts/SettingsPanel"], function ($, _, i18n, AbstractRouteView, eventManager, constants, Router, RoutesCollection, SettingsPanel) {
    return function (_AbstractRouteView) {
        _inherits(Settings, _AbstractRouteView);

        function Settings() {
            _classCallCheck(this, Settings);

            var _this = _possibleConstructorReturn(this, (Settings.__proto__ || Object.getPrototypeOf(Settings)).call(this));

            _this.element = ".main";
            _this.data = _.extend(_this.data, { formId: "settings" });
            _this.translationPath = "templates.routes.parts.settings.fields";
            _this.settingsPanel = new SettingsPanel();
            return _this;
        }

        _createClass(Settings, [{
            key: "render",
            value: function render() {
                var _this2 = this;

                this.data.routePath = Router.getCurrentHash().match(Router.currentRoute.url)[1];
                RoutesCollection.byRouteId(this.data.routePath).then(function (routeData) {
                    _this2.routeData = routeData;
                    _this2.parentRender(function () {
                        _this2.settingsPanel.setup({ route: routeData });
                        _this2.settingsPanel.render();
                    });
                });
            }
        }, {
            key: "saveClick",
            value: function saveClick(event) {
                var _this3 = this;

                event.preventDefault();
                this.settingsPanel.save().then(function () {
                    eventManager.sendEvent(constants.EVENT_DISPLAY_MESSAGE_REQUEST, {
                        key: "routeSettingsSaveSuccess",
                        filter: _this3.routeData.get("name")
                    });
                }, function () {
                    eventManager.sendEvent(constants.EVENT_DISPLAY_MESSAGE_REQUEST, {
                        key: "routeSettingsSaveFailed",
                        filter: _this3.routeData.get("name")
                    });
                });
            }
        }, {
            key: "resetClick",
            value: function resetClick(event) {
                event.preventDefault();
                eventManager.sendEvent(constants.EVENT_CHANGE_VIEW, {
                    route: Router.configuration.routes.routeSettings, args: [this.routeData.get("id")]
                });
            }
        }, {
            key: "template",
            get: function get() {
                return "templates/openig/admin/routes/parts/Settings.html";
            }
        }, {
            key: "partials",
            get: function get() {
                return ["templates/openig/admin/routes/components/FormFooter.html"];
            }
        }, {
            key: "events",
            get: function get() {
                return {
                    "click .js-save-btn": "saveClick",
                    "click .js-reset-btn": "resetClick"
                };
            }
        }]);

        return Settings;
    }(AbstractRouteView);
});
//# sourceMappingURL=Settings.js.map
