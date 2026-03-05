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

define(["jquery", "lodash", "backbone", "org/forgerock/openig/ui/admin/services/ServerUrls", "org/forgerock/openig/ui/admin/services/ServerInfoService"], function ($, _, Backbone, serverUrls, server) {
    return (
        /* Define Route structure + add defaults, constants, orders */
        function (_Backbone$Model) {
            _inherits(RouteModel, _Backbone$Model);

            function RouteModel(options) {
                _classCallCheck(this, RouteModel);

                var _this = _possibleConstructorReturn(this, (RouteModel.__proto__ || Object.getPrototypeOf(RouteModel)).call(this, options));

                _this.url = serverUrls.systemObjectsPath + "/ui/record";
                return _this;
            }

            _createClass(RouteModel, [{
                key: "validate",
                value: function validate(attrs) {
                    if (!attrs.id || attrs.id.trim() === "") {
                        return "routeErrorNoId";
                    }

                    if (!attrs.name || attrs.name.trim() === "") {
                        return "routeErrorNoName";
                    }

                    if (!attrs.baseURI || attrs.baseURI.trim() === "") {
                        return "routeErrorNoUrl";
                    }
                }
            }, {
                key: "getMVCCRev",
                value: function getMVCCRev() {
                    return this.get("_rev") || "*";
                }
            }, {
                key: "save",
                value: function save(attr, options) {
                    this.setPendingChanges();
                    return Backbone.Model.prototype.save.call(this, attr, options);
                }
            }, {
                key: "sync",
                value: function sync(method, model, options) {
                    options = options || {};

                    options.headers = {};
                    if (method !== "create") {
                        options.url = this.url + "/" + model.id;
                        options.headers = { "If-Match": model.getMVCCRev() };
                    }

                    options.headers["Cache-Control"] = "no-cache";
                    options.dataType = "json";
                    options.contentType = "application/json";

                    return Backbone.Model.prototype.sync.call(this, method, model, options);
                }
            }, {
                key: "getStatusTextKey",
                value: function getStatusTextKey() {
                    if (this.get("deployed") === true) {
                        if (this.get("pendingChanges") === true) {
                            return "templates.routes.changesPending";
                        } else {
                            return "templates.routes.deployedState";
                        }
                    } else {
                        return "templates.routes.undeployedState";
                    }
                }
            }, {
                key: "setPendingChanges",
                value: function setPendingChanges() {
                    if (this.needUnsetPendingChanges()) {
                        // model state changed to undeployed and has pending changes flag
                        this.set("pendingChanges", false);
                        console.log("No pending changes", this.id);
                    } else if (this.needSetPendingChanges()) {
                        // deployed model changed; ignore pendingChanges and deployedDate changes
                        this.set("pendingChanges", true);
                        console.log("Has pending changes", this.id);
                    }
                }
            }, {
                key: "needUnsetPendingChanges",
                value: function needUnsetPendingChanges() {
                    return this.hasChanged("deployed") && this.get("deployed") && this.get("pendingChanges");
                }
            }, {
                key: "needSetPendingChanges",
                value: function needSetPendingChanges() {
                    return !this.hasChanged("pendingChanges") && !this.hasChanged("deployedDate") && this.get("deployed") && !this.get("pendingChanges");
                }
            }, {
                key: "getFilter",
                value: function getFilter(condition) {
                    return _.find(this.get("filters"), condition);
                }
            }, {
                key: "setFilter",
                value: function setFilter(filter, condition) {
                    var filters = this.get("filters");
                    var filterIndex = _.findIndex(filters, condition);
                    filters[filterIndex] = filter;
                    this.set("filters", filters);
                    var hasChanged = !_.isEqual(this.get("filters"), this.previousAttributes().filters);
                    if (hasChanged && this.needSetPendingChanges()) {
                        this.set("pendingChanges", true);
                        console.log("Has pending changes (filters)", this.id);
                    }
                }
            }, {
                key: "idAttribute",
                get: function get() {
                    return "_id";
                }
            }, {
                key: "defaults",
                get: function get() {
                    return {
                        id: "",
                        name: "",
                        baseURI: "",
                        deployedDate: undefined,
                        pendingChanges: false,
                        filters: []
                    };
                }

                /**
                 * Builds a new RouteModel that will be enriched with server version information.
                 * @param {Object} options default values
                 * @returns {Promise.<RouteModel>} a promise of a RouteModel
                 */

            }], [{
                key: "newRouteModel",
                value: function newRouteModel(options) {
                    return server.getInfo().then(function (info) {
                        var model = new RouteModel(options);
                        if (info) {
                            model.set("version", info.version);
                        }
                        return model;
                    });
                }
            }]);

            return RouteModel;
        }(Backbone.Model)
    );
});
//# sourceMappingURL=RouteModel.js.map
