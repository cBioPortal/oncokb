/**
 * Created by jiaojiao on 4/24/17.
 */
'use strict';

/**
 * @ngdoc directive
 * @name oncokbApp.directive:curationQueue
 * @description
 * # curationQueue
 */
angular.module('oncokbApp')
    .directive('curationQueue', function(DTColumnDefBuilder, DTOptionsBuilder, DatabaseConnector, $rootScope, $timeout, users, mainUtils, dialogs, _, storage, $q) {
        return {
            templateUrl: 'views/curationQueue.html',
            restrict: 'E',
            scope: {
                location: '=',
                queue: '=?', // the '?' makes it optional to assign value, otherwise it will throw Non_assignable expression
                docs: '=',
                metaFlags: '=',
                hugoSymbols: '=',
                specifyAnnotationInGene: '&specifyAnnotation'
            },
            replace: true,
            link: {
                pre: function preLink(scope) {
                    scope.data = {
                        allCurations: false,
                        curators: [],
                        modifiedCurator: {},
                        modifiedMainType: '',
                        modifiedSubType: {},
                        sectionList: ['Mutation Effect', 'Prevalence', 'Prognostic implications', 'NCCN guidelines', 'Standard sensitivity', 'Standard resistance', 'Investigational sensitivity', 'Investigational resistance'],
                        modifiedSection: '',
                        metaModel: '',
                        geneModel: '',
                        queueModel: '',
                        geneMetaData: '',
                        mainTypes: [],
                        subTypes: [],
                        formExpanded: false,
                        editing: false,
                        hugoVariantMapping: {},
                        resendEmail: false,
                        queueItemInEditing: '',
                        invalidData: false,
                        loading: false
                    };
                    scope.loading = {
                        add: false,
                        complete: {},
                        update: {},
                        delete: {}
                    };
                    scope.input = {
                        article: '',
                        link: '',
                        hugoSymbols: '',
                        variant: '',
                        mainType: '',
                        subType: '',
                        section: '',
                        curator: '',
                        dueDay: '',
                        comment: ''
                    };
                    scope.dtOptions = {
                        hasBootstrap: true,
                        paging: false,
                        scrollCollapse: true,
                        scrollY: 500,
                        aaSorting: [[0, 'asc']]
                    };
                    scope.dtColumns = [
                        DTColumnDefBuilder.newColumnDef(0),
                        DTColumnDefBuilder.newColumnDef(1),
                        DTColumnDefBuilder.newColumnDef(2),
                        DTColumnDefBuilder.newColumnDef(3),
                        DTColumnDefBuilder.newColumnDef(4),
                        null,null,null,
                        DTColumnDefBuilder.newColumnDef(8),
                        DTColumnDefBuilder.newColumnDef(9),
                        DTColumnDefBuilder.newColumnDef(10)
                    ];
                    if (scope.location === 'gene') {
                        scope.dtColumns[5] = DTColumnDefBuilder.newColumnDef(5).withOption('sType', 'date');
                        scope.dtColumns[6] = DTColumnDefBuilder.newColumnDef(6).withOption('sType', 'date-html');
                        scope.dtColumns[7] = DTColumnDefBuilder.newColumnDef(7);
                        scope.data.geneModel = $rootScope.model;
                        scope.data.queueModel = $rootScope.model.getRoot().get('queue');
                        scope.data.geneMetaData = $rootScope.geneMetaData;
                        scope.queue = [];
                        if (scope.data.queueModel) {
                            _.each(scope.data.queueModel.asArray(), function(item) {
                                scope.queue.push({
                                    article: item.get('article'),
                                    pmid: item.get('pmid'),
                                    pmidString: 'PMID: ' + item.get('pmid'),
                                    link: item.get('link'),
                                    variant: item.get('variant'),
                                    mainType: item.get('mainType'),
                                    subType: item.get('subType'),
                                    section: item.get('section'),
                                    addedBy: item.get('addedBy'),
                                    addedAt: item.get('addedAt'),
                                    curated: item.get('curated'),
                                    curator: item.get('curator'),
                                    comment: item.get('comment'),
                                    dueDay: item.get('dueDay'),
                                    notified: item.get('notified') // when the curation expired, we sent an email automatically. notified is used to track when this automated get sent.
                                });
                            });
                        }
                        scope.setArticlesNumberInMeta();
                    } else if (scope.location === 'genes') {
                        scope.dtColumns[5] = DTColumnDefBuilder.newColumnDef(5);
                        scope.dtColumns[6] = DTColumnDefBuilder.newColumnDef(6).withOption('sType', 'date');
                        scope.dtColumns[7] = DTColumnDefBuilder.newColumnDef(7).withOption('sType', 'date-html');
                        scope.data.metaModel = $rootScope.metaData;
                    }
                    scope.secondTimeAutoNotify();
                },
                post: function postLink(scope) {
                    scope.$watch('input.article', function(n, o) {
                        if (n !== o) {
                            $timeout.cancel(scope.articleTimeoutPromise);
                            scope.articleTimeoutPromise = $timeout(function() {
                                if (/^[\d]*$/.test(scope.input.article)) {
                                    scope.getArticle(scope.input.article);
                                }
                            }, 500);
                        }
                    });
                }
            },
            controller: function($scope) {
                DatabaseConnector.getOncokbInfo(function(oncokbInfo) {
                    if (oncokbInfo && oncokbInfo.users) {
                        $scope.data.curators = oncokbInfo.users;
                    }
                });
                $scope.userRole = users.getMe().role;
                $scope.getButtonHtml = function (type, addedAt) {
                    var result = '';
                    switch(type) {
                    case 'add':
                        if ($scope.loading.add) {
                            result = '<i class="fa fa-spinner" aria-hidden="true"></i>';
                        } else if ($scope.data.editing) {
                            result = 'Save modified curation';
                        } else {
                            result = 'Add';
                        }
                        break;
                    case 'complete':
                        if ($scope.loading.complete[addedAt]) {
                            $scope.data.loading = true;
                            result = '<i class="fa fa-spinner" aria-hidden="true"></i>';
                        } else {
                            result = '<i class="fa fa-check"></i>';
                        }
                        break;
                    case 'update':
                        if ($scope.loading.update[addedAt]) {
                            $scope.data.loading = true;
                            result = '<i class="fa fa-spinner" aria-hidden="true"></i>';
                        } else {
                            result = '<i class="fa fa-check"></i>';
                        }
                        break;
                    case 'delete':
                        if ($scope.loading.delete[addedAt]) {
                            $scope.data.loading = true;
                            result = '<i class="fa fa-spinner" aria-hidden="true"></i>';
                        } else {
                            result = '<i class="fa fa-trash-o"></i>';
                        }
                        break;
                    }
                    return result;
                };
                function searchQueueModel(hugoSymbol) {
                    var fileId;
                    for (var i = 0; i < $scope.docs.length; i++) {
                        if ($scope.docs[i].title === hugoSymbol) {
                            fileId = $scope.docs[i].id;
                            break;
                        }
                    }
                    var deferred = $q.defer();
                    if (fileId) {
                        storage.getRealtimeDocument(fileId).then(function(realtime) {
                            if (realtime && realtime.error) {
                                deferred.error();
                            } else {
                                $scope.data.geneModel = realtime.getModel();
                                $scope.data.queueModel = realtime.getModel().getRoot().get('queue');
                                $scope.data.geneMetaData = $scope.data.metaModel.get(hugoSymbol);
                                deferred.resolve();
                            }
                        });
                    } else {
                        deferred.error('Can not find the gene document');
                    }
                    return deferred.promise;
                }
                $scope.addCuration = function() {
                    if ($scope.data.editing) {
                        var queueItem;
                        for (var i = 0; i < $scope.queue.length; i++) {
                            if ($scope.queue[i].addedAt === $scope.data.queueItemInEditing.addedAt) {
                                queueItem = $scope.queue[i];
                                break;
                            }
                        }
                        if ($scope.location === 'genes') {
                            var promise = searchQueueModel(queueItem.hugoSymbol);
                            promise.then(function() {
                                saveConfirmedCuration(queueItem);
                            }, function(error) {
                            });
                        } else {
                            saveConfirmedCuration(queueItem);
                        }
                    } else {
                        if ($scope.location === 'genes') {
                            $scope.loading.add = true;
                            $scope.data.loading = true;
                            var tempArr = $scope.input.variant.split(';');
                            _.each(tempArr, function(pair) {
                                if (pair) {
                                    var tempIndex = pair.indexOf(':');
                                    var hugoSymbol = pair.substring(0, tempIndex);
                                    var variant = pair.substring(tempIndex+1);
                                    if (hugoSymbol && variant) {
                                        $scope.data.hugoVariantMapping[hugoSymbol.trim()] = variant.trim();
                                    }
                                }
                            });
                            addConfirmedCurationInGenes(0);
                        } else if ($scope.location === 'gene') {
                            addConfirmedCuration();
                            $scope.clearInput();
                        }
                    }
                };
                function addConfirmedCurationInGenes(index) {
                    var hugoSymbol = $scope.input.hugoSymbols[index];
                    var promise = searchQueueModel(hugoSymbol);
                    promise.then(function() {
                        if ($scope.data.geneModel) {
                            addConfirmedCuration(hugoSymbol);
                        }
                        if (index === $scope.input.hugoSymbols.length-1) {
                            $scope.loading.add = false;
                            $scope.data.loading = false;
                            $scope.clearInput();
                        } else {
                            $timeout(function() {
                                addConfirmedCurationInGenes(++index);
                            }, 200);
                        }
                    }, function(error) {
                    });
                }

                function addConfirmedCuration(hugoSymbol) {
                    $scope.data.geneModel.beginCompoundOperation();
                    var item = $scope.data.geneModel.createMap({
                        link: $scope.input.link,
                        variant: $scope.data.hugoVariantMapping[hugoSymbol] ? $scope.data.hugoVariantMapping[hugoSymbol] : $scope.input.variant,
                        mainType: $scope.input.mainType,
                        subType: $scope.input.subType ? $scope.input.subType.name : '',
                        section: $scope.input.section ? $scope.input.section.join() : '',
                        curator: $scope.input.curator ? $scope.input.curator.name : '',
                        curated: false,
                        addedBy: users.getMe().name,
                        addedAt: new Date().getTime(),
                        dueDay: $scope.input.dueDay ? new Date($scope.input.dueDay).getTime() : '',
                        comment: $scope.input.comment,
                        notified: false
                    });
                    if ($scope.predictedArticle && $scope.validPMID) {
                        item.set('article', $scope.predictedArticle);
                        item.set('pmid', $scope.input.article);
                    } else {
                        item.set('article', $scope.input.article);
                    }
                    $scope.data.queueModel.push(item);
                    var queueItem = {
                        article: item.get('article'),
                        pmid: item.get('pmid'),
                        pmidString: 'PMID: ' + item.get('pmid'),
                        link: item.get('link'),
                        variant: item.get('variant'),
                        mainType: item.get('mainType'),
                        subType: item.get('subType'),
                        section: item.get('section'),
                        addedBy: item.get('addedBy'),
                        addedAt: item.get('addedAt'),
                        curated: item.get('curated'),
                        curator: item.get('curator'),
                        dueDay: item.get('dueDay'),
                        comment: item.get('comment'),
                        notified: item.get('notified')
                    };
                    if ($scope.location === 'genes' && hugoSymbol) {
                        queueItem.hugoSymbol = hugoSymbol;
                    }
                    $scope.queue.push(queueItem);
                    $scope.setArticlesNumberInMeta(hugoSymbol);
                    if (item.get('curator')) {
                        $scope.sendEmail(queueItem);
                    }
                    $scope.data.geneModel.endCompoundOperation();
                }
                $scope.initialProcess = function(x, type) {
                    if ($scope.location === 'genes' && x.hugoSymbol) {
                        var hugoSymbol = x.hugoSymbol;
                        if (type !== 'edit') {
                            $scope.loading[type][x.addedAt] = true;
                            var promise = searchQueueModel(hugoSymbol);
                            promise.then(function(result) {
                                processByType(x, type);
                                $scope.loading[type][x.addedAt] = false;
                                $scope.data.loading = false;
                            }, function(error) {
                            });
                        } else {
                            processByType(x, type);
                        }
                    } else {
                        processByType(x, type);
                    }
                };
                function processByType(x, type) {
                    var queueModelItem, queueItem;
                    if (type !== 'edit') {
                        for (var i = 0; i < $scope.data.queueModel.length; i++) {
                            if ($scope.data.queueModel.get(i).get('addedAt') === x.addedAt) {
                                queueModelItem = $scope.data.queueModel.get(i);
                                break;
                            }
                        }
                    }
                    for (var i = 0; i < $scope.queue.length; i++) {
                        if ($scope.queue[i].addedAt === x.addedAt) {
                            queueItem = $scope.queue[i];
                            break;
                        }
                    }
                    switch (type) {
                    case 'edit':
                        editCuration(queueItem);
                        break;
                    case 'delete':
                        deleteCuration(queueItem, queueModelItem);
                        break;
                    case 'complete':
                        completeCuration(queueItem, queueModelItem);
                        break;
                    }
                }

                function editCuration(queueItem) {
                    $scope.data.resendEmail = false;
                    $scope.data.editing = true;
                    $scope.data.queueItemInEditing = queueItem;
                    $scope.data.modifiedCurator = {};
                    if (queueItem.curator) {
                        for (var i = 0; i < $scope.data.curators.length; i++) {
                            if ($scope.data.curators[i].name === queueItem.curator) {
                                $scope.data.modifiedCurator = $scope.data.curators[i];
                                break;
                            }
                        }
                    }
                    $scope.data.modifiedMainType = '';
                    $scope.data.modifiedSubType = {};
                    if (queueItem.mainType) {
                        for (var i = 0;i < $scope.data.mainTypes.length; i++) {
                            if ($scope.data.mainTypes[i] === queueItem.mainType) {
                                $scope.data.modifiedMainType = $scope.data.mainTypes[i];
                                break;
                            }
                        }
                    }
                    if ($scope.data.modifiedMainType && queueItem.subType) {
                        for (var i = 0;i < $scope.data.subTypes[$scope.data.modifiedMainType].length; i++) {
                            if ($scope.data.subTypes[$scope.data.modifiedMainType][i].name === queueItem.subType) {
                                $scope.data.modifiedSubType = $scope.data.subTypes[$scope.data.modifiedMainType][i];
                                break;
                            }
                        }
                    }
                    $scope.data.formExpanded = true;
                    $scope.input = {
                        article: queueItem.article,
                        link: queueItem.link,
                        variant: queueItem.variant,
                        mainType: $scope.data.modifiedMainType,
                        subType: $scope.data.modifiedSubType,
                        curator: $scope.data.modifiedCurator,
                        comment: queueItem.comment
                    };
                    if (queueItem.section) {
                        $scope.input.section = queueItem.section.split(',');
                    }
                    if (queueItem.dueDay) {
                        $scope.input.dueDay = $scope.getFormattedDate(queueItem.dueDay);
                    }
                    if ($scope.location === 'genes') {
                        $scope.input.hugoSymbols = [queueItem.hugoSymbol];
                    }
                    $timeout(function() {
                        var dueDay = angular.element(document.querySelector('#datepicker'));
                        dueDay.datepicker();
                    }, 1000);
                }
                $scope.getFormattedDate = function(timeStamp) {
                    var tempTime = new Date(timeStamp);
                    var month = tempTime.getMonth() + 1;
                    var day = tempTime.getDate();
                    var year = tempTime.getFullYear();
                    return month + "/" + day + "/" + year;
                }
                function saveConfirmedCuration(queueItem) {
                    var queueModelItem;
                    for (var i = 0; i < $scope.data.queueModel.length; i++) {
                        if ($scope.data.queueModel.get(i).get('addedAt') === $scope.data.queueItemInEditing.addedAt) {
                            queueModelItem = $scope.data.queueModel.get(i);
                            break;
                        }
                    }
                    queueModelItem.set('article', $scope.input.article);
                    queueModelItem.set('link', $scope.input.link);
                    queueModelItem.set('variant', $scope.input.variant);
                    queueModelItem.set('mainType', $scope.input.mainType);
                    queueModelItem.set('subType', $scope.input.subType ? $scope.input.subType.name : '');
                    queueModelItem.set('section', $scope.input.section ? $scope.input.section.join() : '');
                    queueModelItem.set('curator', $scope.input.curator ? $scope.input.curator.name : '');
                    queueModelItem.set('comment', $scope.input.comment);
                    if ($scope.input.dueDay) {
                        queueModelItem.set('dueDay', new Date($scope.input.dueDay).getTime());
                    }
                    queueItem.article = queueModelItem.get('article');
                    queueItem.link = queueModelItem.get('link');
                    queueItem.variant = queueModelItem.get('variant');
                    queueItem.mainType = queueModelItem.get('mainType');
                    queueItem.subType = queueModelItem.get('subType');
                    queueItem.section = queueModelItem.get('section');
                    queueItem.curator = queueModelItem.get('curator');
                    queueItem.dueDay = queueModelItem.get('dueDay');
                    queueItem.comment = queueModelItem.get('comment');
                    if ($scope.data.resendEmail) {
                        $scope.sendEmail(queueItem, queueModelItem);
                    }
                    $scope.clearInput();
                }
                function completeCuration(queueItem, queueModelItem) {
                    queueModelItem.set('curated', true);
                    queueItem.curated = true;
                    $scope.setArticlesNumberInMeta(queueItem.hugoSymbol);
                };
                function deleteCuration(queueItem, queueModelItem) {
                    $scope.data.queueModel.removeValue(queueModelItem);
                    var index = $scope.queue.indexOf(queueItem);
                    $scope.queue.splice(index, 1);
                    $scope.setArticlesNumberInMeta(queueItem.hugoSymbol);
                };
                $scope.getArticle = function(pmid) {
                    if (!pmid) {
                        $scope.predictedArticle = '';
                        $scope.validPMID = false;
                        $scope.input.link = '';
                        return;
                    }
                    DatabaseConnector.getPubMedArticle([pmid], function(data) {
                        var articleData = data.result[pmid];
                        if (!articleData || articleData.error) {
                            $scope.predictedArticle = '<p style="color: red">Invalid PMID</p>';
                            $scope.validPMID = false;
                            $scope.input.link = '';
                        } else {
                            var tempArticle = articleData.title.trim();
                            // for some articles, the tile start with '[', and end with '].' we need to trim it in such cases
                            if (/^\[.*\]\.$/.test(tempArticle)) {
                                tempArticle = tempArticle.substring(1, tempArticle.length - 2);
                            }
                            var articleStr = tempArticle + ' ';
                            if (articleData && _.isArray(articleData.authors) && articleData.authors.length > 0) {
                                articleStr += articleData.authors[0].name + ' et al. ';
                            }
                            if (articleData.source) {
                                articleStr += articleData.source + '.';
                            }
                            if (articleData.pubdate) {
                                articleStr += (new Date(articleData.pubdate)).getFullYear();
                            }
                            $scope.pmid = pmid;
                            $scope.predictedArticle = articleStr;
                            $scope.validPMID = true;
                            $scope.input.link = 'https://www.ncbi.nlm.nih.gov/pubmed/' + pmid;
                        }
                    }, function() {
                        console.log('error');
                    });
                };
                $scope.sendEmail = function(queueItem, queueModelItem) {
                    var expiredCuration = false;
                    if ($scope.isExpiredCuration(queueItem.dueDay)) {
                        expiredCuration = true;
                    }
                    var email = '';
                    for (var i = 0; i < $scope.data.curators.length; i++) {
                        if (queueItem.curator === $scope.data.curators[i].name) {
                            email = $scope.data.curators[i].email;
                            break;
                        }
                    }
                    if (!email) return;
                    var content = 'Dear ' + queueItem.curator.split(' ')[0] + ',\n\n';
                    if (expiredCuration) {
                        content += 'You have not completed curation of the assigned publication: ' + queueItem.article;
                        if (queueItem.link) {
                            content += '(' + queueItem.link + ')';
                        }
                        content += ' which was due on ' + $scope.getFormattedDate(queueItem.dueDay) + '. Please complete this assignment as soon as possible and let us know when you have done this. \n\nIf you have already completed this task, please remember to CLICK THE GREEN CHECK BOX BUTTON at the bottom of the gene page (this will let us know the task is complete). If you have any questions or concerns please email or slack us as needed.';
                        content += 'Thank you, \nOncoKB Admin';
                    } else {
                        content += queueItem.addedBy + ' of OncoKB would like you curate the following publications in the indicated alteration, tumor type and section:\n\n';
                        var tempArr = [queueItem.article];
                        if (queueItem.link) {
                            tempArr = tempArr.concat(['(', queueItem.link, ')']);
                        }
                        if (queueItem.variant) {
                            tempArr = tempArr.concat(['Alteration:', queueItem.variant + ',']);
                        }
                        if (queueItem.subType) {
                            tempArr = tempArr.concat(['Tumor type:', queueItem.subType + ',']);
                        } else if (queueItem.mainType) {
                            tempArr = tempArr.concat(['Tumor type:', queueItem.mainType + ',']);
                        }
                        if (queueItem.section) {
                            tempArr = tempArr.concat(['Section:', queueItem.section]);
                        }
                        content += tempArr.join(' ') + '\n';
                        if (queueItem.comment) {
                            content += queueItem.comment + '\n';
                        }
                        content += '\nPlease try to curate this literature before ' + $scope.getFormattedDate(queueItem.dueDay) + ' and remember to log your hours for curating this data.\n\n';
                        content += 'IMPORTANT: Please remember to CLICK THE GREEN CHECK BOX BUTTON at the bottom of the gene page (this will let us know the task is complete).\n\n';
                        content += 'If you have any questions or concerns please email or slack ' + queueItem.addedBy + '.\n\n';
                        content += 'Thank you, \nOncoKB Admin';
                    }
                    var subject = 'OncoKB Curation Assignment';
                    mainUtils.sendEmail(email, subject, content).then(function() {
                        if (expiredCuration) {
                            queueModelItem.set('notified', new Date().getTime());
                            queueItem.notified = new Date().getTime();
                        }
                    }, function(error) {
                        dialogs.error('Error', 'Failed to notify curator automatically. Please send curator email manually.');
                    });
                };

                var annotationLocation = $scope.specifyAnnotationInGene();
                $scope.getAnnotationLocation = function(x) {
                    if (x.pmid && annotationLocation[x.pmid]) {
                        return annotationLocation[x.pmid].join('; ');
                    } else if (x.article && annotationLocation[x.article]) {
                        return annotationLocation[x.article].join('; ');
                    }
                };
                $scope.setArticlesNumberInMeta = function(hugoSymbol) {
                    var incompleteCount = 0, allCount = 0;
                    if ($scope.location === 'genes' && hugoSymbol) {
                        _.each($scope.queue, function(item) {
                            if (item.hugoSymbol === hugoSymbol) {
                                if (!item.curated) {
                                    incompleteCount++;
                                }
                                allCount++;
                            }
                        });
                    } else if ($scope.location === 'gene') {
                        _.each($scope.queue, function(item) {
                            if (!item.curated) {
                                incompleteCount++;
                            }
                            allCount++;
                        });
                    }
                    if ($scope.data.geneMetaData) {
                        $scope.data.geneMetaData.set('CurationQueueArticles', incompleteCount);
                        $scope.data.geneMetaData.set('AllArticles', allCount);
                        if ($scope.location === 'genes') {
                            // CurationQueueArticles is set again to synchronize newly added curations with last column in genes table
                            $scope.metaFlags[hugoSymbol].CurationQueueArticles = incompleteCount;
                        }
                    }
                };
                function getOncoTreeMainTypes() {
                    mainUtils.getOncoTreeMainTypes().then(function(result) {
                        var mainTypesReturned = result.mainTypes,
                            tumorTypesReturned = result.tumorTypes;
                        if (mainTypesReturned) {
                            $scope.data.mainTypes = _.map(mainTypesReturned, function(item) {
                                return item.name;
                            });
                            if (_.isArray(tumorTypesReturned)) {
                                if (tumorTypesReturned.length === mainTypesReturned.length) {
                                    var tumorTypes = {};
                                    var allTumorTypes = [];
                                    _.each(mainTypesReturned, function(mainType, i) {
                                        tumorTypes[mainType.name] = tumorTypesReturned[i];
                                    });
                                    $scope.data.subTypes = tumorTypes;
                                } else {
                                    console.error('The number of returned tumor types is not matched with number of main types.');
                                }
                            }
                        }
                    }, function(error) {
                    });
                }
                getOncoTreeMainTypes();
                $scope.toggleForm = function() {
                    $scope.data.formExpanded = !$scope.data.formExpanded;
                    $timeout(function() {
                        var dueDay = angular.element(document.querySelector('#datepicker'));
                        // set 2 weeks as the default due day
                        $scope.input.dueDay = $scope.getFormattedDate(new Date().getTime() + 14*8.64e+7);
                        dueDay.datepicker();
                    }, 1000);
                };
                $scope.clearInput = function() {
                    $scope.input = {
                        article: '',
                        link: '',
                        hugoSymbols: '',
                        variant: '',
                        mainType: '',
                        subType: '',
                        section: '',
                        curator: '',
                        dueDay: '',
                        comment: ''
                    };
                    $scope.data.formExpanded = false;
                    $scope.data.editing = false;
                    $scope.predictedArticle = '';
                    $scope.validPMID = false;
                    $scope.data.resendEmail = false;
                };
                $scope.isExpiredCuration = mainUtils.isExpiredCuration;
                $scope.checkInput = function() {
                    var queueItem = $scope.data.queueItemInEditing;
                    if ($scope.data.editing) {
                        if ($scope.input.curator && queueItem.curator !== $scope.input.curator.name ||
                            $scope.input.dueDay && queueItem.dueDay !== new Date($scope.input.dueDay).getTime()) {
                            $scope.data.resendEmail = true;
                        } else {
                            $scope.data.resendEmail = false;
                        }
                    } else {
                        if ($scope.input.dueDay && $scope.isExpiredCuration(new Date($scope.input.dueDay).getTime())) {
                            $scope.data.invalidData = true;
                        } else {
                            $scope.data.invalidData = false;
                        }
                    }
                };
                $scope.secondTimeAutoNotify = function() {
                    _.each($scope.queue, function (queueItem) {
                        if (queueItem.curator && mainUtils.isExpiredCuration(queueItem.dueDay) && !queueItem.notified) {
                            $scope.sendEmail(queueItem);
                            queueItem.notified = new Date().getTime();
                        }
                    });
                    // In genes page, expired queueModelItem already got set in genes.js
                    if ($scope.location === 'gene' && $scope.data.queueModel) {
                        _.each($scope.data.queueModel.asArray(), function(queueModelItem) {
                            if (queueModelItem.get('curator') && mainUtils.isExpiredCuration(queueModelItem.get('dueDay')) && !queueModelItem.get('notified')) {
                                queueModelItem.set('notified', new Date().getTime());
                            }
                        });
                    }
                };
                function getTimeStamp(str) {
                    var date = new Date(str);
                    if(date instanceof Date && !isNaN(date.getTime())) {
                        return date.getTime();
                    } else {
                        return 0;
                    }
                }
                jQuery.extend(jQuery.fn.dataTableExt.oSort, {
                    'date-html-asc': function(a, b) {
                        a = $(a).text();
                        b = $(b).text();
                        return getTimeStamp(a) - getTimeStamp(b);
                    },
                    'date-html-desc': function(a, b) {
                        a = $(a).text();
                        b = $(b).text();
                        return getTimeStamp(b) - getTimeStamp(a);
                    }
                });
            }
        };
    })
;
