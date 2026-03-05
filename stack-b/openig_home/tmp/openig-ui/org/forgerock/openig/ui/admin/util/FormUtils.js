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

define(["jquery", "lodash", "i18next", "org/forgerock/commons/ui/common/main/ValidatorsManager"], function ($, _, i18n, validatorsManager) {
    return {
        extendControlsSettings: function extendControlsSettings(controls, options) {
            var _this = this;

            if (!options.defaultValidatorEvent) {
                options.defaultValidatorEvent = "keyup blur";
            }
            _.forEach(controls, function (c) {
                if (options.autoTitle && options.autoTitle === true && c.title === undefined) {
                    c.title = options.translatePath + "." + c.name;
                }
                if (options.autoHint && options.autoHint === true && c.hint === undefined) {
                    c.hint = options.translatePath + "." + c.name + "Hint";
                }
                if (options.autoPlaceHolder && options.autoPlaceHolder === true && c.placeholder === undefined) {
                    c.placeholder = options.translatePath + "." + c.name + "PlaceHolder";
                }
                if (options.defaultControlType && c.controlType === undefined) {
                    c.controlType = options.defaultControlType;
                }
                if (c.validatorEvent === undefined) {
                    c.validatorEvent = options.defaultValidatorEvent;
                }

                if (c.controlType) {
                    _this.extendControlsSettings(c.controls, options);
                }
            });
        },
        fillPartialsByControlType: function fillPartialsByControlType(controls) {
            var self = this;
            _.forEach(controls, function (c) {
                // 'control()' function is used for dynamically select the partial to be executed within handelbars template
                c.control = function () {
                    switch (this.controlType) {
                        case "slider":
                            return "templates/openig/admin/common/form/SliderControl";
                        case "edit":
                            return "templates/openig/admin/common/form/EditControl";
                        case "multiselect":
                            return "templates/openig/admin/common/form/MultiSelectControl";
                        case "dropdownedit":
                            return "templates/openig/admin/common/form/DropdownEditControl";
                        case "group":
                            self.fillPartialsByControlType(c.controls);
                            return "templates/openig/admin/common/form/GroupControl";
                        default:
                            return this.template;
                    }
                };
            });
        },
        initializeMultiSelect: function initializeMultiSelect(control) {
            var input = $(control);
            var delimiter = input.data("delimiter");
            var tags = input.val().split(delimiter);
            var mandatory = input.data("mandatory");
            var predefinedOptions = _.map(input.data("options").split(delimiter), function (option) {
                return {
                    value: option,
                    text: option
                };
            });

            return input.selectize({
                delimiter: delimiter,
                persist: true,
                sortField: "text",
                options: predefinedOptions,
                create: function create(input) {
                    return {
                        value: input,
                        text: input
                    };
                },
                onInitialize: function onInitialize() {
                    var _this2 = this;

                    if (tags) {
                        _.forEach(tags, function (tag) {
                            _this2.addOption({ value: tag, text: tag });
                        });
                        var defaultValues = tags;
                        defaultValues.push(this.getValue().split(/\s* \s*/));
                        defaultValues.push(mandatory);
                        this.setValue(_(defaultValues).flattenDeep().uniq().value());
                    }
                },
                onItemRemove: function onItemRemove(value) {
                    if (mandatory === value) {
                        this.addItem(value);
                    }
                }
            });
        },
        isFormValid: function isFormValid(form) {
            var _this3 = this;

            var deferred = $.Deferred();
            var validatorResults = [];
            _.forEach($(form).find("input"), function (control) {
                var input = $(control);
                var valRes = _this3.evaluateAllValidatorsForField(input, form);
                validatorResults.push(valRes);
            });
            $.when.apply($, validatorResults).then(function () {
                for (var _len = arguments.length, args = Array(_len), _key = 0; _key < _len; _key++) {
                    args[_key] = arguments[_key];
                }

                if ($.inArray(false, args) === -1) {
                    deferred.resolve();
                } else {
                    deferred.reject();
                }
            });
            return deferred;
        },
        evaluateAllValidatorsForField: function evaluateAllValidatorsForField(element, container) {
            var validatorsRegistered = element.attr("data-validator");
            var deferred = $.Deferred();
            if (validatorsRegistered) {
                // wait for all promises to be resolved from the various valiators named on the element
                return $.when.apply($, _.map(validatorsRegistered.split(" "), function (validatorName) {
                    return validatorsManager.evaluateValidator(validatorName, element, container);
                })).then(function () {
                    for (var _len2 = arguments.length, args = Array(_len2), _key2 = 0; _key2 < _len2; _key2++) {
                        args[_key2] = arguments[_key2];
                    }

                    var allFailures = _(args).toArray().flatten().filter(function (value) {
                        return value !== undefined;
                    }).uniq().value();

                    if (allFailures.length) {
                        // Failed
                        return $.Deferred().resolve(false);
                    } else {
                        //Succeeded
                        return $.Deferred().resolve(true);
                    }
                });
            } else {
                // Succeded
                return deferred.resolve(true);
            }
        },


        // Callback method for form2js which convert string values into right types
        convertToJSTypes: function convertToJSTypes(element) {
            if (!element.tagName || element.tagName.toLowerCase() !== "input" || !element.type) {
                return;
            }
            switch (element.type.toLowerCase()) {
                case "number":
                    return { name: element.name, value: element.valueAsNumber };
                case "checkbox":
                    return { name: element.name, value: element.checked };
            }
        }
    };
});
//# sourceMappingURL=FormUtils.js.map
