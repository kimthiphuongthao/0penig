"use strict";

var _createClass = function () { function defineProperties(target, props) { for (var i = 0; i < props.length; i++) { var descriptor = props[i]; descriptor.enumerable = descriptor.enumerable || false; descriptor.configurable = true; if ("value" in descriptor) descriptor.writable = true; Object.defineProperty(target, descriptor.key, descriptor); } } return function (Constructor, protoProps, staticProps) { if (protoProps) defineProperties(Constructor.prototype, protoProps); if (staticProps) defineProperties(Constructor, staticProps); return Constructor; }; }();

function _classCallCheck(instance, Constructor) { if (!(instance instanceof Constructor)) { throw new TypeError("Cannot call a class as a function"); } }

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

define(["lodash"], function (_) {
    return function () {
        // Set text or conditions filter is looking for
        // value could contains plain text and/or key with value
        // ie.: "plain text key: value"
        function DataFilter(value, searchableParams) {
            _classCallCheck(this, DataFilter);

            if (!value && value.length === 0) {
                this.searchConditions = {};
                return;
            }
            if (searchableParams) {
                this.searchable = searchableParams;
            }
            this.searchConditions = DataFilter.parseSearch(value);
        }

        // Get array of object keys where to search


        _createClass(DataFilter, [{
            key: "matchRules",
            value: function matchRules(data) {
                var match = true;
                _.forOwn(this.searchConditions, function (searchValue, searchKey) {
                    if (searchKey === "*") {
                        // simple text match when any attribute contains text
                        if (searchValue.length > 0) {
                            _.each(searchValue, function (oneSearchValue) {
                                match = match && _.toArray(data).filter(function (value) {
                                    return DataFilter.containsFilter(value, oneSearchValue);
                                }).length > 0;
                            });
                        }
                    } else {
                        // attribute match when attribute's value start with text
                        _.each(searchValue, function (oneSearchValue) {
                            match = match && DataFilter.startWithFilter(data[searchKey], oneSearchValue);
                        });
                    }
                });
                return match;
            }

            // Filter compare data object with search conditions

        }, {
            key: "filter",
            value: function filter(data) {
                if (!this.searchConditions) {
                    return true;
                }
                if (this.searchable) {
                    data = _.pick(data, this.searchable);
                }
                return this.matchRules(data);
            }
        }, {
            key: "searchableParams",
            get: function get() {
                return this.searchable;
            }

            // Set array of object keys where to search
            ,
            set: function set(value) {
                this.searchable = value;
            }

            // Parse text into search conditions

        }], [{
            key: "parseSearch",
            value: function parseSearch(text) {
                text = text.toLowerCase();
                var search = {
                    "*": []
                };

                var regex = /(\b(?!http|https\b)\w+:)| /g;
                var lastKey = ["*:"];
                var lastValStart = 0;

                var addValue = function addValue(newValue) {
                    var newKey = lastKey[0].slice(0, lastKey[0].length - 1);
                    newValue = newValue.trim();
                    if (!newValue || newValue === "") {
                        return;
                    }
                    if (newKey === "" || newKey === "*") {
                        search["*"].push(newValue);
                    } else {
                        var existingValue = search[newKey];
                        if (!existingValue) {
                            search[newKey] = [newValue];
                        } else {
                            search[newKey].push(newValue);
                        }
                    }
                };

                var key = regex.exec(text);
                while (key !== null) {
                    if (key.index === regex.lastIndex) {
                        regex.lastIndex++;
                    }
                    addValue(text.slice(lastValStart, key.index));

                    lastKey = key;
                    lastValStart = regex.lastIndex;
                    key = regex.exec(text);
                }
                addValue(text.slice(lastValStart));
                return search;
            }
        }, {
            key: "isValue",
            value: function isValue(value) {
                return _.isNumber(value) || _.isString(value) || _.isDate(value) || _.isBoolean(value);
            }
        }, {
            key: "containsFilter",
            value: function containsFilter(value, filter) {
                return DataFilter.isValue(value) && value.toString().toLowerCase().indexOf(filter) >= 0;
            }
        }, {
            key: "startWithFilter",
            value: function startWithFilter(value, filter) {
                return DataFilter.isValue(value) && value && value.toString().toLowerCase().indexOf(filter) === 0;
            }
        }]);

        return DataFilter;
    }();
});
//# sourceMappingURL=DataFilter.js.map
