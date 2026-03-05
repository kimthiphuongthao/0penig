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

define(["jquery", "lodash", "form2js", "selectize", "i18next", "org/forgerock/openig/ui/admin/routes/AbstractRouteView", "org/forgerock/commons/ui/common/main/ValidatorsManager", "org/forgerock/openig/ui/admin/util/RoutesUtils", "org/forgerock/openig/ui/admin/util/FormUtils"], function ($, _, form2js, selectize, i18n, AbstractRouteView, validatorsManager, RoutesUtils, FormUtils) {
    return AbstractRouteView.extend({
        template: "templates/openig/admin/routes/parts/Authorization.html",
        partials: ["templates/openig/admin/common/form/EditControl.html", "templates/openig/admin/common/form/SliderControl.html", "templates/openig/admin/common/form/GroupControl.html", "templates/openig/admin/routes/components/FormFooter.html", "templates/openig/admin/common/form/MultiSelectControl.html"],
        events: {
            "click input[name='enabled']": "enableAuthorizationClick",
            "click .js-reset-btn": "resetClick",
            "click .js-save-btn": "saveClick"
        },
        data: {
            formId: "authorization-form"
        },
        initialize: function initialize(options) {
            this.data = _.extend(this.data, options.parentData);
            this.filterCondition = { "type": "PolicyEnforcementFilter" };
            this.settingTitle = i18n.t("templates.routes.parts.authorization.title");
        },
        render: function render() {
            var _this = this;

            this.data.authZFilter = this.getFilter();
            if (!this.data.authZFilter) {
                this.data.authZFilter = this.createFilter();
            }

            this.data.controls = [{
                name: "enabled",
                value: this.data.authZFilter.enabled,
                controlType: "slider",
                hint: false
            }, {
                name: "authZGroup",
                title: "",
                controlType: "group",
                cssClass: this.data.authZFilter.enabled ? "collapse in" : "collapse",
                controls: [{
                    name: "openAMconfigurationGroup",
                    controlType: "group",
                    controls: [{
                        name: "openamUrl",
                        value: this.data.authZFilter.openamUrl,
                        validator: "uri required"
                    }, {
                        name: "pepRealm",
                        value: this.data.authZFilter.pepRealm
                    }, {
                        name: "pepUsername",
                        value: this.data.authZFilter.pepUsername,
                        validator: "required"
                    }, {
                        name: "pepPassword",
                        value: this.data.authZFilter.pepPassword,
                        validator: "required"
                    }]
                }, {
                    name: "enforcementEndpointGroup",
                    controlType: "group",
                    controls: [{
                        name: "realm",
                        value: this.data.authZFilter.realm
                    }, {
                        name: "application",
                        value: this.data.authZFilter.application
                    }, {
                        name: "ssoTokenSubject",
                        value: this.data.authZFilter.ssoTokenSubject
                    }, {
                        name: "jwtSubject",
                        value: this.data.authZFilter.jwtSubject
                    }]
                }, {
                    name: "contextualAuthGroup",
                    controlType: "group",
                    controls: [{
                        name: "headers",
                        value: this.data.authZFilter.headers,
                        controlType: "multiselect",
                        options: "User-Agent Host From Referer Via X-Forwarded-For",
                        delimiter: " "
                    }, {
                        name: "address",
                        value: this.data.authZFilter.address,
                        controlType: "slider"
                    }]
                }]
            }];
            FormUtils.extendControlsSettings(this.data.controls, {
                autoTitle: true,
                autoHint: true,
                translatePath: "templates.routes.parts.authorization.fields",
                defaultControlType: "edit"
            });
            FormUtils.fillPartialsByControlType(this.data.controls);
            this.parentRender(function () {
                _this.setFormFooterVisibility(_this.data.authZFilter.enabled);
                validatorsManager.bindValidators(_this.$el);
                _.forEach(_this.$el.find(".multi-select-control"), function (control) {
                    FormUtils.initializeMultiSelect(control);
                });
            });
        },
        enableAuthorizationClick: function enableAuthorizationClick(event) {
            var _this2 = this;

            var newState = event.currentTarget.checked;
            var collapseState = newState ? "show" : "hide";
            this.$el.find("div[name='authZGroup']").collapse(collapseState);

            if (!newState) {
                //Save Off state
                this.data.authZFilter.enabled = newState;
                this.data.routeData.setFilter(this.data.authZFilter, this.filterCondition);
                this.data.routeData.save().then(function () {
                    _this2.showNotification(_this2.NOTIFICATION_TYPE.Disabled);
                }, function () {
                    _this2.showNotification(_this2.NOTIFICATION_TYPE.SaveFailed);
                });
            } else {
                //Save On state, only when form is valid
                var form = this.$el.find("#" + this.data.formId)[0];
                FormUtils.isFormValid(form).done(function () {
                    _this2.data.authZFilter.enabled = newState;
                    _this2.data.routeData.setFilter(_this2.data.authZFilter, _this2.filterCondition);
                    _this2.data.routeData.save();
                });
            }
            this.setFormFooterVisibility(newState);
        },
        saveClick: function saveClick(event) {
            var _this3 = this;

            event.preventDefault();
            var form = this.$el.find("#" + this.data.formId)[0];
            FormUtils.isFormValid(form).done(function () {
                var formVal = form2js(form, ".", false, FormUtils.convertToJSTypes);
                _.extend(_this3.data.authZFilter, formVal);
                if (!_this3.getFilter()) {
                    RoutesUtils.addFilterIntoModel(_this3.data.routeData, _this3.data.authZFilter);
                }
                _this3.data.routeData.setFilter(_this3.data.authZFilter, _this3.filterCondition);
                _this3.data.routeData.save().then(function () {
                    var submit = _this3.$el.find(".js-save-btn");
                    submit.attr("disabled", true);
                    _this3.showNotification(_this3.NOTIFICATION_TYPE.SaveSuccess);
                }, function () {
                    _this3.showNotification(_this3.NOTIFICATION_TYPE.SaveFailed);
                });
            }).fail(function () {
                $(form).find("input").trigger("validate");
            });
        },
        getFilter: function getFilter() {
            return this.data.routeData.getFilter(this.filterCondition);
        },
        createFilter: function createFilter() {
            return {
                type: "PolicyEnforcementFilter",
                headers: "User-Agent",
                pepRealm: "/",
                realm: "/",
                application: "iPlanetAMWebAgentService"
            };
        }
    });
});
//# sourceMappingURL=Authorization.js.map
