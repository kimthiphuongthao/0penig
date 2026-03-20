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

define(["jquery", "lodash", "i18next", "org/forgerock/commons/ui/common/util/UIUtils", "org/forgerock/openig/ui/admin/models/RoutesCollection", "org/forgerock/openig/ui/admin/models/ServerRoutesCollection", "org/forgerock/commons/ui/common/components/BootstrapDialog", "org/forgerock/commons/ui/common/main/EventManager", "org/forgerock/commons/ui/common/util/Constants", "org/forgerock/openig/ui/admin/services/TransformService", "org/forgerock/commons/ui/common/main/Router", "org/forgerock/openig/ui/common/util/Clipboard", "codemirror",
// codemirror's dependency for ld+json mode
"codemirror/mode/javascript/javascript"], function ($, _, i18n, UIUtils, RoutesCollection, ServerRoutesCollection, BootstrapDialog, eventManager, constants, transformService, router, Clipboard, CodeMirror) {
    return {
        generateRouteId: function generateRouteId(routeName) {
            return routeName.toLowerCase().replace(/[^\w ]+/g, "").replace(/ +/g, "-");
        },
        isRouteIdUniq: function isRouteIdUniq(routeId) {
            return RoutesCollection.byRouteId(routeId).then(function (routeModel) {
                return !routeModel;
            });
        },
        checkName: function checkName(name) {
            return RoutesCollection.availableRoutes().then(function (routes) {
                var foundRoute = routes.findWhere({ name: name });
                return foundRoute ? "templates.routes.duplicateNameError" : "";
            });
        },
        toggleValue: function toggleValue(e) {
            var toggle = this.$el.find(e.target);
            if (toggle.val() === "true") {
                toggle.val(false);
            } else {
                toggle.val(true);
            }
        },
        duplicateRouteDialog: function duplicateRouteDialog(routeId, routeTitle) {
            UIUtils.confirmDialog(i18n.t("templates.routes.duplicateDialog", { title: routeTitle }), "danger", function () {
                router.navigate("routes/duplicate/" + routeId, true);
            });
        },
        showTooltip: function showTooltip(target, options) {
            target.tooltip(_.extend({
                container: "body",
                placement: "bottom",
                trigger: "manual"
            }, options));
            target.tooltip("show");
            _.delay(function () {
                target.tooltip("hide");
            }, 1500);
        },
        showExportDialog: function showExportDialog(jsonContent) {
            var self = this;
            var buttons = [];
            if (Clipboard.isClipboardEnabled()) {
                buttons.push({
                    id: "btnOk",
                    label: i18n.t("common.modalWindow.button.copyToClipboard"),
                    cssClass: "btn-default",
                    action: function action(dialog) {
                        var copyElement = dialog.getMessage().find("#jsonExportContent")[0];
                        if (Clipboard.copyContent(copyElement)) {
                            self.showTooltip(this, {
                                title: i18n.t("common.modalWindow.message.copied")
                            });
                        } else {
                            self.showTooltip(this, {
                                title: i18n.t("common.modalWindow.message.copyFailed")
                            });
                        }
                    }
                });
            }
            buttons.push({
                label: i18n.t("common.form.cancel"),
                cssClass: "btn-primary",
                action: function action(dialog) {
                    dialog.close();
                }
            });

            var msgNode = $("<div><pre id=\"jsonExportContent\" class=\"hidden-pre\">" + jsonContent + "</pre></div>");
            var codeMirror = CodeMirror(function (elm) {
                msgNode.append(elm);
            }, {
                value: jsonContent,
                mode: "application/ld+json",
                theme: "forgerock",
                autofocus: true,
                readOnly: true
            });
            codeMirror.setSize("100%", "100%");
            BootstrapDialog.show({
                title: i18n.t("common.modalWindow.title.configExport"),
                message: msgNode,
                closable: true,
                buttons: buttons,
                onshown: function onshown() {
                    this.message.css("max-height", "calc(100vh - 212px)");
                    codeMirror.refresh();
                    this.message.css("height", this.message.find(".CodeMirror").height());
                }
            });
        },
        exportRouteConfigDialog: function exportRouteConfigDialog(id) {
            var _this = this;

            RoutesCollection.byRouteId(id).then(function (routeData) {
                if (routeData) {
                    try {
                        var jsonContent = JSON.stringify(transformService.transformRoute(routeData), null, 2);
                        _this.showExportDialog(jsonContent);
                    } catch (e) {
                        eventManager.sendEvent(constants.EVENT_DISPLAY_MESSAGE_REQUEST, {
                            key: e.errorType || "modelTransformationFailed", message: e.message
                        });
                    }
                }
            });
        },
        deployRouteModel: function deployRouteModel(model) {
            var deferred = $.Deferred();
            var promise = deferred.promise();
            var id = model.get("id");
            var title = model.get("name");
            var jsonConfig = transformService.transformRoute(model);
            ServerRoutesCollection.deploy(id, jsonConfig).done(function () {
                eventManager.sendEvent(constants.EVENT_DISPLAY_MESSAGE_REQUEST, { key: "routeDeployedSuccess", title: title });
                model.set({
                    deployed: true,
                    deployedDate: new Date(),
                    pendingChanges: false
                });
                model.save();
                deferred.resolve();
            }).fail(function (errorResponse) {
                var errorMessage = void 0;
                if (errorResponse) {
                    errorMessage = errorResponse.cause ? errorResponse.cause.message : errorResponse.statusText;
                }
                eventManager.sendEvent(constants.EVENT_DISPLAY_MESSAGE_REQUEST, { key: "routeDeployedFailed", title: title, message: errorMessage });
                deferred.reject();
            });
            return promise;
        },
        deployRouteDialog: function deployRouteDialog(id, title) {
            var _this2 = this;

            var deferred = $.Deferred();
            var promise = deferred.promise();
            RoutesCollection.byRouteId(id).then(function (routeData) {
                if (routeData) {
                    var isDeployed = ServerRoutesCollection.isDeployed(id);
                    if (!isDeployed) {
                        _this2.deployRouteModel(routeData).done(function () {
                            deferred.resolve();
                        }).fail(function () {
                            deferred.reject();
                        });
                    } else {
                        UIUtils.confirmDialog(i18n.t("templates.routes.deployDialog", { title: title }), "danger", function () {
                            _this2.deployRouteModel(routeData).done(function () {
                                deferred.resolve();
                            }).fail(function () {
                                deferred.reject();
                            });
                        });
                    }
                }
            });
            return promise;
        },
        undeployRoute: function undeployRoute(id) {
            var deferred = $.Deferred();
            ServerRoutesCollection.undeploy(id).done(function () {
                RoutesCollection.byRouteId(id).then(function (routeData) {
                    routeData.set({
                        deployed: false,
                        deployedDate: null,
                        pendingChanges: false
                    });
                    routeData.save();
                    deferred.resolve();
                });
            }).fail(function (errorResponse) {
                var errorMessage = void 0;
                if (errorResponse) {
                    errorMessage = errorResponse.cause ? errorResponse.cause.message : errorResponse.statusText;
                }
                deferred.reject(errorMessage);
            });
            return deferred;
        },
        undeployRouteDialog: function undeployRouteDialog(id, title) {
            var _this3 = this;

            var deferred = $.Deferred();
            var promise = deferred.promise();
            UIUtils.confirmDialog(i18n.t("templates.routes.undeployDialog", { title: title }), "danger", function () {
                _this3.undeployRoute(id).then(function () {
                    eventManager.sendEvent(constants.EVENT_DISPLAY_MESSAGE_REQUEST, { key: "routeUndeployedSuccess", title: title });
                    deferred.resolve();
                }, function (errorMessage) {
                    eventManager.sendEvent(constants.EVENT_DISPLAY_MESSAGE_REQUEST, { key: "routeUndeployedFailed", title: title, message: errorMessage });
                    deferred.reject();
                });
            });
            return promise;
        },
        deleteRoute: function deleteRoute(id, title) {
            var deferred = $.Deferred();
            RoutesCollection.removeByRouteId(id).then(function () {
                eventManager.sendEvent(constants.EVENT_DISPLAY_MESSAGE_REQUEST, { key: "deleteRouteSuccess", title: title });
                deferred.resolve();
            }, function () {
                eventManager.sendEvent(constants.EVENT_DISPLAY_MESSAGE_REQUEST, { key: "deleteRouteFailed", title: title });
                deferred.reject();
            });
            return deferred;
        },
        deleteRouteDialog: function deleteRouteDialog(id, title) {
            var _this4 = this;

            var deferred = $.Deferred();
            RoutesCollection.byRouteId(id).then(function (routeModel) {
                if (routeModel) {
                    var isDeployed = ServerRoutesCollection.isDeployed(id);
                    var dialogMessageKey = isDeployed ? "templates.routes.undeployAndDeleteDialog" : "templates.routes.deleteDialog";
                    UIUtils.confirmDialog(i18n.t(dialogMessageKey, { title: title }), "danger", function () {
                        if (isDeployed) {
                            _this4.undeployRoute(id).then(function () {
                                _this4.deleteRoute(id, title).then(function () {
                                    deferred.resolve();
                                }, function () {
                                    deferred.reject();
                                });
                            }, function (errorMessage) {
                                eventManager.sendEvent(constants.EVENT_DISPLAY_MESSAGE_REQUEST, { key: "routeUndeployedFailed", title: title, message: errorMessage });
                                deferred.reject();
                            });
                        } else {
                            _this4.deleteRoute(id, title).then(function () {
                                deferred.resolve();
                            }, function () {
                                deferred.reject();
                            });
                        }
                    });
                }
            });
            return deferred;
        },
        addFilterIntoModel: function addFilterIntoModel(routeModel, filter) {
            var filters = _.clone(routeModel.get("filters"));

            if (_.includes(filters, filter)) {
                return;
            }
            filters.push(filter);
            filters = _.sortBy(filters, function (f) {
                return constants.defaultFiltersOrder[_.get(f, "type", "Unknown")];
            });
            routeModel.set("filters", filters);
        }
    };
});
//# sourceMappingURL=RoutesUtils.js.map
