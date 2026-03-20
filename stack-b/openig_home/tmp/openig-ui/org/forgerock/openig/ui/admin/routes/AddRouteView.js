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

define(["jquery", "lodash", "org/forgerock/openig/ui/admin/routes/AbstractRouteView", "org/forgerock/commons/ui/common/main/EventManager", "org/forgerock/commons/ui/common/util/Constants", "org/forgerock/openig/ui/admin/routes/parts/SettingsPanel", "org/forgerock/commons/ui/common/main/Router", "org/forgerock/openig/ui/admin/models/RouteModel", "org/forgerock/openig/ui/admin/models/RoutesCollection", "org/forgerock/commons/ui/common/main/ValidatorsManager"], function ($, _, AbstractRouteView, EventManager, Constants, SettingsPanel, router, RouteModel, RoutesCollection, validatorsManager) {
    var AddRouteView = function (_AbstractRouteView) {
        _inherits(AddRouteView, _AbstractRouteView);

        function AddRouteView() {
            _classCallCheck(this, AddRouteView);

            var _this = _possibleConstructorReturn(this, (AddRouteView.__proto__ || Object.getPrototypeOf(AddRouteView)).call(this));

            _this.translationPath = "templates.routes.parts.settings.fields";
            _this.settingsPanel = new SettingsPanel();
            _this.data.formId = "add-route";
            return _this;
        }

        _createClass(AddRouteView, [{
            key: "render",
            value: function render() {
                var _this2 = this;

                this.isChanged = false;
                this.setupRoute().then(function (routeData) {
                    _this2.routeData = routeData;
                    _this2.data.advancedOnly = _this2.isDuplicate();
                    _this2.parentRender(function () {
                        validatorsManager.bindValidators(_this2.$el);
                        _this2.settingsPanel.setup({ route: routeData });
                        _this2.settingsPanel.render();
                    });
                });
            }
        }, {
            key: "setupRoute",
            value: function setupRoute() {
                this.data.routeId = router.getCurrentHash().match(router.currentRoute.url)[1];
                if (this.data.routeId) {
                    return RoutesCollection.byRouteId(this.data.routeId).then(function (original) {
                        var duplicate = original.clone();
                        duplicate.unset("_id");
                        return duplicate;
                    });
                } else {
                    return RouteModel.newRouteModel();
                }
            }
        }, {
            key: "fillAdvancedForm",
            value: function fillAdvancedForm() {
                var input = this.$el.find("input[name='applicationUrl']");
                this.settingsPanel.setupForm(this.getParsedData(input.val()));
            }
        }, {
            key: "fillBasicForm",
            value: function fillBasicForm() {
                var baseURI = this.$el.find("input[name='baseURI']").val();
                var condition = this.$el.find("input[name='condition']").val();
                this.$el.find("input[name='applicationUrl']").val(baseURI + condition);
            }
        }, {
            key: "openAdvanced",
            value: function openAdvanced(event) {
                event.preventDefault();
                this.fillAdvancedForm();
                this.settingsPanel.validate();
                this.swapOptions();
            }
        }, {
            key: "closeAdvanced",
            value: function closeAdvanced(event) {
                event.preventDefault();
                if ($(event.target).hasClass("disabled")) {
                    return;
                }
                this.fillBasicForm();
                this.swapOptions();
                this.toggleCreateButton(true);
            }
        }, {
            key: "toggleCreateButton",
            value: function toggleCreateButton(enable) {
                this.$el.find(".js-create-btn").attr("disabled", !enable);
            }
        }, {
            key: "swapOptions",
            value: function swapOptions() {
                this.$el.find(".basic-settings").toggleClass("hidden");
                this.$el.find(".advanced-settings").toggleClass("hidden");
            }
        }, {
            key: "conditionChanged",
            value: function conditionChanged(event) {
                var button = this.$el.find(".js-basic-btn");
                var tooltip = button.closest("div");
                var disable = event.target.name === "expression";
                button.toggleClass("disabled", disable);
                if (disable) {
                    tooltip.tooltip();
                } else {
                    tooltip.tooltip("destroy");
                }
            }
        }, {
            key: "getParsedData",
            value: function getParsedData(applUrl) {
                var match = /(http[s]?:\/\/[^/\s]+)(\/\w*)?/i.exec(applUrl);
                if (match) {
                    var path = match[2];
                    var name = path ? path.slice(1) : "";
                    return {
                        baseURI: match[1],
                        condition: this.createPathCondition(path),
                        name: name,
                        id: name
                    };
                } else {
                    return this.getDefaults(applUrl);
                }
            }
        }, {
            key: "createPathCondition",
            value: function createPathCondition(path) {
                return {
                    type: "path",
                    path: path
                };
            }
        }, {
            key: "getDefaults",
            value: function getDefaults(value) {
                return {
                    baseURI: value,
                    condition: this.createPathCondition(),
                    name: "",
                    id: ""
                };
            }
        }, {
            key: "createClick",
            value: function createClick(event) {
                var _this3 = this;

                event.preventDefault();
                var isAdvancedOpen = this.isAdvancedOpen();
                if (!isAdvancedOpen) {
                    this.fillAdvancedForm();
                }
                this.settingsPanel.validate().then(function () {
                    _this3.createRoute();
                }, function () {
                    _this3.$el.find("input").trigger("validate");
                    if (!isAdvancedOpen) {
                        _this3.swapOptions();
                    }
                });
            }
        }, {
            key: "createRoute",
            value: function createRoute() {
                this.settingsPanel.save().then(function (newRoute) {
                    RoutesCollection.add([newRoute]);
                    EventManager.sendEvent(Constants.EVENT_CHANGE_VIEW, {
                        route: router.configuration.routes.routeOverview,
                        args: [newRoute.id]
                    });
                }, function () {
                    EventManager.sendEvent(Constants.EVENT_DISPLAY_MESSAGE_REQUEST, {
                        key: "routeCreationFailed"
                    });
                });
            }
        }, {
            key: "cancelClick",
            value: function cancelClick() {
                EventManager.sendEvent(Constants.EVENT_CHANGE_VIEW, {
                    route: router.configuration.routes.listRoutesView
                });
            }
        }, {
            key: "isAdvancedOpen",
            value: function isAdvancedOpen() {
                return !this.$el.find(".advanced-settings").hasClass("hidden");
            }
        }, {
            key: "isDuplicate",
            value: function isDuplicate() {
                return router.configuration.routes.duplicateRouteView.url.test(router.getURIFragment());
            }
        }, {
            key: "element",
            get: function get() {
                return "#content";
            }
        }, {
            key: "template",
            get: function get() {
                return "templates/openig/admin/routes/AddRouteTemplate.html";
            }
        }, {
            key: "events",
            get: function get() {
                return {
                    "click .js-create-btn": "createClick",
                    "click .js-cancel-btn": "cancelClick",
                    "click .js-advanced-btn": "openAdvanced",
                    "click .js-basic-btn": "closeAdvanced",
                    "click [name='conditionType']": "conditionChanged"
                };
            }
        }]);

        return AddRouteView;
    }(AbstractRouteView);

    return new AddRouteView();
});
//# sourceMappingURL=AddRouteView.js.map
