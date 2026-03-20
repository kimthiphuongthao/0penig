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

define(["jquery", "lodash", "form2js", "i18next", "org/forgerock/openig/ui/admin/routes/AbstractRouteView"], function ($, _, form2js, i18n, AbstractRouteView) {
    return function (_AbstractRouteView) {
        _inherits(Overview, _AbstractRouteView);

        function Overview(options) {
            _classCallCheck(this, Overview);

            var _this = _possibleConstructorReturn(this, (Overview.__proto__ || Object.getPrototypeOf(Overview)).call(this));

            _this.element = ".main";
            _this.data = options.parentData;
            _this.data.routeId = _this.data.routeData.get("id");
            _this.data.title = _this.data.routeData.get("name");
            _this.data.baseURI = _this.data.routeData.get("baseURI");
            _this.data.condition = _this.getConditionValue(_this.data.routeData);
            _this.data.overviewItems = [{
                title: i18n.t("config.AppConfiguration.Navigation.routeSideMenu.capture"),
                route: "routeCapture",
                icon: "fa-search"
            }, {
                title: i18n.t("config.AppConfiguration.Navigation.routeSideMenu.throttling"),
                route: "routeThrottling",
                icon: "fa-filter"
            }, {
                title: i18n.t("config.AppConfiguration.Navigation.routeSideMenu.authentication"),
                route: "routeAuthentication",
                icon: "fa-user"
            }, {
                title: i18n.t("config.AppConfiguration.Navigation.routeSideMenu.authorization"),
                route: "routeAuthorization",
                icon: "fa-key"
            }, {
                title: i18n.t("config.AppConfiguration.Navigation.routeSideMenu.statistics"),
                route: "routeStatistics",
                icon: "fa-line-chart"
            }];
            return _this;
        }

        _createClass(Overview, [{
            key: "getConditionValue",
            value: function getConditionValue(route) {
                var condition = route.get("condition");
                if (!condition || !condition.type) {
                    return "";
                }
                return condition[condition.type];
            }
        }, {
            key: "render",
            value: function render() {
                var _this2 = this;

                _.forEach(this.data.overviewItems, function (item) {
                    item.routeId = _this2.data.routeId;
                    item.status = _this2.getStatus(item.route);
                });

                this.parentRender();
            }
        }, {
            key: "getStatus",
            value: function getStatus(route) {
                var filters = this.data.routeData.get("filters");
                var status = i18n.t("templates.routes.filters.Off");
                var filter = void 0;
                switch (route) {
                    case "routeCapture":
                        var captureStatus = this.getCaptureStatus();
                        if (captureStatus !== "") {
                            status = captureStatus;
                        }
                        break;
                    case "routeThrottling":
                        filter = _.find(filters, {
                            "type": "ThrottlingFilter",
                            "enabled": true
                        });
                        if (filter) {
                            status = i18n.t("templates.routes.filters.ThrottlingFilter", {
                                numberOfRequests: filter.numberOfRequests,
                                duration: filter.durationValue,
                                durationRange: i18n.t("common.timeSlot." + filter.durationRange)
                            });
                        }
                        break;
                    case "routeAuthentication":
                        filter = _.find(filters, {
                            "type": "OAuth2ClientFilter",
                            "enabled": true
                        });
                        if (filter) {
                            status = i18n.t("templates.routes.filters.OAuth2ClientFilter");
                        }
                        filter = _.find(filters, {
                            "type": "SingleSignOnFilter",
                            "enabled": true
                        });
                        if (filter) {
                            status = i18n.t("templates.routes.filters.SingleSignOnFilter");
                        }
                        break;
                    case "routeAuthorization":
                        filter = _.find(filters, {
                            "type": "PolicyEnforcementFilter",
                            "enabled": true
                        });
                        if (filter) {
                            status = i18n.t("templates.routes.filters.PolicyEnforcementFilter");
                        }
                        break;
                    case "routeStatistics":
                        var statistics = this.data.routeData.get("statistics");
                        if (_.get(statistics, "enabled")) {
                            status = i18n.t("templates.routes.parts.statistics.fields.status");
                        }
                }
                return status;
            }
        }, {
            key: "getCaptureStatus",
            value: function getCaptureStatus() {
                var capture = this.data.routeData.get("capture");
                var entity = void 0;
                if (_.get(capture, "entity")) {
                    entity = i18n.t("templates.routes.capture.entity");
                }

                var inbound = void 0;
                if (_.get(capture, "inbound.request") && _.get(capture, "inbound.response")) {
                    inbound = i18n.t("templates.routes.capture.inboundMessages");
                } else if (_.get(capture, "inbound.request")) {
                    inbound = i18n.t("templates.routes.capture.inboundRequests");
                } else if (_.get(capture, "inbound.response")) {
                    inbound = i18n.t("templates.routes.capture.inboundResponses");
                }

                var outbound = void 0;
                if (_.get(capture, "outbound.request") && _.get(capture, "outbound.response")) {
                    outbound = i18n.t("templates.routes.capture.outboundMessages");
                } else if (_.get(capture, "outbound.request")) {
                    outbound = i18n.t("templates.routes.capture.outboundRequests");
                } else if (_.get(capture, "outbound.response")) {
                    outbound = i18n.t("templates.routes.capture.outboundResponses");
                }

                var messageArr = [entity, inbound, outbound];
                var messages = "";
                _.forEach(messageArr, function (value) {
                    if (value) {
                        if (messages === "") {
                            messages += value;
                        } else {
                            messages += ",  " + value;
                        }
                    }
                });

                return messages;
            }
        }, {
            key: "template",
            get: function get() {
                return "templates/openig/admin/routes/parts/Overview.html";
            }
        }, {
            key: "partials",
            get: function get() {
                return ["templates/openig/admin/routes/components/OverviewItem.html"];
            }
        }]);

        return Overview;
    }(AbstractRouteView);
});
//# sourceMappingURL=Overview.js.map
