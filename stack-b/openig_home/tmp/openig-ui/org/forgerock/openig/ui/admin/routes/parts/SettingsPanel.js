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

define(["jquery", "lodash", "form2js", "i18next", "org/forgerock/commons/ui/common/main/AbstractView", "org/forgerock/commons/ui/common/main/ValidatorsManager", "org/forgerock/openig/ui/admin/util/RoutesUtils", "org/forgerock/openig/ui/admin/util/FormUtils", "org/forgerock/openig/ui/admin/services/TransformService"], function ($, _, form2js, i18n, AbstractView, validatorsManager, RoutesUtils, FormUtils, transformService) {
    return function (_AbstractView) {
        _inherits(SettingsPanel, _AbstractView);

        function SettingsPanel() {
            _classCallCheck(this, SettingsPanel);

            var _this = _possibleConstructorReturn(this, (SettingsPanel.__proto__ || Object.getPrototypeOf(SettingsPanel)).call(this));

            _this.element = "#settingsPanel";
            _this.manualIdChange = false;
            _this.translationPath = "templates.routes.parts.settings.fields";
            _this.conditionType = { PATH: "path", EXPRESSION: "expression" };
            _this.condition = { type: _this.conditionType.PATH };
            return _this;
        }

        _createClass(SettingsPanel, [{
            key: "setup",
            value: function setup(options) {
                this.routeData = options.route;
                this.isEdit = this.routeData.has("_id");
            }
        }, {
            key: "setupForm",
            value: function setupForm(data) {
                var _this2 = this;

                _.forOwn(data, function (value, key) {
                    if (key === "condition") {
                        _this2.condition = value;
                        _this2.setSelected(value.type);
                    } else {
                        _this2.$el.find("input[name='" + key + "']").val(value);
                    }
                });
            }
        }, {
            key: "render",
            value: function render() {
                var _this3 = this;

                this.condition = _.extend(this.condition, this.routeData.get("condition"));
                this.conditionOptions = this.getDropdownItems(this.conditionType);
                this.data.controls = [{
                    name: "baseURI",
                    value: this.routeData.get("baseURI"),
                    validator: "required baseURI spaceCheck"
                }, {
                    name: "condition",
                    controlType: "dropdownedit",
                    dropdownName: "conditionType",
                    selectedOption: this.translationPath + "." + this.condition.type,
                    value: this.condition[this.condition.type],
                    options: this.conditionOptions
                }, {
                    name: "name",
                    value: this.routeData.get("name"),
                    validator: "required spaceCheck customValidator"
                }, {
                    name: "id",
                    value: this.routeData.get("id"),
                    validator: "required spaceCheck urlCompatible customValidator",
                    disabled: this.isEdit
                }];
                FormUtils.extendControlsSettings(this.data.controls, {
                    autoTitle: true,
                    autoHint: true,
                    translatePath: this.translationPath,
                    defaultControlType: "edit"
                });
                FormUtils.fillPartialsByControlType(this.data.controls);
                this.parentRender(function () {
                    validatorsManager.bindValidators(_this3.$el);
                });
            }
        }, {
            key: "getDropdownItems",
            value: function getDropdownItems(options) {
                var _this4 = this;

                return _.sortByOrder(_.map(options, function (name) {
                    return {
                        name: name,
                        title: i18n.t(_this4.translationPath + "." + name)
                    };
                }), "title", ["asc"]);
            }
        }, {
            key: "setCondition",
            value: function setCondition(type, value) {
                if (!type) {
                    type = this.condition.type;
                }
                this.condition[type] = value;
            }
        }, {
            key: "setSelected",
            value: function setSelected(option) {
                var conditionField = this.$el.find("[name='condition']");
                var currentOption = this.condition.type;

                if (option !== currentOption) {
                    // Store previous value
                    this.setCondition(currentOption, conditionField.val());
                    this.condition.type = option;
                    this.convertCondition(currentOption, option, conditionField.val());
                }

                // Set new value
                conditionField.val(this.condition[option]);

                // Update dropdown button title
                var button = this.$el.find("[name='conditionButton']")[0];
                $(button).html(this.getConditionOption(option).title).append(" <span class='caret'></span>");
            }
        }, {
            key: "onConditionButtonClick",
            value: function onConditionButtonClick(event) {
                event.preventDefault();
                this.setSelected(event.target.name);
            }
        }, {
            key: "convertCondition",
            value: function convertCondition(fromType, toType) {
                if (this.getConditionOption(toType).isChanged && !_.isEmpty(this.condition[toType])) {

                    // Never change values edited by user, except when it's empty
                    return;
                }
                if (fromType === this.conditionType.PATH && toType === this.conditionType.EXPRESSION) {
                    this.condition[toType] = transformService.generateCondition(this.condition[fromType]);
                    this.getConditionOption(toType).isChanged = false;
                }
            }
        }, {
            key: "onConditionChange",
            value: function onConditionChange(event) {
                event.preventDefault();

                // Update condition value on user event
                this.condition[this.condition.type] = event.target.value;
                this.getConditionOption(this.condition.type).isChanged = true;
            }
        }, {
            key: "getConditionOption",
            value: function getConditionOption(type) {
                return _.find(this.conditionOptions, { name: type });
            }
        }, {
            key: "save",
            value: function save() {
                var _this5 = this;

                var form = this.$el[0];
                return FormUtils.isFormValid(form).then(function () {
                    _this5.routeData.set(_this5.getFormData(form));
                    return _this5.routeData.save();
                }, function () {
                    $(form).find("input").trigger("validate");
                });
            }
        }, {
            key: "getFormData",
            value: function getFormData(form) {
                var formVal = form2js(form, ".", false, FormUtils.convertToJSTypes);

                // update 'condition' value to keep all values
                formVal.condition = this.condition;
                return formVal;
            }
        }, {
            key: "validate",
            value: function validate() {
                var form = this.$el.closest("form");
                return FormUtils.isFormValid(form).then(function () {
                    $(form).find("input").trigger("validate");
                });
            }
        }, {
            key: "generateId",
            value: function generateId(evt) {
                // Avoid re-generate on tab, after manual change or at edit page
                if (evt.keyCode === 9 || this.manualIdChange || this.isEdit) {
                    return;
                }
                this.$el.find("[name='id']").val(RoutesUtils.generateRouteId(evt.target.value));
            }
        }, {
            key: "validateId",
            value: function validateId(evt) {
                var deferred = $.Deferred();
                var target = this.$el.find("[name='id']")[0];
                if (this.routeData.get("id") !== target.value) {
                    RoutesUtils.isRouteIdUniq(target.value).then(function (isValid) {
                        $(target).data("custom-valid-msg", isValid ? "" : "templates.routes.duplicateIdError");
                        $(target).trigger("validate");
                        deferred.resolve(isValid);
                    });
                }
                if (evt && evt.keyCode !== 9) {
                    this.manualIdChange = true;
                }
                return deferred;
            }
        }, {
            key: "validateName",
            value: function validateName() {
                var target = this.$el.find("[name='name']")[0];
                if (this.routeData.get("name") !== target.value) {
                    RoutesUtils.checkName(target.value).then(function (checkResult) {
                        $(target).data("custom-valid-msg", checkResult || "");
                        $(target).trigger("validate");
                    });
                }
            }
        }, {
            key: "template",
            get: function get() {
                return "templates/openig/admin/routes/parts/SettingsPanel.html";
            }
        }, {
            key: "partials",
            get: function get() {
                return ["templates/openig/admin/common/form/EditControl.html", "templates/openig/admin/common/form/DropdownEditControl.html"];
            }
        }, {
            key: "events",
            get: function get() {
                return {
                    "blur input[name='name']": "validateName",
                    "blur input[name='condition']": "onConditionChange",
                    "keyup input[name='name']": "generateId",
                    "keyup input[name='id']": "validateId",
                    "click .condition-link-btn": "switchClick",
                    "click ul[name='conditionType']": "onConditionButtonClick",
                    "validationSuccessful": "validationSuccessful",
                    "validationFailed": "validationFailed"
                };
            }
        }]);

        return SettingsPanel;
    }(AbstractView);
});
//# sourceMappingURL=SettingsPanel.js.map
