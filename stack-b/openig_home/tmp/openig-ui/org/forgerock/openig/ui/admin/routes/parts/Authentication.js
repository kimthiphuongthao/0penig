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

define(["jquery", "lodash", "form2js", "selectize", "i18next", "org/forgerock/openig/ui/admin/routes/AbstractRouteView", "org/forgerock/commons/ui/common/components/BootstrapDialog", "org/forgerock/commons/ui/common/util/UIUtils", "org/forgerock/openig/ui/admin/routes/parts/OpenIDAuthentication", "org/forgerock/openig/ui/admin/routes/parts/OpenAmSsoAuthentication"], function ($, _, form2js, selectize, i18n, AbstractRouteView, BootstrapDialog, UIUtils, OpenIDAuthentication, OpenAmSsoAuthentication) {
    return function (_AbstractRouteView) {
        _inherits(Authentication, _AbstractRouteView);

        function Authentication() {
            _classCallCheck(this, Authentication);

            return _possibleConstructorReturn(this, (Authentication.__proto__ || Object.getPrototypeOf(Authentication)).apply(this, arguments));
        }

        _createClass(Authentication, [{
            key: "initialize",
            value: function initialize(options) {
                this.data = options.parentData;
                this.settingTitle = i18n.t("templates.routes.parts.authentication.title");
                this.filters = {
                    openid: new OpenIDAuthentication({ data: this.data }),
                    sso: new OpenAmSsoAuthentication({ data: this.data })
                };
                this.prevFilterName = this.getActiveFilterName();
            }
        }, {
            key: "render",
            value: function render() {
                this.data.items = [{
                    icon: "fa-openid",
                    title: i18n.t("templates.routes.parts.authentication.fields.openID"),
                    hint: i18n.t("templates.routes.parts.authentication.fields.openIDHint"),
                    name: "openid",
                    enabled: this.filters.openid.isFilterEnabled()
                }, {
                    img: "img/forgerock-mark-white.png",
                    title: i18n.t("templates.routes.parts.authentication.fields.sso"),
                    hint: i18n.t("templates.routes.parts.authentication.fields.ssoHint"),
                    name: "sso",
                    enabled: this.filters.sso.isFilterEnabled()
                }];
                this.data.enabled = _.some(this.data.items, { enabled: true });
                if (this.data.enabled) {
                    this.setFilterOption(_.find(this.data.items, { enabled: true }).name);
                }
                this.parentRender();
            }
        }, {
            key: "getActiveFilterName",
            value: function getActiveFilterName() {
                var filterName = void 0;
                _.find(this.filters, function (filter, name) {
                    if (filter.isFilterEnabled()) {
                        filterName = name;
                        return true;
                    }
                });
                return filterName;
            }
        }, {
            key: "refreshOptions",
            value: function refreshOptions() {
                this.setFilterOption(this.getActiveFilterName());
            }
        }, {
            key: "enableAuthenticationClick",
            value: function enableAuthenticationClick(event) {
                var newState = event.currentTarget.checked;
                this.$el.find("#authOptions").toggle(newState);
                if (!newState) {
                    this.setFilterOption();
                }
            }
        }, {
            key: "radioClick",
            value: function radioClick(event) {
                this.setFilterOption(event.currentTarget.value);
            }
        }, {
            key: "setFilterOption",
            value: function setFilterOption(checkedName) {
                var _this2 = this;

                var dialogNotification = false;
                this.prevFilterName = this.getActiveFilterName();
                _.each(this.$el.find("input[type='radio']"), function (radio) {
                    var item = $(radio).closest(".js-radio-item");
                    var edit = item.find(".js-edit-panel");
                    var checked = checkedName === radio.value;
                    var filterItem = _this2.filters[radio.value];
                    var filter = filterItem.getFilter();
                    item.toggleClass("disabled", !checked);
                    edit.toggleClass("hidden", !checked);
                    if (filter) {
                        filterItem.toggleFilter(checked);
                    } else if (checked) {
                        _this2.showSettingsDialog(filterItem);
                        dialogNotification = true;
                    }
                    radio.checked = checked;
                });
                if (this.prevFilterName !== checkedName) {
                    this.data.routeData.save().then(function () {
                        if (!dialogNotification) {
                            _this2.showNotification(checkedName ? _this2.NOTIFICATION_TYPE.SaveSuccess : _this2.NOTIFICATION_TYPE.Disabled);
                        }
                    }, function () {
                        _this2.showNotification(_this2.NOTIFICATION_TYPE.SaveFailed);
                    });
                }
            }
        }, {
            key: "settingsClick",
            value: function settingsClick(event) {
                this.showSettingsDialog(this.filters[event.currentTarget.name]);
            }
        }, {
            key: "showSettingsDialog",
            value: function showSettingsDialog(settings) {
                var _this3 = this;

                var message = $("<div></div>");
                settings.element = message;
                settings.render();
                this.delegateEvents();

                BootstrapDialog.show({
                    title: i18n.t(settings.translatePath + ".dialogTitle"),
                    message: message,
                    cssClass: "filter-dialog",
                    animate: false,
                    closable: false,
                    size: BootstrapDialog.SIZE_WIDE,
                    buttons: [{
                        label: i18n.t("common.form.cancel"),
                        action: function action(dialog) {
                            if (!settings.isFilterEnabled()) {
                                _this3.setFilterOption(_this3.prevFilterName);
                            }
                            dialog.close();
                        }
                    }, {
                        label: i18n.t("common.form.save"),
                        cssClass: "btn-primary",
                        action: function action(dialog) {
                            settings.toggleFilter(true);
                            settings.save().then(function () {
                                _this3.refreshOptions();
                                dialog.close();
                            });
                        }
                    }]
                });
            }
        }, {
            key: "template",
            get: function get() {
                return "templates/openig/admin/routes/parts/Authentication.html";
            }
        }, {
            key: "partials",
            get: function get() {
                return ["templates/openig/admin/common/form/SliderControl.html", "templates/openig/admin/routes/components/AuthRadioItem.html"];
            }
        }, {
            key: "events",
            get: function get() {
                return {
                    "click #enableAuthentication": "enableAuthenticationClick",
                    "click .js-settings-btn": "settingsClick",
                    "click input[type='radio']": "radioClick"
                };
            }
        }]);

        return Authentication;
    }(AbstractRouteView);
});
//# sourceMappingURL=Authentication.js.map
