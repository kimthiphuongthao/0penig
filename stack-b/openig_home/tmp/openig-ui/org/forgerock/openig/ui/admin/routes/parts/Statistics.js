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

define(["jquery", "lodash", "form2js", "i18next", "selectize", "org/forgerock/openig/ui/admin/util/FormUtils", "org/forgerock/openig/ui/admin/util/RoutesUtils", "org/forgerock/openig/ui/admin/util/ValueHelper", "org/forgerock/commons/ui/common/main/EventManager", "org/forgerock/commons/ui/common/util/Constants", "org/forgerock/openig/ui/admin/routes/AbstractRouteView"], function ($, _, form2js, i18n, selectize, FormUtils, RoutesUtils, ValueHelper, EventManager, Constants, AbstractRouteView) {
    return AbstractRouteView.extend({
        template: "templates/openig/admin/routes/parts/Statistics.html",
        partials: ["templates/openig/admin/common/form/SliderControl.html", "templates/openig/admin/common/form/GroupControl.html", "templates/openig/admin/common/form/MultiSelectControl.html", "templates/openig/admin/routes/components/FormFooter.html"],
        events: {
            "click .js-reset-btn": "resetClick",
            "click .js-save-btn": "saveClick",
            "click input[name='enabled']": "enableStatistics",
            "change input[name='percentiles']": "percentilesChange"
        },
        data: {
            formId: "statistics-form"
        },
        initialize: function initialize(options) {
            this.data = _.extend(this.data, options.parentData);
            this.settingTitle = i18n.t("templates.routes.parts.statistics.title");
        },

        spaceDelimiter: " ",
        render: function render() {
            var _this = this;

            var statistics = this.getStatistics();
            var defaultValues = [0.999.toLocaleString(undefined, { minimumFractionDigits: 3 }), 0.9999.toLocaleString(undefined, { minimumFractionDigits: 4 }), 0.99999.toLocaleString(undefined, { minimumFractionDigits: 5 })].join(this.spaceDelimiter);

            this.data.controls = [{
                name: "enabled",
                value: statistics.enabled,
                controlType: "slider",
                hint: false
            }, {
                name: "statisticsGroup",
                title: "",
                controlType: "group",
                cssClass: this.getStatistics().enabled ? "collapse in" : "collapse",
                controls: [{
                    name: "percentiles",
                    value: statistics.percentiles,
                    controlType: "multiselect",
                    delimiter: this.spaceDelimiter,
                    options: defaultValues,
                    placeholder: defaultValues
                }]
            }];
            FormUtils.extendControlsSettings(this.data.controls, {
                autoTitle: true,
                autoHint: true,
                translatePath: "templates.routes.parts.statistics.fields",
                defaultControlType: "edit"
            });
            FormUtils.fillPartialsByControlType(this.data.controls);
            this.parentRender(function () {
                _this.setFormFooterVisibility(_this.getStatistics().enabled);
                _.forEach(_this.$el.find(".multi-select-control"), function (control) {
                    var multiselect = FormUtils.initializeMultiSelect(control);
                    multiselect[0].selectize.on("item_add", function (value) {
                        _this.onItemAdd(value, multiselect);
                    });
                });
            });
        },
        enableStatistics: function enableStatistics(event) {
            var _this2 = this;

            var newState = event.currentTarget.checked;
            var collapseState = newState ? "show" : "hide";
            this.$el.find("div[name='statisticsGroup']").collapse(collapseState);

            var statistics = this.getStatistics();
            statistics.enabled = newState;
            this.data.routeData.set("statistics", statistics);

            this.data.routeData.save().then(function () {
                if (!newState) {
                    _this2.showNotification(_this2.NOTIFICATION_TYPE.Disabled);
                }
            }, function () {
                _this2.showNotification(_this2.NOTIFICATION_TYPE.SaveFailed);
            });

            this.setFormFooterVisibility(newState);
        },
        onItemAdd: function onItemAdd(value, control) {
            var selectize = control[0].selectize;
            var removeItem = function removeItem() {
                selectize.removeOption(value);
                selectize.removeItem(value);
            };
            var decimalSeparator = ValueHelper.getDecimalSeparator();
            var float = ValueHelper.toNumber(value);
            var splitNumber = float.toString().split(".");
            // Build number in appropriate localized format.
            // If the number has both, integer and fractional part the fixed consists
            // from '0', separator and 'fractional part';
            // if the number has no fractional part then the fixed consists from '0', 'separator' and 'integer part'
            var fixed = "0" + decimalSeparator + _.last(splitNumber);

            if (!_.isFinite(float)) {
                // in case the value is not a number
                removeItem();
            } else if (!_.isEqual(value, fixed)) {
                // in case the entered value differs from calculated
                removeItem();
                selectize.addOption({ value: fixed, text: fixed });
                selectize.addItem(fixed, true);
            }
        },
        saveClick: function saveClick(event) {
            var _this3 = this;

            event.preventDefault();
            var form = this.$el.find("#" + this.data.formId)[0];
            this.data.routeData.set("statistics", this.formToStatistics(form));
            this.data.routeData.save().then(function () {
                var submit = _this3.$el.find(".js-save-btn");
                submit.attr("disabled", true);
                _this3.showNotification(_this3.NOTIFICATION_TYPE.SaveSuccess);
            }, function () {
                _this3.showNotification(_this3.NOTIFICATION_TYPE.SaveFailed);
            });
            this.isDataDirty();
        },
        percentilesChange: function percentilesChange() {
            this.isDataDirty();
        },


        // Check entered data against saved
        isDataDirty: function isDataDirty() {
            var savedVal = _.get(this.data.routeData.get("statistics"), "percentiles");
            var form = this.$el.find("#" + this.data.formId)[0];
            var formVal = this.formToStatistics(form).percentiles;
            var submit = this.$el.find(".js-save-btn");

            submit.attr("disabled", _.isEqual(_.sortBy(savedVal.split(this.spaceDelimiter)), _.sortBy(formVal.split(this.spaceDelimiter))));
        },
        formToStatistics: function formToStatistics(form) {
            var formVal = form2js(form, ".", false, FormUtils.convertToJSTypes);
            return {
                enabled: formVal.enabled,
                percentiles: formVal.percentiles
            };
        },
        getStatistics: function getStatistics() {
            var statistics = this.data.routeData.get("statistics");
            if (!statistics) {
                statistics = this.defaultStatistics();
            }
            return statistics;
        },
        defaultStatistics: function defaultStatistics() {
            return {
                enabled: false,
                percentiles: ""
            };
        }
    });
});
//# sourceMappingURL=Statistics.js.map
