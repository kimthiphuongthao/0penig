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

define(["lodash", "form2js", "i18next", "org/forgerock/openig/ui/admin/routes/AbstractRouteView", "org/forgerock/openig/ui/admin/util/FormUtils"], function (_, form2js, i18n, AbstractRouteView, FormUtils) {
    return function (_AbstractRouteView) {
        _inherits(Capture, _AbstractRouteView);

        function Capture(options) {
            _classCallCheck(this, Capture);

            var _this = _possibleConstructorReturn(this, (Capture.__proto__ || Object.getPrototypeOf(Capture)).call(this));

            _this.data = _.extend({ formId: "capture-form" }, options.parentData);
            _this.settingTitle = i18n.t("templates.routes.parts.capture.title");
            return _this;
        }

        _createClass(Capture, [{
            key: "render",
            value: function render() {
                var capture = this.findCapture();

                this.data.controls = [{
                    name: "entityGroup",
                    controlType: "group",
                    controls: [{
                        name: "entity",
                        value: capture.entity,
                        controlType: "slider",
                        hint: "templates.routes.parts.capture.fields.hint"
                    }]
                }, {
                    name: "inboundGroup",
                    controlType: "group",
                    controls: [{
                        name: "inboundRequest",
                        value: capture.inbound.request,
                        controlType: "slider"
                    }, {
                        name: "inboundResponse",
                        value: capture.inbound.response,
                        controlType: "slider"
                    }]
                }, {
                    name: "outboundGroup",
                    controlType: "group",
                    controls: [{
                        name: "outboundRequest",
                        value: capture.outbound.request,
                        controlType: "slider"
                    }, {
                        name: "outboundResponse",
                        value: capture.outbound.response,
                        controlType: "slider"
                    }]
                }];
                FormUtils.extendControlsSettings(this.data.controls, {
                    autoTitle: true,
                    autoHint: false,
                    translatePath: "templates.routes.parts.capture.fields",
                    defaultControlType: "edit"
                });
                FormUtils.fillPartialsByControlType(this.data.controls);

                this.parentRender();
            }
        }, {
            key: "saveClick",
            value: function saveClick(event) {
                var _this2 = this;

                event.preventDefault();

                var form = this.$el.find("#" + this.data.formId)[0];
                var capture = this.formToCapture(form);
                if (this.isCaptureEnabled(capture)) {
                    this.data.routeData.set("capture", capture);
                } else {
                    this.data.routeData.unset("capture");
                }
                this.data.routeData.save().then(function () {
                    var submit = _this2.$el.find(".js-save-btn");
                    submit.attr("disabled", true);
                    _this2.showNotification(_this2.NOTIFICATION_TYPE.SaveSuccess);
                }, function () {
                    _this2.showNotification(_this2.NOTIFICATION_TYPE.SaveFailed);
                });
            }
        }, {
            key: "onToggleSwitch",
            value: function onToggleSwitch(event) {
                event.preventDefault();

                var form = this.$el.find("#" + this.data.formId)[0];
                var submit = this.$el.find(".js-save-btn");

                var capture = this.findCapture();
                var newCapture = this.formToCapture(form);

                // If captures are equal: disable the submit button, enable it otherwise
                submit.attr("disabled", _.isEqual(capture, newCapture));
            }
        }, {
            key: "isCaptureEnabled",
            value: function isCaptureEnabled(capture) {
                return capture.entity === true || capture.inbound.request === true || capture.inbound.response === true || capture.outbound.request === true || capture.outbound.response === true;
            }
        }, {
            key: "findCapture",
            value: function findCapture() {
                var capture = this.data.routeData.get("capture");
                if (!capture) {
                    capture = this.defaultCapture();
                }
                return capture;
            }
        }, {
            key: "defaultCapture",
            value: function defaultCapture() {
                return {
                    entity: false,
                    inbound: {
                        request: false,
                        response: false
                    },
                    outbound: {
                        request: false,
                        response: false
                    }
                };
            }
        }, {
            key: "formToCapture",
            value: function formToCapture(form) {
                var formVal = form2js(form, ".", false, FormUtils.convertToJSTypes);
                return {
                    entity: formVal.entity,
                    inbound: {
                        request: formVal.inboundRequest,
                        response: formVal.inboundResponse
                    },
                    outbound: {
                        request: formVal.outboundRequest,
                        response: formVal.outboundResponse
                    }
                };
            }
        }, {
            key: "template",
            get: function get() {
                return "templates/openig/admin/routes/parts/Capture.html";
            }
        }, {
            key: "partials",
            get: function get() {
                return ["templates/openig/admin/common/form/SliderControl.html", "templates/openig/admin/common/form/GroupControl.html", "templates/openig/admin/routes/components/FormFooter.html"];
            }
        }, {
            key: "events",
            get: function get() {
                return {
                    "click .js-reset-btn": "resetClick",
                    "click .js-save-btn": "saveClick",
                    "change .checkbox-slider input[type='checkbox']": "onToggleSwitch"
                };
            }
        }]);

        return Capture;
    }(AbstractRouteView);
});
//# sourceMappingURL=Capture.js.map
