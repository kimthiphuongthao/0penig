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

define(["jquery", "lodash", "form2js", "selectize", "i18next", "org/forgerock/openig/ui/admin/routes/AbstractRouteView", "org/forgerock/commons/ui/common/main/ValidatorsManager", "org/forgerock/openig/ui/admin/util/RoutesUtils", "org/forgerock/openig/ui/admin/util/FormUtils"], function ($, _, form2js, selectize, i18n, AbstractRouteView, validatorsManager, RoutesUtils, FormUtils) {
    return function (_AbstractRouteView) {
        _inherits(AbstractAuthenticationFilterView, _AbstractRouteView);

        function AbstractAuthenticationFilterView() {
            _classCallCheck(this, AbstractAuthenticationFilterView);

            return _possibleConstructorReturn(this, (AbstractAuthenticationFilterView.__proto__ || Object.getPrototypeOf(AbstractAuthenticationFilterView)).apply(this, arguments));
        }

        _createClass(AbstractAuthenticationFilterView, [{
            key: "initializeFilter",
            value: function initializeFilter() {
                this.data.filter = this.getFilter();
                if (!this.data.filter && this.createFilter) {
                    this.data.filter = this.createFilter();
                }
            }
        }, {
            key: "isFilterEnabled",
            value: function isFilterEnabled() {
                var filter = this.data.routeData.getFilter(this.filterCondition);
                return filter && filter.enabled;
            }
        }, {
            key: "render",
            value: function render() {
                var _this2 = this;

                if (this.prepareControls) {
                    this.prepareControls();
                }
                FormUtils.extendControlsSettings(this.data.controls, {
                    autoTitle: true,
                    autoHint: true,
                    translatePath: this.translatePath + ".fields",
                    defaultControlType: "edit"
                });
                FormUtils.fillPartialsByControlType(this.data.controls);

                this.parentRender(function () {
                    validatorsManager.bindValidators(_this2.$el);
                    _.forEach(_this2.$el.find(".multi-select-control"), function (control) {
                        FormUtils.initializeMultiSelect(control);
                    });
                });
            }
        }, {
            key: "toggleFilter",
            value: function toggleFilter(enabled) {
                this.data.filter.enabled = enabled;
                this.data.routeData.setFilter(this.data.filter, this.filterCondition);
            }
        }, {
            key: "save",
            value: function save() {
                var _this3 = this;

                var form = this.$el.find("#" + this.data.formId)[0];
                return FormUtils.isFormValid(form).done(function () {
                    var formVal = form2js(form, ".", false, FormUtils.convertToJSTypes);
                    _.extend(_this3.data.filter, formVal);
                    if (!_this3.getFilter()) {
                        RoutesUtils.addFilterIntoModel(_this3.data.routeData, _this3.data.filter);
                    }
                    _this3.data.routeData.setFilter(_this3.data.filter, _this3.filterCondition);
                    return _this3.data.routeData.save().then(function () {
                        _this3.showNotification(_this3.NOTIFICATION_TYPE.SaveSuccess);
                    }, function () {
                        _this3.showNotification(_this3.NOTIFICATION_TYPE.SaveFailed);
                    });
                }).fail(function () {
                    $(form).find("input").trigger("validate");
                });
            }
        }, {
            key: "getFilter",
            value: function getFilter() {
                return this.data.routeData.getFilter(this.filterCondition);
            }
        }, {
            key: "template",
            get: function get() {
                return "templates/openig/admin/routes/parts/AuthenticationDialog.html";
            }
        }]);

        return AbstractAuthenticationFilterView;
    }(AbstractRouteView);
});
//# sourceMappingURL=AbstractAuthenticationFilterView.js.map
