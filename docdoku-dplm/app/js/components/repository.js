(function () {

    'use strict';

    angular.module('dplm.services')

        .factory('IndexKeys', function () {
            return {
                HASH: 'hash',
                DIGEST: 'digest',
                WORKSPACE_ID: 'workspaceId',
                NUMBER: 'number',
                DOCUMENT_MASTER_ID: 'documentMasterId',
                REVISION: 'revision',
                ITERATION: 'iteration',
                LAST_MODIFIED_DATE: 'lastModifiedDate'
            };
        })


        .service('RepositoryService', function ($q, $timeout, $window, $filter,
                                                INDEX_LOCATION, INDEX_SEARCH_PATTERN, DocdokuAPIService, FolderService, DBService, FileUtils,
                                                IndexKeys) {

            var _this = this;

            var fs = $window.require('fs');
            var crypto = $window.require('crypto');
            var glob = $window.require("glob");
            var fileShortName = $filter('fileShortName');
            var lastIteration = $filter('lastIteration');
            var utcToLocalDateTime = $filter('utcToLocalDateTime');
            var fileMode = $filter('fileMode');

            var indexes = {};

            var getIndexValue = function (index, path, key) {
                return index[path + '.' + key] || null;
            };

            var setIndexValue = function (index, path, key, value) {
                index[path + '.' + key] = value;
            };

            var getHashFromFile = function (path) {
                return crypto.createHash('MD5').update(fs.readFileSync(path)).digest('base64');
            };

            var writeIndex = function (folderPath) {
                var index = _this.getRepositoryIndex(folderPath);
                var indexPath = folderPath + INDEX_LOCATION;
                fs.writeFileSync(indexPath, JSON.stringify(index));
            };

            var updateIndexForPart = function (index, path, part) {
                setIndexValue(index, path, IndexKeys.HASH, getHashFromFile(path));
                setIndexValue(index, path, IndexKeys.WORKSPACE_ID, part.workspaceId);
                setIndexValue(index, path, IndexKeys.NUMBER, part.number);
                setIndexValue(index, path, IndexKeys.REVISION, part.version);
                setIndexValue(index, path, IndexKeys.ITERATION, part.partIterations.length);
            };

            var updateIndexForDocument = function (index, path, document) {
                setIndexValue(index, path, IndexKeys.HASH, getHashFromFile(path));
                setIndexValue(index, path, IndexKeys.WORKSPACE_ID, document.workspaceId);
                setIndexValue(index, path, IndexKeys.DOCUMENT_MASTER_ID, document.documentMasterId);
                setIndexValue(index, path, IndexKeys.REVISION, document.version);
                setIndexValue(index, path, IndexKeys.ITERATION, document.documentIterations.length);
            };

            var removeFromIndex = function (index, path) {
                Object.keys(IndexKeys)
                    .map(function (key) {
                        return path + '.' + IndexKeys[key];
                    })
                    .forEach(function (entry) {
                        delete index[entry];
                    });
            };

            var documentRequest = function (api, workspaceId, documentId, version) {
                return function () {
                    return $q(function (resolve, reject) {
                        api.apis.document.getDocumentRevision({
                            workspaceId: workspaceId,
                            documentId: documentId,
                            documentVersion: version
                        }).then(function (response) {
                            resolve(response.obj);
                        }, reject);
                    });
                };
            };

            var partRequest = function (api, workspaceId, number, version) {
                return function () {
                    return $q(function (resolve, reject) {
                        api.apis.part.getPartRevision({
                            workspaceId: workspaceId,
                            partNumber: number,
                            partVersion: version
                        }).then(function (response) {
                            resolve(response.obj);
                        }, reject);
                    });
                };
            };

            this.getItemBinaryResource = function (item, path) {
                var name = fileShortName(path);
                var itemLastIteration = lastIteration(item);
                var binaryResource;
                if (item.documentMasterId) {
                    binaryResource = $filter('filter')(itemLastIteration.attachedFiles, {name: name})[0];
                } else if (item.number) {
                    binaryResource = itemLastIteration.nativeCADFile;
                }
                return binaryResource;
            };

            this.getFileIndex = function (index, path) {

                var digest = getIndexValue(index, path, IndexKeys.DIGEST);
                var workspaceId = getIndexValue(index, path, IndexKeys.WORKSPACE_ID);

                return digest && workspaceId ? {
                    digest: digest,
                    workspaceId: workspaceId,
                    documentMasterId: getIndexValue(index, path, IndexKeys.DOCUMENT_MASTER_ID),
                    number: getIndexValue(index, path, IndexKeys.NUMBER),
                    revision: getIndexValue(index, path, IndexKeys.REVISION),
                    iteration: getIndexValue(index, path, IndexKeys.ITERATION),
                    lastModifiedDate: getIndexValue(index, path, IndexKeys.LAST_MODIFIED_DATE),
                    hash: getHashFromFile(path)
                } : null;

            };

            this.getRepositoryIndex = function (indexFolder) {
                try {
                    if (!indexes[indexFolder]) {
                        indexes[indexFolder] = $window.require(indexFolder + INDEX_LOCATION);
                    }
                } catch (e) {
                    var dir = indexFolder + '/.dplm/';
                    if (!fs.existsSync(dir)) {
                        fs.mkdirSync(dir);
                    }
                    fs.writeFileSync(indexFolder + INDEX_LOCATION, '{}');
                    indexes[indexFolder] = $window.require(indexFolder + INDEX_LOCATION);
                }
                return indexes[indexFolder];
            };

            this.search = function (folder) {
                return $q(function (resolve, reject) {
                    glob(INDEX_SEARCH_PATTERN, {
                        cwd: folder,
                        nodir: true
                    }, function (err, files) {
                        if (err) {
                            reject(err);
                        }
                        else {
                            resolve(files);
                        }
                    });
                });
            };


            this.saveItemToIndex = function (indexFolder, path, item) {
                if (item.number) {
                    _this.savePartToIndex(indexFolder, path, item);
                } else if (item.documentMasterId) {
                    _this.saveDocumentToIndex(indexFolder, path, item);
                }
            };

            this.savePartToIndex = function (indexFolder, path, part) {
                var index = _this.getRepositoryIndex(indexFolder);
                updateIndexForPart(index, path, part);
                writeIndex(indexFolder);
                FileUtils.setFileMode(path, part);
            };

            this.saveDocumentToIndex = function (indexFolder, path, document) {
                var index = _this.getRepositoryIndex(indexFolder);
                updateIndexForDocument(index, path, document);
                writeIndex(indexFolder);
                FileUtils.setFileMode(path, document);
            };

            this.updateFileInIndex = function (indexFolder, path) {
                var index = _this.getRepositoryIndex(indexFolder);
                setIndexValue(index, path, IndexKeys.DIGEST, getHashFromFile(path));
                setIndexValue(index, path, IndexKeys.LAST_MODIFIED_DATE, Date.now());
                writeIndex(indexFolder);
            };

            this.getLocalChanges = function (folder) {
                var indexFolder = folder.path;
                var index = _this.getRepositoryIndex(indexFolder);
                var keys = Object.keys(index);
                var changes = [];
                keys.forEach(function (key) {
                    var path;
                    if (key.endsWith('.' + IndexKeys.DOCUMENT_MASTER_ID)) {
                        path = key.substr(0, key.length - IndexKeys.DOCUMENT_MASTER_ID.length - 1);
                    } else if (key.endsWith('.' + IndexKeys.NUMBER)) {
                        path = key.substr(0, key.length - IndexKeys.NUMBER.length - 1);
                    }
                    if (path && _this.isModified(index, path)) {
                        changes.push(path);
                    }
                });
                return changes;
            };

            this.isModified = function (index, path) {
                var digest = getIndexValue(index, path, IndexKeys.DIGEST);
                return digest && getHashFromFile(path) !== digest;
            };

            this.isOutOfDate = function (index, file) {
                if (!file.index || !file.item) {
                    return false;
                }
                var binary = _this.getItemBinaryResource(file.item, file.path);
                return binary && getIndexValue(index, file.path, IndexKeys.LAST_MODIFIED_DATE) < new Date(binary.lastModified).getTime();
            };

            this.syncIndex = function (indexFolder) {

                var deferred = $q.defer();

                var promise = deferred.promise;

                var index = _this.getRepositoryIndex(indexFolder);
                var keys = Object.keys(index);

                var documents = keys.filter(function (key) {
                    return key.endsWith('.' + IndexKeys.DOCUMENT_MASTER_ID);
                });

                var parts = keys.filter(function (key) {
                    return key.endsWith('.' + IndexKeys.NUMBER);
                });

                var progress = 0;
                var total = documents.length + parts.length;

                var notify = function () {
                    deferred.notify({total: total, progress: ++progress, folder: indexFolder});
                };

                deferred.notify({total: total, progress: 0, folder: indexFolder});

                var chain = $q.when();

                DocdokuAPIService.getApi().then(function (api) {
                    documents.forEach(function (id) {
                        var filePath = id.substr(0, id.length - IndexKeys.DOCUMENT_MASTER_ID.length - 1);
                        var version = getIndexValue(index, filePath, IndexKeys.REVISION);
                        var workspaceId = getIndexValue(index, filePath, IndexKeys.WORKSPACE_ID);
                        chain = chain.then(documentRequest(api, workspaceId, index[id], version)).then(function (document) {
                            updateIndexForDocument(index, filePath, document);
                            return DBService.storeDocuments([document]);
                        }, function () {
                            removeFromIndex(index, filePath);
                        }).then(notify);
                    });

                    parts.forEach(function (number) {
                        var filePath = number.substr(0, number.length - IndexKeys.NUMBER.length - 1);
                        var version = getIndexValue(index, filePath, IndexKeys.REVISION);
                        var workspaceId = getIndexValue(index, filePath, IndexKeys.WORKSPACE_ID);
                        chain = chain.then(partRequest(api, workspaceId, index[number], version)).then(function (part) {
                            updateIndexForPart(index, filePath, part);
                            return DBService.storeParts([part]);
                        }, function () {
                            removeFromIndex(index, filePath);
                        }).then(notify);
                    });

                    return chain;

                }).then(function () {
                    FolderService.getFolder({path: indexFolder}).lastSync = new Date();
                    FolderService.save();
                    writeIndex(indexFolder);
                    deferred.resolve();
                });

                return promise;

            };

            this.syncIndexes = function (indexFolders) {

                var deferred = $q.defer();
                var promise = deferred.promise;
                var chain = $q.when();

                angular.forEach(indexFolders, function (folderPath) {
                    chain = chain.then(function () {
                        return _this.syncIndex(folderPath);
                    });
                });

                chain.then(deferred.resolve, null, deferred.notify);

                return promise;
            };

        });

})();
