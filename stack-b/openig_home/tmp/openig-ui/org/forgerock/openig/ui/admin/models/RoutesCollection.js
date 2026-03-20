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

define(["jquery", "backbone", "org/forgerock/openig/ui/admin/services/ServerUrls", "org/forgerock/openig/ui/admin/models/RouteModel"], function ($, Backbone, serverUrls, RouteModel) {
    /* Get and keep Routes */
    var RoutesCollection = function (_Backbone$Collection) {
        _inherits(RoutesCollection, _Backbone$Collection);

        function RoutesCollection() {
            _classCallCheck(this, RoutesCollection);

            var _this = _possibleConstructorReturn(this, (RoutesCollection.__proto__ || Object.getPrototypeOf(RoutesCollection)).call(this));

            _this.url = serverUrls.systemObjectsPath + "/ui/record";
            _this.model = RouteModel;
            _this.routesCache = {};
            return _this;
        }

        _createClass(RoutesCollection, [{
            key: "parse",
            value: function parse(response) {
                return response.result;
            }

            // Get all routes from server and save in local cache

        }, {
            key: "availableRoutes",
            value: function availableRoutes() {
                var _this2 = this;

                var deferred = $.Deferred();
                if (this.routesCache.currentRoutes) {
                    deferred.resolve(this.routesCache.currentRoutes);
                } else {
                    this.fetch({
                        reset: true,
                        processData: false,
                        data: $.param({ _queryFilter: true })
                    }).then(function () {
                        _this2.routesCache.currentRoutes = _this2;
                        deferred.resolve(_this2);
                    }, function () {
                        deferred.reject();
                    });
                }

                return deferred;
            }

            // Find by Id, also in cache

        }, {
            key: "byRouteId",
            value: function byRouteId(id) {
                var _this3 = this;

                var deferred = $.Deferred();
                this.availableRoutes().then(function () {
                    deferred.resolve(_this3.findWhere({ id: id }));
                });

                return deferred;
            }

            // Remove also from local cache

        }, {
            key: "removeByRouteId",
            value: function removeByRouteId(id) {
                var _this4 = this;

                var deferred = $.Deferred();
                var item = this.findWhere({ id: id });
                item.destroy().then(function (model) {
                    _this4.remove(model);
                    _this4.routesCache.currentRoutes.remove(item);
                    deferred.resolve();
                }, function (error) {
                    console.log(error);
                    deferred.reject(error);
                });
                return deferred;
            }
        }, {
            key: "comparator",
            value: function comparator(item) {
                // Sort by name
                return item.get("name");
            }
        }]);

        return RoutesCollection;
    }(Backbone.Collection);

    return new RoutesCollection();
});
//# sourceMappingURL=RoutesCollection.js.map
