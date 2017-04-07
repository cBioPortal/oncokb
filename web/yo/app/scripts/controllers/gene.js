'use strict';

angular.module('oncokbApp')
    .controller('GeneCtrl', ['_', 'S', '$resource', '$interval', '$timeout', '$scope', '$rootScope', '$location', '$route', '$routeParams', '$window', 'dialogs', 'importer', 'storage', 'loadFile', 'user', 'users', 'documents', 'OncoKB', 'gapi', 'DatabaseConnector', 'SecretEmptyKey', '$sce', 'jspdf', 'FindRegex', 'stringUtils', 'mainUtils',
        function(_, S, $resource, $interval, $timeout, $scope, $rootScope, $location, $route, $routeParams, $window, dialogs, importer, storage, loadFile, User, Users, Documents, OncoKB, gapi, DatabaseConnector, SecretEmptyKey, $sce, jspdf, FindRegex, stringUtils, mainUtils) {
            $scope.test = function(event, a, b, c, d, e, f, g) {
                $scope.stopCollopse(event);
                console.log(a, b, c, d, e, f, g);
            };
            $scope.authorize = function() {
                storage.requireAuth(false).then(function() {
                    var target = $location.search().target;
                    if (target) {
                        $location.url(target);
                    } else {
                        storage.getDocument('1rFgBCL0ftynBxRl5E6mgNWn0WoBPfLGm8dgvNBaHw38').then(function(file) {
                            storage.downloadFile(file).then(function(text) {
                                $scope.curateFile = text;
                            });
                        });
                    }
                });
            };

            $scope.addMutation = function(newMutationName) {
                if (this.gene && newMutationName) {
                    newMutationName = newMutationName.toString().trim();
                    var exists = false;
                    var isVUS = false;
                    var mutationNameBlackList = [
                        'activating mutations',
                        'activating mutation',
                        'inactivating mutations',
                        'inactivating mutation'
                    ];
                    this.gene.mutations.asArray().forEach(function(e) {
                        if (e.name.getText().toLowerCase() === newMutationName.toLowerCase()) {
                            exists = true;
                        }
                    });
                    this.vus.asArray().forEach(function(e) {
                        if (e.name.getText().toLowerCase() === newMutationName.toLowerCase()) {
                            isVUS = true;
                        }
                    });

                    if (mutationNameBlackList
                            .indexOf(newMutationName.toLowerCase()) !== -1) {
                        dialogs.notify('Warning',
                            'This mutation name is not allowed.');
                    } else if (exists) {
                        dialogs.notify('Warning', 'Mutation exists.');
                    } else if (isVUS) {
                        dialogs.notify('Warning', 'Mutation is in VUS list.');
                    } else {
                        var _mutation = '';
                        $scope.realtimeDocument.getModel().beginCompoundOperation();
                        _mutation = $scope.realtimeDocument.getModel().create(OncoKB.Mutation);
                        var filteredContent = [];
                        _.each(newMutationName.split(','), function(item) {
                            item = item.trim();
                            if (item.length > 0) {
                                filteredContent.push(item);
                            }
                        });
                        _mutation.name.setText(filteredContent.join(','));
                        _mutation.oncogenic_eStatus.set('obsolete', 'false');
                        _mutation.shortSummary_eStatus.set('obsolete', 'false');

                        this.gene.mutations.push(_mutation);
                        $scope.realtimeDocument.getModel().endCompoundOperation();
                        $scope.initGeneStatus(_mutation);
                    }
                }
            };

            $scope.stateComparator = function(state, viewValue) {
                return viewValue === SecretEmptyKey || (String(state)).toLowerCase().indexOf((String(viewValue)).toLowerCase()) > -1;
            };

            $scope.getComments = function() {
                console.log($scope.comments);
            };

            $scope.addComment = function(object, key, string) {
                var _user = Users.getMe();
                if (object && object[key + '_comments'] && _user.email) {
                    var _comment = '';
                    var _date = new Date();

                    $scope.realtimeDocument.getModel().beginCompoundOperation();
                    _comment = $scope.realtimeDocument.getModel().create('Comment');
                    _comment.date.setText(_date.getTime().toString());
                    if (_user.name) {
                        _comment.userName.setText(_user.name);
                    } else {
                        _comment.userName.setText('Unknown');
                    }
                    _comment.email.setText(_user.email);
                    _comment.content.setText(string);
                    _comment.resolved.setText('false');
                    object[key + '_comments'].push(_comment);
                    $scope.realtimeDocument.getModel().endCompoundOperation();
                } else {
                    console.log('Unable to add comment.');
                }
            };
            $scope.vusUpdate = function(message) {
                if ($scope.status.isDesiredGene) {
                    if ($scope.status.vusUpdateTimeout) {
                        $timeout.cancel($scope.status.vusUpdateTimeout);
                    }
                    $scope.status.vusUpdateTimeout = $timeout(function() {
                        var vus = $scope.realtimeDocument.getModel().getRoot().get('vus');
                        var vusData = stringUtils.getVUSData(vus);
                        DatabaseConnector.updateVUS($scope.gene.name, JSON.stringify(vusData), function(result) {
                            console.log('success saving vus to database');
                        }, function(error) {
                            console.log('error happened when saving VUS to DB', error);
                            var subject = 'VUS update Error for ' + $scope.gene.name.getText();
                            var content = 'Error happened when ' + message + '. The system error returned is ' + error;
                            mainUtils.sendEmail('dev.oncokb@gmail.com', subject, content);
                        });
                    }, 2000);
                }
            };

            $scope.getData = function() {
            };
            $scope.showEntry = function(obj) {
                if (obj) {
                    // hideAllObsolete will be changed to true if current
                    // user permission is 4
                    return !($scope.status.hideAllObsolete && isObsoleted(obj));
                }
                return true;
            };
            function parseMutationString(mutationStr) {
                mutationStr = mutationStr.replace(/\([^\)]+\)/g, '');
                var parts = mutationStr.split(',');
                var altResults = [];
                var proteinChange = '';
                var displayName = '';
                for (var i = 0; i < parts.length; i++) {
                    if (parts[i].indexOf('[') === -1) {
                        proteinChange = parts[i];
                        displayName = parts[i];
                    } else {
                        var l = parts[i].indexOf('[');
                        var r = parts[i].indexOf(']');
                        proteinChange = parts[i].substring(0, l);
                        displayName = parts[i].substring(l + 1, r);
                    }

                    if (proteinChange.indexOf('/') === -1) {
                        altResults.push({
                            alteration: proteinChange,
                            name: displayName,
                            gene: {
                                hugoSymbol: $scope.gene.name.getText()
                            }
                        });
                    } else {
                        var tempRes = proteinChange.match(/([A-Z][0-9]+)(.*)/i);
                        var refs = tempRes[2].split('/');
                        for (var j = 0; j < refs.length; j++) {
                            altResults.push({
                                alteration: tempRes[1] + refs[j],
                                name: displayName,
                                gene: {
                                    hugoSymbol: $scope.gene.name.getText()
                                }
                            });
                        }
                    }
                }
                return altResults;
            }

            $rootScope.reviewMode = false;
            $scope.displayCheck = function(uuid, reviewObj, mutationReview, tumorReview, treatmentReview, precise) {
                // regular mode check
                if (!$rootScope.reviewMode) {
                    if (mutationReview && mutationReview.get('removed') || tumorReview && tumorReview.get('removed') || treatmentReview && treatmentReview.get('removed')) {
                        return false;
                    }
                    return true;
                }
                // review mode check
                if (mutationReview && mutationReview.get('removed') || tumorReview && tumorReview.get('removed') || treatmentReview && treatmentReview.get('removed')) {
                    // removedItem is set to true to indicate it is inside a deleted section
                    if(reviewObj && !reviewObj.get('removedItem')) {
                        reviewObj.set('removedItem', true);
                    }
                    return true;
                } else if(reviewObj && reviewObj.get('removedItem')) {
                    reviewObj.delete('removedItem');
                }
                // precisely check for this element
                if(_.isBoolean(precise) && precise) {
                    return needReview(uuid) || reviewObj.get('review') === false || reviewObj.get('rollback');
                } else {
                    // check elements in a section
                    return reviewObj.get('review') || reviewObj.get('action');
                }
            };
            function resetReview(reviewObj) {
                reviewObj.delete('review');
                reviewObj.delete('action');
                reviewObj.delete('rollback');
            }

            $scope.review = function() {
                if ($rootScope.reviewMode) {
                    $scope.exitReview();
                } else {
                    var collaborators = $scope.realtimeDocument.getCollaborators();
                    var otherCollaborators = {};
                    _.each(collaborators, function(collaborator) {
                        if (collaborator.displayName !== User.name) {
                            otherCollaborators[collaborator.displayName] = '';
                        }
                    });
                    if (Object.keys(otherCollaborators).length > 0) {
                        var dlg = dialogs.confirm('Reminder', Object.keys(otherCollaborators).join(', ') + ((Object.keys(otherCollaborators).length > 1 ) ? ' are' : ' is') + ' currently working on this gene document. Entering review mode will disable them from editing.');
                        dlg.result.then(function() {
                            prepareReviewItems();
                        });
                    } else {
                        prepareReviewItems();
                    }
                }
            };
            $scope.exitReview = function() {
                var currentReviewer = $scope.realtimeDocument.getModel().createString('');
                $scope.gene.name_review.set('currentReviewer', currentReviewer);
                $rootScope.reviewMode = false;
                $scope.fileEditable = true;
                myUpdatedEvidenceModels = [];
                $interval.cancel($scope.reviewMoeInterval);
                _.each($scope.geneStatus, function(item) {
                    item.isOpen = false;
                });
            };
            $scope.developerCheck = function() {
                var developers = ['Hongxin Zhang', 'Jianjiong Gao', 'Jiaojiao Wang'];
                if (developers.indexOf(Users.getMe().name) !== -1) {
                    return true;
                }
                return false;
            };
            $scope.geneMainDivStyle = {
                opacity: '1'
            };
            function needReview(uuid) {
                if(uuid) {
                    uuid = uuid.getText();
                    if ( $rootScope.geneMetaData.get(uuid) &&  $rootScope.geneMetaData.get(uuid).get('review')) {
                        return true;
                    }
                }
                return false;
            }

            function setReview(uuid, flag) {
                uuid = uuid.getText();
                if (flag) {
                    if ( $rootScope.geneMetaData.get(uuid)) {
                         $rootScope.geneMetaData.get(uuid).set('review', true);
                    } else {
                        var temp = $rootScope.metaModel.createMap();
                        temp.set('review', true);
                         $rootScope.geneMetaData.set(uuid, temp);
                    }
                } else if (!flag) {
                    if ( $rootScope.geneMetaData.get(uuid)) {
                         $rootScope.geneMetaData.get(uuid).set('review', false);
                    }
                }
            }

            function setOriginalStatus(reviewObjs) {
                for (var i = 0; i < reviewObjs.length; i++) {
                    var reviewObj = reviewObjs[i];
                    resetReview(reviewObj, true);
                }
            }

            function mostRecentItem(reviewObjs) {
                var mostRecent = -1;
                for (var i = 0; i < reviewObjs.length; i++) {
                    if (mostRecent < 0) {
                        if (reviewObjs[i].get('updateTime')) {
                            mostRecent = i;
                        }
                    } else if (Date.parse(reviewObjs[mostRecent].get('updateTime')) < Date.parse(reviewObjs[i].get('updateTime'))) {
                        mostRecent = i;
                    }
                }
                if (mostRecent < 0) {
                    return 0;
                }
                return mostRecent;
            }

            function setUpdatedSignature(tempArr, reviewObj) {
                var mostRecent = mostRecentItem(tempArr);
                reviewObj.set('updatedBy', tempArr[mostRecent].get('updatedBy'));
                reviewObj.set('updateTime', tempArr[mostRecent].get('updateTime'));
            }
            function isChangedBy(reviewObj, userName) {
                if (reviewObj.get('updatedBy') === userName) {
                    return true;
                } else {
                    return false;
                }
            }

            var myUpdatedEvidences = {};
            var myDeletedEvidences = [];
            var myGeneTypeEvidence = {};
            var myUpdatedEvidenceModels = [];
            var myDeletedEvidenceModels = [];

            function formMyEvidences(type, mutation, tumor, TI, treatment) {
                var evidenceResult = $scope.getEvidence(type, mutation, tumor, TI, treatment);
                myUpdatedEvidences = _.extend(myUpdatedEvidences, evidenceResult);
                myUpdatedEvidenceModels.push([type, mutation, tumor, TI, treatment]);
            }

            function prepareReviewItems() {
                var currentReviewer = $scope.realtimeDocument.getModel().createString(User.name);
                $scope.gene.name_review.set('currentReviewer', currentReviewer);
                $scope.status.noChanges = false;
                $scope.status.hasReviewContent = false;
                $scope.status.mutationChanged = false;
                if(needReview($scope.gene.summary_uuid) || needReview($scope.gene.type_uuid) || needReview($scope.gene.background_uuid)) {
                    $scope.status.hasReviewContent = true;
                }
                setOriginalStatus([$scope.gene.summary_review, $scope.gene.type_review, $scope.gene.background_review]);
                var mutationChanged = false;
                var tumorChanged = false;
                var treatmentChanged = false;
                var tempArr = [];
                for (var i = 0; i < $scope.gene.mutations.length; i++) {
                    $scope.geneStatus[i].isOpen = false;
                    var mutation = $scope.gene.mutations.get(i);
                    if(isObsoleted(mutation)) {
                        continue;
                    }
                    setOriginalStatus([mutation.name_review]);
                    if (mutation.name_review.get('removed')) {
                        $scope.status.hasReviewContent = true;
                        continue;
                    }
                    tempArr = [mutation.oncogenic_review, mutation.shortSummary_review, mutation.summary_review];
                    setOriginalStatus(tempArr);
                    if (needReview(mutation.shortSummary_uuid) || needReview(mutation.summary_uuid) || needReview(mutation.oncogenic_uuid)) {
                        mutation.oncogenic_review.set('review', true);
                        mutationChanged = true;
                        setUpdatedSignature(tempArr, mutation.oncogenic_review);
                    }
                    tempArr = [mutation.effect_review, mutation.description_review];
                    setOriginalStatus(tempArr);
                    if (needReview(mutation.description_uuid) || needReview(mutation.effect_uuid)) {
                        mutation.effect_review.set('review', true);
                        mutation.effect_review.set('mutation_effect', true);
                        mutationChanged = true;
                        setUpdatedSignature(tempArr, mutation.effect_review);
                    }
                    for (var j = 0; j < mutation.tumors.length; j++) {
                        var tumor = mutation.tumors.get(j);
                        if(isObsoleted(tumor)) {
                            continue;
                        }
                        setOriginalStatus([tumor.name_review]);
                        if (tumor.name_review.get('removed')) {
                            mutationChanged = true;
                            continue;
                        }
                        tempArr = [tumor.prevalence_review];
                        setOriginalStatus(tempArr);
                        if (needReview(tumor.prevalence_uuid)) {
                            tumor.prevalence_review.set('review', true);
                            tumorChanged = true;
                        }
                        tempArr = [tumor.progImp_review];
                        setOriginalStatus(tempArr);
                        if (needReview(tumor.progImp_uuid)) {
                            tumor.progImp_review.set('review', true);
                            tumorChanged = true;
                        }
                        tempArr = [tumor.nccn_review, tumor.nccn.therapy_review, tumor.nccn.disease_review, tumor.nccn.version_review, tumor.nccn.description_review];
                        setOriginalStatus(tempArr);
                        setOriginalStatus([tumor.cancerTypes_review]);
                        if (needReview(tumor.nccn.therapy_uuid) || needReview(tumor.nccn.disease_uuid) || needReview(tumor.nccn.version_uuid) || needReview(tumor.nccn.description_uuid)) {
                            tumor.nccn_review.set('review', true);
                            tumorChanged = true;
                            setUpdatedSignature(tempArr, tumor.nccn_review);
                        }
                        for (var k = 0; k < tumor.TI.length; k++) {
                            var ti = tumor.TI.get(k);
                            if(isObsoleted(ti)) {
                                continue;
                            }
                            for (var m = 0; m < ti.treatments.length; m++) {
                                var treatment = ti.treatments.get(m);
                                if(isObsoleted(treatment)) {
                                    continue;
                                }
                                setOriginalStatus([treatment.name_review]);
                                if (treatment.name_review.get('removed')) {
                                    treatmentChanged = true;
                                    continue;
                                }
                                tempArr = [treatment.name_review, treatment.level_review, treatment.indication_review, treatment.description_review];
                                setOriginalStatus(tempArr);
                                // we set review to TREATMENT_NAME_CHANGE to indicate the case where only treatment name get changed
                                if (needReview(treatment.level_uuid) || needReview(treatment.indication_uuid) || needReview(treatment.description_uuid)) {
                                    treatment.name_review.set('review', true);
                                    treatmentChanged = true;
                                    setUpdatedSignature(tempArr, treatment.name_review);
                                } else if(needReview(treatment.name_uuid)) {
                                    treatment.name_review.set('review', 'TREATMENT_NAME_CHANGE');
                                    treatmentChanged = true;
                                }
                            }
                            setOriginalStatus([ti.name_review, ti.description_review]);
                            if (needReview(ti.description_uuid) || treatmentChanged) {
                                ti.name_review.set('review', true);
                                tumorChanged = true;
                            }
                            treatmentChanged = false;
                        }
                        setOriginalStatus([tumor.name_review, tumor.summary_review, tumor.trials_review]);
                        if(needReview(tumor.summary_uuid)) {
                            tumorChanged = true;
                        }
                        if(needReview(tumor.trials_uuid) || needReview(tumor.name_uuid)) {
                            tumorChanged = true;
                        }
                        if (tumorChanged) {
                            tumor.name_review.set('review', true);
                            mutationChanged = true;
                        }
                        tumorChanged = false;
                    }
                    setOriginalStatus([mutation.name_review]);
                    if(needReview(mutation.name_uuid)) {
                        mutation.name_review.set('review', true);
                        mutationChanged = true;
                    }
                    if (mutationChanged) {
                        mutation.name_review.set('review', true);
                        $scope.geneStatus[i].isOpen = true;
                        $scope.status.hasReviewContent = true;
                        $scope.status.mutationChanged = true;
                    }
                    mutationChanged = false;
                }

                if($scope.status.hasReviewContent === false) {
                     $rootScope.geneMetaData.clear();
                    dialogs.notify('Warning', 'No changes need to be reviewed');
                } else {
                    $rootScope.reviewMode = true;
                    if($scope.status.mutationChanged) {
                        openChangedSections();
                    }
                }
            }
            function openChangedSections() {
                for (var i = 0; i < $scope.gene.mutations.length; i++) {
                    var mutation = $scope.gene.mutations.get(i);
                    if(!mutation.name_review.get('review')) {
                        continue;
                    }
                    if(!$scope.geneStatus[i]) {
                        $scope.initGeneStatus(mutation);
                    }
                    $scope.geneStatus[i].isOpen = true;
                    if(mutation.oncogenic_review.get('review')) {
                        $scope.geneStatus[i].oncogenic.isOpen = true;
                    }
                    if(mutation.effect_review.get('review')) {
                        $scope.geneStatus[i].mutationEffect.isOpen = true;
                    }
                    for (var j = 0; j < mutation.tumors.length; j++) {
                        var tumor = mutation.tumors.get(j);
                        if(!tumor.name_review.get('review')) {
                            continue;
                        }
                        if(!$scope.geneStatus[i][j]) {
                            $scope.initGeneStatus(mutation, tumor);
                        }
                        $scope.geneStatus[i][j].isOpen = true;
                        if(tumor.prevalence_review.get('review')) {
                            $scope.geneStatus[i][j].prevalence.isOpen = true;
                        }
                        if(tumor.progImp_review.get('review')) {
                            $scope.geneStatus[i][j].progImp.isOpen = true;
                        }
                        if(tumor.nccn_review.get('review')) {
                            $scope.geneStatus[i][j].nccn.isOpen = true;
                        }
                        if(tumor.trials_review.get('review')) {
                            $scope.geneStatus[i][j].trials.isOpen = true;
                        }
                        for (var k = 0; k < tumor.TI.length; k++) {
                            var ti = tumor.TI.get(k);
                            if(!ti.name_review.get('review')) {
                                continue;
                            }
                            if(!$scope.geneStatus[i][j][k]) {
                                $scope.initGeneStatus(mutation, tumor, ti);
                            }
                            $scope.geneStatus[i][j][k].isOpen = true;
                            for (var m = 0; m < ti.treatments.length; m++) {
                                var treatment = ti.treatments.get(m);
                                if(!treatment.name_review.get('review')) {
                                    continue;
                                }
                                if(!$scope.geneStatus[i][j][k][m]) {
                                    $scope.initGeneStatus(mutation, tumor, ti, treatment);
                                }
                                $scope.geneStatus[i][j][k][m].isOpen = true;
                            }
                        }
                    }
                }
            }

            function collectChangesByPerson(userName) {
                $scope.status.savingAll = false;
                $scope.status.noChanges = false;
                myUpdatedEvidences = {};
                myDeletedEvidences = [];
                myGeneTypeEvidence = {};
                myUpdatedEvidenceModels = [];

                if ($scope.gene.summary_review.get('updatedBy') === userName) {
                    formMyEvidences('GENE_SUMMARY', null, null, null, null);
                }
                if ($scope.gene.background_review.get('updatedBy') === userName) {
                    formMyEvidences('GENE_BACKGROUND', null, null, null, null);
                }
                if ($scope.gene.type_review.get('updatedBy') === userName) {
                    myGeneTypeEvidence = {
                        hugoSymbol: $scope.gene.name.getText(),
                        oncogene: $scope.gene.type.get('OCG') ? true : false,
                        tsg: $scope.gene.type.get('TSG') ? true : false
                    };
                }
                var tempArr = [];
                for (var i = 0; i < $scope.gene.mutations.length; i++) {
                    var mutation = $scope.gene.mutations.get(i);
                    if(isObsoleted(mutation)) {
                        continue;
                    }
                    if (mutation.name_review.get('removed')) {
                        myDeletedEvidences = collectUUIDs('mutation', mutation, myDeletedEvidences);
                        myDeletedEvidenceModels.push(['mutation', mutation]);
                        continue;
                    }
                    tempArr = [mutation.oncogenic_review, mutation.shortSummary_review, mutation.summary_review];
                    if (needReview(mutation.shortSummary_uuid) || needReview(mutation.summary_uuid) || needReview(mutation.oncogenic_uuid)) {
                        if (isChangedBy(mutation.oncogenic_review, userName)) {
                            formMyEvidences('ONCOGENIC', mutation, null, null, null);
                        }
                    }
                    tempArr = [mutation.effect_review, mutation.description_review];
                    if (needReview(mutation.description_uuid) || needReview(mutation.effect_uuid)) {
                        if (isChangedBy(mutation.effect_review, userName)) {
                            formMyEvidences('MUTATION_EFFECT', mutation, null, null, null);
                        }
                    }
                    for (var j = 0; j < mutation.tumors.length; j++) {
                        var tumor = mutation.tumors.get(j);
                        if(isObsoleted(tumor)) {
                            continue;
                        }
                        if (tumor.name_review.get('removed')) {
                            myDeletedEvidences = collectUUIDs('tumor', tumor, myDeletedEvidences);
                            myDeletedEvidenceModels.push(['tumor', mutation, tumor]);
                            continue;
                        }
                        tempArr = [tumor.prevalence_review];
                        if (needReview(tumor.prevalence_uuid)) {
                            if (isChangedBy(tumor.prevalence_review, userName)) {
                                formMyEvidences('PREVALENCE', mutation, tumor, null, null);
                            }
                        }
                        tempArr = [tumor.progImp_review];
                        if (needReview(tumor.progImp_uuid)) {
                            if (isChangedBy(tumor.progImp_review, userName)) {
                                formMyEvidences('PROGNOSTIC_IMPLICATION', mutation, tumor, null, null);
                            }
                        }
                        tempArr = [tumor.nccn_review, tumor.nccn.therapy_review, tumor.nccn.disease_review, tumor.nccn.version_review, tumor.nccn.description_review];
                        if (needReview(tumor.nccn.therapy_uuid) || needReview(tumor.nccn.disease_uuid) || needReview(tumor.nccn.version_uuid) || needReview(tumor.nccn.description_uuid)) {
                            if (isChangedBy(tumor.nccn_review, userName)) {
                                formMyEvidences('NCCN_GUIDELINES', mutation, tumor, null, null);
                            }
                        }
                        for (var k = 0; k < tumor.TI.length; k++) {
                            var ti = tumor.TI.get(k);
                            if(isObsoleted(ti)) {
                                continue;
                            }
                            for (var m = 0; m < ti.treatments.length; m++) {
                                var treatment = ti.treatments.get(m);
                                if(isObsoleted(treatment)) {
                                    continue;
                                }
                                if (treatment.name_review.get('removed')) {
                                    myDeletedEvidences = collectUUIDs('treatment', treatment, myDeletedEvidences);
                                    myDeletedEvidenceModels.push(['treatment', mutation, tumor, ti, treatment]);
                                    continue;
                                }
                                if(needReview(treatment.name_uuid)) {
                                    formMyEvidences('TREATMENT_NAME_CHANGE', mutation, tumor, ti, treatment);
                                }
                                tempArr = [treatment.level_review, treatment.indication_review, treatment.description_review];
                                if (needReview(treatment.name_uuid) || needReview(treatment.level_uuid) || needReview(treatment.indication_uuid) || needReview(treatment.description_uuid)) {
                                    if (isChangedBy(treatment.name_review, userName)) {
                                        formMyEvidences(ti.name.getText(), mutation, tumor, ti, treatment);
                                    }
                                }
                            }
                            if (needReview(ti.description_uuid)) {
                                if (ti.description_review.get('updatedBy') === userName) {
                                    formMyEvidences(ti.name.getText(), mutation, tumor, ti, null);
                                }
                            }
                        }
                        if(needReview(tumor.name_uuid)) {
                            formMyEvidences('TUMOR_NAME_CHANGE', mutation, tumor, null, null);
                        }
                        if(needReview(tumor.summary_uuid)) {
                            if (tumor.summary_review.get('updatedBy') === userName) {
                                formMyEvidences('TUMOR_TYPE_SUMMARY', mutation, tumor, null, null);
                            }
                        }
                        if(needReview(tumor.trials_uuid)) {
                            if (tumor.trials_review.get('updatedBy') === userName) {
                                formMyEvidences('CLINICAL_TRIAL', mutation, tumor, null, null);
                            }
                        }
                    }
                    if(needReview(mutation.name_uuid)) {
                        formMyEvidences('MUTATION_NAME_CHANGE', mutation, null, null, null);
                    }
                }
                if (_.isEmpty(myGeneTypeEvidence) && _.isEmpty(myUpdatedEvidences) && _.isEmpty(myDeletedEvidences)) {
                    $scope.status.noChanges = true;
                    dialogs.notify('Warning', 'You have no changes need to be saved');
                }
            }

            var apiCallCount = 0;

            function doneSaving() {
                if (apiCallCount === 1) {
                    $scope.status.savingAll = false;
                    $scope.status.noChanges = true;
                    myUpdatedEvidences = {};
                    myDeletedEvidences = [];
                    myGeneTypeEvidence = {};
                    myUpdatedEvidenceModels = [];
                    myDeletedEvidenceModels = [];
                } else {
                    apiCallCount--;
                }
            }

            $scope.acceptAllMyChanges = function() {
                collectChangesByPerson(User.name);
                if ($scope.status.isDesiredGene && !$scope.status.noChanges) {
                    $scope.status.savingAll = true;
                }
                if (_.isEmpty(myGeneTypeEvidence) && _.isEmpty(myUpdatedEvidences) && !_.isEmpty(myDeletedEvidences)) {
                    apiCallCount = 1;
                    evidenceDeleteUpdate(doneSaving);
                } else if (_.isEmpty(myGeneTypeEvidence) && !_.isEmpty(myUpdatedEvidences) && _.isEmpty(myDeletedEvidences)) {
                    apiCallCount = 1;
                    evidenceBatchUpdate(doneSaving);
                } else if (_.isEmpty(myGeneTypeEvidence) && !_.isEmpty(myUpdatedEvidences) && !_.isEmpty(myDeletedEvidences)) {
                    apiCallCount = 2;
                    evidenceBatchUpdate(doneSaving);
                    evidenceDeleteUpdate(doneSaving);
                } else if (!_.isEmpty(myGeneTypeEvidence) && _.isEmpty(myUpdatedEvidences) && _.isEmpty(myDeletedEvidences)) {
                    apiCallCount = 1;
                    geneTypeUpdate(doneSaving);
                } else if (!_.isEmpty(myGeneTypeEvidence) && _.isEmpty(myUpdatedEvidences) && !_.isEmpty(myDeletedEvidences)) {
                    apiCallCount = 2;
                    geneTypeUpdate(doneSaving);
                    evidenceDeleteUpdate(doneSaving);
                } else if (!_.isEmpty(myGeneTypeEvidence) && !_.isEmpty(myUpdatedEvidences) && _.isEmpty(myDeletedEvidences)) {
                    apiCallCount = 2;
                    geneTypeUpdate(doneSaving);
                    evidenceBatchUpdate(doneSaving);
                } else if (!_.isEmpty(myGeneTypeEvidence) && !_.isEmpty(myUpdatedEvidences) && !_.isEmpty(myDeletedEvidences)) {
                    apiCallCount = 3;
                    geneTypeUpdate(doneSaving);
                    evidenceBatchUpdate(doneSaving);
                    evidenceDeleteUpdate(doneSaving);
                }
            }
            function geneTypeUpdate(callback) {
                if ($scope.status.isDesiredGene) {
                    DatabaseConnector.updateGeneType($scope.gene.name.getText(), myGeneTypeEvidence, function(result) {
                        $scope.modelUpdate('GENE_TYPE', null, null, null, null);
                        callback();
                    }, function(error) {
                        dialogs.error('Error', 'Failed to update to database! Please contact the developer.');
                        $scope.status.savingAll = false;
                    });
                } else {
                    $scope.modelUpdate('GENE_TYPE', null, null, null, null);
                }
            }

            function evidenceBatchUpdate(callback) {
                if ($scope.status.isDesiredGene) {
                    _.each(_.keys(myUpdatedEvidences), function(uuid) {
                        if ( $rootScope.geneMetaData.get(uuid) && ! $rootScope.geneMetaData.get(uuid).get('review')) {
                            delete myUpdatedEvidences[uuid];
                        }
                    });
                    DatabaseConnector.updateEvidenceBatch(myUpdatedEvidences, function(result) {
                        for (var i = 0; i < myUpdatedEvidenceModels.length; i++) {
                            $scope.modelUpdate(myUpdatedEvidenceModels[i][0], myUpdatedEvidenceModels[i][1], myUpdatedEvidenceModels[i][2], myUpdatedEvidenceModels[i][3], myUpdatedEvidenceModels[i][4]);
                        }
                        myUpdatedEvidenceModels = [];
                        callback();
                    }, function(error) {
                        dialogs.error('Error', 'Failed to update to database! Please contact the developer.');
                        $scope.status.savingAll = false;
                    });
                } else {
                    for (var i = 0; i < myUpdatedEvidenceModels.length; i++) {
                        $scope.modelUpdate(myUpdatedEvidenceModels[i][0], myUpdatedEvidenceModels[i][1], myUpdatedEvidenceModels[i][2], myUpdatedEvidenceModels[i][3], myUpdatedEvidenceModels[i][4]);
                    }
                    myUpdatedEvidenceModels = [];
                }
            }
            function evidenceDeleteUpdate(callback) {
                if ($scope.status.isDesiredGene) {
                    DatabaseConnector.deleteEvidences(myDeletedEvidences, function(result) {
                        _.each(myDeletedEvidenceModels, function(item) {
                            removeModel(item[0], item[1], item[2], item[3], item[4], myDeletedEvidences);
                        });
                        callback();
                    }, function(error) {
                        dialogs.error('Error', 'Failed to update to database! Please contact the developer.');
                        $scope.status.savingAll = false;
                    });
                } else {
                    _.each(myDeletedEvidenceModels, function(item) {
                        removeModel(item[0], item[1], item[2], item[3], item[4], myDeletedEvidences);
                    });
                }
            }
            function getUUIDBbyContent(uuid, content) {
                return content ? uuid.getText() : '';
            }
            $scope.getEvidence = function(type, mutation, tumor, TI, treatment) {
                var evidences = {};
                var dataUUID = '';
                var extraDataUUID = '';
                var data = {
                    additionalInfo: null,
                    alterations: null,
                    cancerType: null,
                    description: null,
                    evidenceType: type,
                    gene: {
                        hugoSymbol: $scope.gene.name.getText()
                    },
                    knownEffect: null,
                    lastEdit: null,
                    levelOfEvidence: null,
                    subtype: null,
                    articles: [],
                    clinicalTrials: [],
                    nccnGuidelines: null,
                    treatments: null,
                    propagation: null
                };
                var levelMapping = {
                    '0': 'LEVEL_0',
                    '1': 'LEVEL_1',
                    '2A': 'LEVEL_2A',
                    '2B': 'LEVEL_2B',
                    '3A': 'LEVEL_3A',
                    '3B': 'LEVEL_3B',
                    '4': 'LEVEL_4',
                    'R1': 'LEVEL_R1',
                    'R2': 'LEVEL_R2',
                    'R3': 'LEVEL_R3',
                    'no': 'NO'
                };
                var extraData = _.clone(data);
                var i = 0;
                var uuids = [];
                switch (type) {
                case 'GENE_SUMMARY':
                    data.description = $scope.gene.summary.getText();
                    dataUUID = $scope.gene.summary_uuid.getText();
                    data.lastEdit = $scope.gene.summary_review.get('updateTime');
                    break;
                case 'GENE_BACKGROUND':
                    data.description = $scope.gene.background.getText();
                    dataUUID = $scope.gene.background_uuid.getText();
                    data.lastEdit = $scope.gene.background_review.get('updateTime');
                    break;
                case 'ONCOGENIC':
                    if(needReview(mutation.oncogenic_uuid) || needReview(mutation.summary_uuid)) {
                        data.knownEffect = mutation.oncogenic.getText();
                        data.description = mutation.summary.getText();
                        dataUUID = mutation.oncogenic_uuid.getText();
                        data.lastEdit = mutation.oncogenic_review.get('updateTime');
                    }
                    if (needReview(mutation.shortSummary_uuid)) {
                        extraData.description = mutation.shortSummary.getText();
                        extraData.evidenceType = 'MUTATION_SUMMARY';
                        extraData.alterations = parseMutationString(mutation.name.getText());
                        extraDataUUID = mutation.shortSummary_uuid.getText();
                        extraData.lastEdit = mutation.shortSummary_review.get('updateTime');
                    }
                    break;
                case 'MUTATION_EFFECT':
                    data.knownEffect = mutation.effect.value.getText();
                    data.description = mutation.description.getText();
                    dataUUID = mutation.effect_uuid.getText();
                    data.lastEdit = mutation.effect_review.get('updateTime');
                    break;
                case 'TUMOR_TYPE_SUMMARY':
                    data.description = tumor.summary.getText();
                    dataUUID = tumor.summary_uuid.getText();
                    data.lastEdit = tumor.summary_review.get('updateTime');
                    break;
                case 'PREVALENCE':
                    data.description = tumor.prevalence.getText();
                    dataUUID = tumor.prevalence_uuid.getText();
                    data.lastEdit = tumor.prevalence_review.get('updateTime');
                    break;
                case 'PROGNOSTIC_IMPLICATION':
                    data.description = tumor.progImp.getText();
                    dataUUID = tumor.progImp_uuid.getText();
                    data.lastEdit = tumor.progImp_review.get('updateTime');
                    break;
                case 'NCCN_GUIDELINES':
                    data.description = tumor.nccn.description.getText();
                    data.nccnGuidelines = [
                        {
                            therapy: stringUtils.getTextString(tumor.nccn.therapy.getText()),
                            category: '',
                            description: stringUtils.getTextString(tumor.nccn.description.getText()),
                            disease: stringUtils.getTextString(tumor.nccn.disease.getText()),
                            pages: '',
                            version: stringUtils.getTextString(tumor.nccn.version.getText())
                        }
                    ];
                    dataUUID = tumor.nccn_uuid.getText();
                    data.lastEdit = tumor.nccn_review.get('updateTime');
                    break;
                case 'Standard implications for sensitivity to therapy':
                    data.evidenceType = 'STANDARD_THERAPEUTIC_IMPLICATIONS_FOR_DRUG_SENSITIVITY';
                    data.knownEffect = 'Sensitive';
                    break;
                case 'Standard implications for resistance to therapy':
                    data.evidenceType = 'STANDARD_THERAPEUTIC_IMPLICATIONS_FOR_DRUG_RESISTANCE';
                    data.knownEffect = 'Resistant';
                    break;
                case 'Investigational implications for sensitivity to therapy':
                    data.evidenceType = 'INVESTIGATIONAL_THERAPEUTIC_IMPLICATIONS_DRUG_SENSITIVITY';
                    data.knownEffect = 'Sensitive';
                    break;
                case 'Investigational implications for resistance to therapy':
                    data.evidenceType = 'INVESTIGATIONAL_THERAPEUTIC_IMPLICATIONS_DRUG_RESISTANCE';
                    data.knownEffect = 'Resistant';
                    break;
                case 'CLINICAL_TRIAL':
                    for (i = 0; i < tumor.trials.length; i++) {
                        data.clinicalTrials.push({
                            nctId: tumor.trials.get(i)
                        });
                    }
                    dataUUID = tumor.trials_uuid.getText();
                    data.lastEdit = tumor.trials_review.get('updateTime');
                    break;
                case 'MUTATION_NAME_CHANGE':
                    uuids = collectUUIDs('mutation', mutation, []);
                    data.evidenceType = null;
                    break;
                case 'TUMOR_NAME_CHANGE':
                    uuids = collectUUIDs('tumor', tumor, []);
                    data.evidenceType = null;
                    break;
                case 'TREATMENT_NAME_CHANGE':
                    uuids = collectUUIDs('treatment', treatment, []);
                    data.evidenceType = null;
                    break;
                default:
                    break;
                }

                if (mutation) {
                    data.alterations = parseMutationString(stringUtils.getTextString(mutation.name.getText()));
                }
                if (tumor) {
                    var tempArr1 = [];
                    var tempArr2 = [];
                    _.each(tumor.cancerTypes.asArray(), function(item) {
                        tempArr1.push(item.cancerType.getText());
                        tempArr2.push(item.oncoTreeCode.getText() ? item.oncoTreeCode.getText() : 'null');
                    });
                    if(tempArr1.length > 0) {
                        data.cancerType = tempArr1.join(',');
                        data.subtype = tempArr2.join(',');
                    }
                }
                if (TI) {
                    if (!treatment) {
                        data.description = TI.description.getText();
                        dataUUID = TI.description_uuid.getText();
                        data.lastEdit = TI.description_review.get('updateTime');
                    } else {
                        dataUUID = treatment.name_uuid.getText();
                        data.lastEdit = treatment.name_review.get('updateTime');
                        data.levelOfEvidence = levelMapping[treatment.level.getText()];
                        data.description = treatment.description.getText();
                        data.propagation = levelMapping[treatment.name_eStatus.get('propagation')];
                        data.treatments = [];
                        var treatments = treatment.name.getText().split(',');
                        for (i = 0; i < treatments.length; i++) {
                            var drugs = treatments[i].split('+');
                            var drugList = [];
                            for (var j = 0; j < drugs.length; j++) {
                                drugList.push({
                                    drugName: stringUtils.getTextString(drugs[j])
                                });
                            }
                            data.treatments.push({
                                approvedIndications: [stringUtils.getTextString(treatment.indication.getText())],
                                drugs: drugList
                            });
                        }
                    }
                }
                if (data.description) {
                    data.description = stringUtils.getTextString(data.description);
                    var abstractResults = FindRegex.result(data.description);
                    var tempAbstract;
                    for (var i = 0; i < abstractResults.length; i++) {
                        tempAbstract = abstractResults[i];
                        switch (tempAbstract.type) {
                        case 'pmid':
                            data.articles.push({
                                pmid: tempAbstract.id
                            });
                            break;
                        case 'abstract':
                            data.articles.push({
                                abstract: tempAbstract.id,
                                link: tempAbstract.link
                            });
                            break;
                        case 'nct':
                            data.clinicalTrials.push({
                                nctId: tempAbstract.id
                            });
                            break;
                        }
                    }
                }
                if(data.lastEdit) {
                    data.lastEdit = validateTimeFormat(data.lastEdit);
                }
                if(extraData.lastEdit) {
                    extraData.lastEdit = validateTimeFormat(extraData.lastEdit);
                }
                if(dataUUID) {
                    evidences[dataUUID] = data;
                }
                if(extraDataUUID) {
                    evidences[extraDataUUID] = extraData;
                }
                if(['MUTATION_NAME_CHANGE', 'TUMOR_NAME_CHANGE', 'TREATMENT_NAME_CHANGE'].indexOf(type) !== -1) {
                    _.each(uuids, function(uuid) {
                        evidences[uuid] = data;
                    });
                }
                return evidences;
            };
            function validateTimeFormat(updateTime) {
                var tempTime = new Date(updateTime);
                if(tempTime instanceof Date && !isNaN(tempTime.getTime())) {
                    updateTime = tempTime.getTime().toString();
                } else {
                    updateTime = new Date().getTime().toString();
                }
                return updateTime;
            }

            function setReviewModeInterval() {
                $interval.cancel($scope.reviewMoeInterval);
                $scope.reviewMoeInterval = $interval(function() {
                    if ($rootScope.reviewMode) {
                        $scope.review();
                        $interval.cancel($scope.reviewMoeInterval);
                    }
                }, 1000 * 60 * 15);
            }
            function acceptItem(arr, reviewObj) {
                _.each(arr, function(item) {
                    // This condition check is to remove review mapping precisely
                    if ( $rootScope.geneMetaData.get(item.uuid.getText()) &&  $rootScope.geneMetaData.get(item.uuid.getText()).get('review')) {
                        var tempTime = item.reviewObj.get('updateTime');
                        item.reviewObj.clear();
                        item.reviewObj.set('review', false);
                        item.reviewObj.set('updateTime', tempTime);
                        // This check is for the case of Mutation/Tumor/Treatment Name change. Since they share the same uuid with deletion.
                        // We need to make sure not set review to false in meta if it also been removed.
                        var currentReviewObj = item.tumorNameReview ? item.tumorNameReview : item.reviewObj;
                        if(!currentReviewObj.get('removed')) {
                            $rootScope.geneMetaData.get(item.uuid.getText()).set('review', false);
                        }
                    }
                });
                if (reviewObj) {
                    reviewObj.set('action', 'accepted');
                }
            }
            $scope.modelUpdate = function(type, mutation, tumor, ti, treatment) {
                switch (type) {
                case 'GENE_SUMMARY':
                    acceptItem([{reviewObj: $scope.gene.summary_review, uuid: $scope.gene.summary_uuid}], $scope.gene.summary_review);
                    break;
                case 'GENE_BACKGROUND':
                    acceptItem([{reviewObj: $scope.gene.background_review, uuid: $scope.gene.background_uuid}], $scope.gene.background_review);
                    break;
                case 'GENE_TYPE':
                    acceptItem([{reviewObj: $scope.gene.type_review, uuid: $scope.gene.type_uuid}], $scope.gene.type_review);
                    break;
                case 'ONCOGENIC':
                    acceptItem([{reviewObj: mutation.oncogenic_review, uuid: mutation.oncogenic_uuid},
                        {reviewObj: mutation.summary_review, uuid: mutation.summary_uuid},
                        {reviewObj: mutation.shortSummary_review, uuid: mutation.shortSummary_uuid}], mutation.oncogenic_review);
                    break;
                case 'MUTATION_EFFECT':
                    acceptItem([{reviewObj: mutation.effect_review, uuid: mutation.effect_uuid},
                        {reviewObj: mutation.description_review, uuid: mutation.description_uuid}], mutation.effect_review);
                    break;
                case 'TUMOR_TYPE_SUMMARY':
                    acceptItem([{reviewObj: tumor.summary_review, uuid: tumor.summary_uuid}], tumor.summary_review);
                    break;
                case 'PREVALENCE':
                    acceptItem([{reviewObj: tumor.prevalence_review, uuid: tumor.prevalence_uuid}], tumor.prevalence_review);
                    break;
                case 'PROGNOSTIC_IMPLICATION':
                    acceptItem([{reviewObj: tumor.progImp_review, uuid: tumor.progImp_uuid}], tumor.progImp_review);
                    break;
                case 'NCCN_GUIDELINES':
                    tumor.nccn_review.clear();
                    tumor.nccn_review.set('review', false);
                    acceptItem([{reviewObj: tumor.nccn.therapy_review, uuid: tumor.nccn.therapy_uuid},
                        {reviewObj: tumor.nccn.disease_review, uuid: tumor.nccn.disease_uuid},
                        {reviewObj: tumor.nccn.version_review, uuid: tumor.nccn.version_uuid},
                        {reviewObj: tumor.nccn.description_review, uuid: tumor.nccn.description_uuid}], tumor.nccn_review);
                    break;
                case 'Standard implications for sensitivity to therapy':
                case 'Standard implications for resistance to therapy':
                case 'Investigational implications for sensitivity to therapy':
                case 'Investigational implications for resistance to therapy':
                    if (!treatment) {
                        acceptItem([{reviewObj: ti.description_review, uuid: ti.description_uuid}], ti.description_review);
                    } else {
                        acceptItem([{reviewObj: treatment.name_review, uuid: treatment.name_uuid},
                            {reviewObj: treatment.indication_review, uuid: treatment.indication_uuid},
                            {reviewObj: treatment.description_review, uuid: treatment.description_uuid}], treatment.name_review);
                        // handle level specifically because level and propagation share the same uuid and review object
                        var levelChanged =  $rootScope.geneMetaData.get(treatment.level_uuid.getText()) &&  $rootScope.geneMetaData.get(treatment.level_uuid.getText()).get('review');
                        if(levelChanged) {
                            treatment.level_review.clear();
                            treatment.level_review.set('review', false);
                             $rootScope.geneMetaData.get(treatment.level_uuid.getText()).set('review', false);
                        }
                    }
                    break;
                case 'CLINICAL_TRIAL':
                    acceptItem([{reviewObj: tumor.trials_review, uuid: tumor.trials_uuid}], tumor.trials_review);
                    break;
                case 'MUTATION_NAME_CHANGE':
                    acceptItem([{reviewObj: mutation.name_review, uuid: mutation.name_uuid}], mutation.name_review);
                    break;
                case 'TUMOR_NAME_CHANGE':
                    acceptItem([{reviewObj: tumor.cancerTypes_review, uuid: tumor.name_uuid, tumorNameReview: tumor.name_review}], tumor.cancerTypes_review);
                    break;
                case 'TREATMENT_NAME_CHANGE':
                    acceptItem([{reviewObj: treatment.name_review, uuid: treatment.name_uuid}], treatment.name_review);
                    break;
                default:
                    break;
                }
            };
            /*
            * This function is used to collect uuids for obsoleted section.
            * */
            function getObsoletedUUIDs(type, mutation, tumor, TI, treatment) {
                switch (type) {
                case 'mutation':
                    return collectUUIDs(type, mutation, []);
                case 'tumor':
                    return collectUUIDs(type, tumor, []);
                case 'TI':
                    return collectUUIDs(type, TI, []);
                case 'treatment':
                    return collectUUIDs(type, treatment, []);
                case 'GENE_SUMMARY':
                    return [$scope.gene.summary_uuid.getText()];
                case 'GENE_BACKGROUND':
                    return [$scope.gene.background_uuid.getText()];
                case 'ONCOGENIC':
                    return [mutation.oncogenic_uuid.getText()];
                case 'MUTATION_EFFECT':
                    return [mutation.effect_uuid.getText()];
                case 'PREVALENCE':
                    return [tumor.prevalence_uuid.getText()];
                case 'PROGNOSTIC_IMPLICATION':
                    return [tumor.progImp_uuid.getText()];
                case 'NCCN_GUIDELINES':
                    return [tumor.nccn_uuid.getText()];
                case 'CLINICAL_TRIAL':
                    return [tumor.trials_uuid.getText()];
                }
            };
            /*
            * When curators unobsoleted items, that would make api call to insert evidences to database
            * This function is used to form evidence models, which would be used for api call
            * */
            function prepareInsertion(type, mutation, tumor, TI, treatment) {
                var evidences = {};
                switch (type) {
                case 'mutation':
                    formSectionEvidences(type, mutation, tumor, TI, treatment, evidences);
                    break;
                case 'tumor':
                    if(isObsoleted(mutation)) return {};
                    formSectionEvidences(type, mutation, tumor, TI, treatment, evidences);
                    break;
                case 'TI':
                    if(isObsoleted(mutation) || isObsoleted(tumor)) return {};
                    formSectionEvidences(type, mutation, tumor, TI, treatment, evidences);
                    break;
                case 'treatment':
                    if(isObsoleted(mutation) || isObsoleted(tumor) || isObsoleted(TI)) return {};
                    formSectionEvidences(type, mutation, tumor, TI, treatment, evidences);
                    break;
                case 'GENE_SUMMARY':
                case 'GENE_BACKGROUND':
                    formEvidencesByType([type], null, null, null, null, evidences);
                    break;
                case 'ONCOGENIC':
                case 'MUTATION_EFFECT':
                case 'PREVALENCE':
                case 'PROGNOSTIC_IMPLICATION':
                case 'NCCN_GUIDELINES':
                case 'CLINICAL_TRIAL':
                    formEvidencesByType([type], mutation, tumor, TI, treatment, evidences);
                    break;
                }
                return evidences;
            }
            function formSectionEvidences(type, mutation, tumor, ti, treatment, evidences) {
                var typeArr = [];
                var dataArr = [];
                var tempType = '';
                if (type === 'mutation') {
                    typeArr = ['ONCOGENIC', 'MUTATION_EFFECT'];
                    dataArr = mutation.tumors.asArray();
                    tempType = 'tumor';
                }
                if (type === 'tumor') {
                    typeArr = ['TUMOR_TYPE_SUMMARY', 'PREVALENCE', 'PROGNOSTIC_IMPLICATION', 'NCCN_GUIDELINES', 'CLINICAL_TRIAL'];
                    dataArr = tumor.TI.asArray();
                    tempType = 'TI';
                }
                if(type === 'TI') {
                    typeArr = [ti.name.getText()];
                    dataArr = ti.treatments.asArray();
                    tempType = 'treatment';
                    formEvidencesByType(typeArr, mutation, tumor, ti, null, evidences);
                }
                if (type === 'treatment') {
                    typeArr = [ti.name.getText()];
                }
                formEvidencesByType(typeArr, mutation, tumor, ti, treatment, evidences);
                _.each(dataArr, function(item) {
                    if(type === 'mutation')tumor = item;
                    if(type === 'tumor')ti = item;
                    if(type === 'TI')treatment = item;
                    formSectionEvidences(tempType, mutation, tumor, ti, treatment, evidences);
                });
                return evidences;
            };
            function formEvidencesByType(types, mutation, tumor, TI, treatment, evidences) {
                _.each(types, function(type) {
                    var evidenceResult = $scope.getEvidence(type, mutation, tumor, TI, treatment);
                    evidences = _.extend(evidences, evidenceResult);
                });
            };
            function isObsoleted(object, key) {
                // set default key to be name
                if(!key)key = 'name';
                return object && object[key+'_eStatus'] && object[key+'_eStatus'].get('obsolete') === 'true';
            }
            $scope.applyObsolete = function(eStatus, type, mutation, tumor, TI, treatment) {
                if(eStatus.get('obsolete') === 'true') {
                    if (!$scope.status.isDesiredGene) {
                        eStatus.set('obsolete', 'false');
                        return true;
                    }
                    var evidences = prepareInsertion(type, mutation, tumor, TI, treatment);
                    if(_.isEmpty(evidences)) {
                        eStatus.set('obsolete', 'false');
                        return false;
                    }
                    $scope.$emit('startSaveDataToDatabase');
                    DatabaseConnector.updateEvidenceBatch(evidences, function(result) {
                        $scope.$emit('doneSaveDataToDatabase');
                        eStatus.set('obsolete', 'false');
                    }, function(error) {
                        dialogs.error('Error', 'Failed to update to database! Please contact the developer.');
                        $scope.$emit('doneSaveDataToDatabase');
                    });
                } else {
                    // if the parent section is already obsolted, there is no need to do anything else
                    if(isObsoleted(mutation) || isObsoleted(tumor) || isObsoleted(TI)) {
                        dialogs.error('Warning', 'Current item is located in an obsoleted section');
                        return true;
                    }
                    var uuids = getObsoletedUUIDs(type, mutation, tumor, TI, treatment);
                    if (!$scope.status.isDesiredGene) {
                        eStatus.set('obsolete', 'true');
                        _.each(uuids, function(uuid) {
                            if(uuid) {
                                $rootScope.geneMetaData.delete(uuid);
                            }
                        });
                        return true;
                    }
                    // make the api call to delete evidences
                    $scope.$emit('startSaveDataToDatabase');
                    DatabaseConnector.deleteEvidences(uuids, function(result) {
                        $scope.$emit('doneSaveDataToDatabase');
                        eStatus.set('obsolete', 'true');
                        _.each(uuids, function(uuid) {
                            if(uuid) {
                                $rootScope.geneMetaData.delete(uuid);
                            }
                        });
                    }, function(error) {
                        dialogs.error('Error', 'Failed to update to database! Please contact the developer.');
                        $scope.$emit('doneSaveDataToDatabase');
                    });
                }
            }

            $scope.updateGene = function() {
                $scope.docStatus.savedGene = false;

                var gene = stringUtils.getGeneData(this.gene, true, true, true, false, true);
                var vus = stringUtils.getVUSFullData(this.vus, true);
                var params = {};

                if (gene) {
                    params.gene = JSON.stringify(gene);
                }
                if (vus) {
                    params.vus = JSON.stringify(vus);
                }

                DatabaseConnector.updateGene(params, function(result) {
                    $scope.docStatus.savedGene = true;
                    changeLastUpdate();
                }, function(result) {
                    $scope.docStatus.savedGene = true;
                    var errorMessage = 'An error has occurred when saving ' +
                        'data, please contact the developer.';

                    // dialogs.error('Error', errorMessage);
                    $rootScope.$emit('oncokbError',
                        {
                            message: 'An error has occurred when saving data. ' +
                            'Gene: ' + $scope.gene.name.getText(),
                            reason: JSON.stringify(result)
                        });
                    changeLastUpdate();
                });
            };

            function changeLastUpdate() {
                if ($scope.gene.status_timeStamp.has('lastUpdate')) {
                    $scope.gene.status_timeStamp.get('lastUpdate').value.setText(new Date().getTime().toString());
                    $scope.gene.status_timeStamp.get('lastUpdate').by.setText(Users.getMe().name);
                } else {
                    var timeStamp;
                    $scope.realtimeDocument.getModel().beginCompoundOperation();
                    timeStamp = $scope.realtimeDocument.getModel().create('TimeStamp');
                    timeStamp.value.setText(new Date().getTime().toString());
                    timeStamp.by.setText(Users.getMe().name);
                    $scope.gene.status_timeStamp.set('lastUpdate', timeStamp);
                    $scope.realtimeDocument.getModel().endCompoundOperation();
                }
                $scope.docStatus.updateGene = true;
            }

            $scope.addTumorType = function(mutation) {
                var newTumorTypesName = getNewCancerTypesName($scope.meta.newCancerTypes);

                if (mutation && newTumorTypesName) {
                    var _tumorType = '';
                    var exists = false;
                    var model = $scope.realtimeDocument.getModel();

                    mutation.tumors.asArray().forEach(function(e) {
                        if ($scope.getCancerTypesName(e.cancerTypes).toLowerCase() === newTumorTypesName.toLowerCase()) {
                            exists = true;
                        }
                    });

                    if (exists) {
                        dialogs.notify('Warning', 'Tumor type exists.');
                    } else {
                        model.beginCompoundOperation();
                        _tumorType = model.create(OncoKB.Tumor);

                        _.each($scope.meta.newCancerTypes, function(ct) {
                            if (ct.mainType && ct.mainType.name) {
                                var cancerType = model.create(OncoKB.CancerType);
                                cancerType.cancerType.setText(ct.mainType.name);
                                if (ct.subtype) {
                                    if (ct.subtype.code) {
                                        cancerType.oncoTreeCode.setText(ct.subtype.code);
                                    }
                                    if (ct.subtype.name) {
                                        cancerType.subtype.setText(ct.subtype.name);
                                    }
                                }
                                cancerType.cancerType_eStatus.set('obsolete', 'false');
                                cancerType.subtype_eStatus.set('obsolete', 'false');
                                cancerType.oncoTreeCode_eStatus.set('obsolete', 'false');
                                _tumorType.cancerTypes.push(cancerType);
                            }
                        });
                        _tumorType.nccn.category.setText('2A');
                        for (var i = 0; i < 4; i++) {
                            var __ti = model.create(OncoKB.TI);
                            var __status = i < 2 ? 1 : 0; // 1: Standard, 0: Investigational
                            var __type = i % 2 === 0 ? 1 : 0; // 1: sensitivity, 0: resistance
                            var __name = (__status ? 'Standard' : 'Investigational') + ' implications for ' + (__type ? 'sensitivity' : 'resistance') + ' to therapy';

                            __ti.types.set('status', __status.toString());
                            __ti.types.set('type', __type.toString());
                            __ti.name.setText(__name);
                            _tumorType.TI.push(__ti);
                        }
                        mutation.tumors.push(_tumorType);
                        model.endCompoundOperation();
                        $scope.meta.newCancerTypes = [{
                            mainType: '',
                            subtype: '',
                            oncoTreeTumorTypes: angular.copy($scope.oncoTree.allTumorTypes)
                        }];
                        var mutationIndex = this.gene.mutations.indexOf(mutation);
                        $scope.initGeneStatus(mutation, _tumorType);
                    }
                }
            };

            $scope.modifyTumorType = function(tumorType) {
                var dlg = dialogs.create('views/modifyTumorTypes.html', 'ModifyTumorTypeCtrl', {
                    model: $scope.realtimeDocument.getModel(),
                    cancerTypes: tumorType.cancerTypes,
                    oncoTree: $scope.oncoTree,
                    cancerTypes_review: tumorType.cancerTypes_review,
                    cancerTypes_uuid: tumorType.name_uuid
                }, {
                    size: 'lg'
                });
                dlg.result.then(function(name) {
                    console.log('successfully updated tumor type');
                    // write the old cancertype and subtypes to the review model
                }, function() {
                    console.log('failed to updated tumor type');
                });
            };

            // Add new therapeutic implication
            $scope.addTI = function(newTIName, mutation, tumor, ti) {
                if (ti && newTIName) {
                    var _treatment = '';
                    var exists = false;
                    newTIName = newTIName.toString().trim();

                    ti.treatments.asArray().forEach(function(e) {
                        if (e.name.getText().toLowerCase() === newTIName.toLowerCase()) {
                            exists = true;
                        }
                    });

                    if (exists) {
                        dialogs.notify('Warning', 'Therapy exists.');
                    } else {
                        $scope.realtimeDocument.getModel().beginCompoundOperation();
                        _treatment = $scope.realtimeDocument.getModel().create(OncoKB.Treatment);
                        _treatment.name.setText(newTIName);
                        _treatment.type.setText('Therapy');
                        if ($scope.checkTI(ti, 1, 1)) {
                            _treatment.level.setText('1');
                        } else if ($scope.checkTI(ti, 0, 1)) {
                            _treatment.level.setText('4');
                        } else if ($scope.checkTI(ti, 1, 0)) {
                            _treatment.level.setText('1');
                        } else if ($scope.checkTI(ti, 0, 0)) {
                            _treatment.level.setText('4');
                        }
                        ti.treatments.push(_treatment);
                        $scope.realtimeDocument.getModel().endCompoundOperation();
                        var mutationIndex = this.gene.mutations.indexOf(mutation);
                        var tumorIndex = mutation.tumors.indexOf(tumor);
                        var tiIndex = tumor.TI.indexOf(ti);
                        $scope.initGeneStatus(mutation, tumor, ti, _treatment);
                    }
                }
            };

            $scope.onFocus = function(e) {
                $timeout(function() {
                    $(e.target).trigger('input');
                    $(e.target).trigger('change'); // for IE
                });
            };

            // Add new therapeutic implication
            $scope.addTrial = function(trials, newTrial, trialsReview, trialsUuid) {
                if (trials && newTrial) {
                    if (trials.indexOf(newTrial) === -1) {
                        if (newTrial.match(/NCT[0-9]+/ig)) {
                            if (trialsReview && !trialsReview.get('lastReviewed')) {
                                trialsReview.set('lastReviewed', trials.asArray().slice(0));
                                setReview(trialsUuid, true);
                            }
                            trialsReview.set('updatedBy', User.name);
                            trialsReview.set('updateTime', new Date().toLocaleString());
                            trials.push(newTrial);
                        } else {
                            dialogs.notify('Warning', 'Please check your trial ID format. (e.g. NCT01562899)');
                        }
                    } else {
                        dialogs.notify('Warning', 'Trial exists.');
                    }
                }
            };
            $scope.removeTrial = function(trials, index, trialsReview, trialsUuid) {
                if(trialsReview && !trialsReview.get('lastReviewed')) {
                    trialsReview.set('lastReviewed', trials.asArray().slice(0));
                    setReview(trialsUuid, true);
                }
                trialsReview.set('updatedBy', User.name);
                trialsReview.set('updateTime', new Date().toLocaleString());
                trials.remove(index);
            };

            $scope.addVUSItem = function(newVUSName, newVUSTime) {
                if (newVUSName) {
                    var notExist = true;
                    newVUSName = newVUSName.trim();
                    $scope.gene.mutations.asArray().forEach(function(e, i) {
                        if (e.name.getText().trim().toLowerCase() === newVUSName.toLowerCase()) {
                            notExist = false;
                        }
                    });

                    if (notExist && !containVariantInVUS(newVUSName)) {
                        $scope.realtimeDocument.getModel().beginCompoundOperation();
                        var vus = $scope.realtimeDocument.getModel().create(OncoKB.VUSItem);
                        var timeStamp = $scope.realtimeDocument.getModel().create(OncoKB.TimeStampWithCurator);

                        if (!newVUSTime) {
                            newVUSTime = new Date().getTime().toString();
                        }

                        timeStamp.value.setText(newVUSTime);
                        timeStamp.by.name.setText(User.name);
                        timeStamp.by.email.setText(User.email);
                        vus.name.setText(newVUSName);
                        vus.time.push(timeStamp);
                        $scope.vus.push(vus);
                        $scope.realtimeDocument.getModel().endCompoundOperation();
                        var tempMessage = User.name + ' tried to add ' + newVUSName + ' at ' + new Date().toLocaleString();
                        $scope.vusUpdate(tempMessage);
                    } else {
                        dialogs.notify('Warning', 'Variant exists.');
                    }
                }
            };

            $scope.cleanTrial = function(trials) {
                var cleanTrials = {};
                trials.asArray().forEach(function(e, index) {
                    if (cleanTrials.hasOwnProperty(e)) {
                        cleanTrials[e].push(index);
                    } else {
                        cleanTrials[e] = [];
                    }
                });
                for (var key in cleanTrials) {
                    if (cleanTrials[key].length > 0) {
                        cleanTrials[key].forEach(function() {
                            trials.removeValue(key);
                        });
                    }
                }
                console.log(cleanTrials);
            };

            $scope.addTrialStr = function(trials) {
                if (trials && this.trialsStr) {
                    var _trials = this.trialsStr.split(/\s+/);
                    _trials.forEach(function(e) {
                        if (trials.indexOf(e) === -1) {
                            trials.push(e);
                        }
                    });
                    this.trialsStr = '';
                }
            };

            $scope.checkScope = function() {
                console.log($scope.gene);
                // console.log($scope.gene.mutations.get(0).tumors.get(0));
                console.log($scope.geneStatus);

                console.log('Num of watchers: ' + checkNumWatchers());
                console.log($scope.gene.status_timeStamp.get('lastUpdate').value);

                $scope.gene.mutations.asArray().forEach(function(e) {
                    console.log('------------------');
                    // console.log(e);
                    // console.log(e.shortSummary);
                    // console.log(e.shortSummary_eStatus);
                    // console.log(e.shortSummary_eStatus.get('curated'));
                    // console.log(e.effect);
                    // console.log(e.oncogenic);
                    // console.log(e.description);]
                    e.tumors.asArray().forEach(function(tumortype) {
                        console.log(tumortype);
                        // tumortype.cancerTypes.asArray().forEach(function(cancerType) {
                        //     console.log(cancerType);
                        // })
                    });
                    console.log('------------------');
                });
            };

            $scope.getCancerTypesName = function(cancerTypes) {
                var list = [];
                cancerTypes.asArray().forEach(function(cancerType) {
                    if (cancerType.subtype.length > 0) {
                        var str = cancerType.subtype.getText();
                        // if (cancerType.oncoTreeCode.length > 0) {
                        //     str += '(' + cancerType.oncoTreeCode + ')';
                        // }
                        list.push(str);
                    } else if (cancerType.cancerType.length > 0) {
                        list.push(cancerType.cancerType.getText());
                    }
                });
                return list.join(', ');
            };

            $scope.getLastReviewedCancerTypesName = mainUtils.getLastReviewedCancerTypesName;

            function getNewCancerTypesName(cancerTypes) {
                var list = [];
                _.each(cancerTypes, function(cancerType) {
                    if (cancerType.subtype && cancerType.subtype.name && cancerType.subtype.name.length > 0) {
                        var str = cancerType.subtype.name;
                        if (cancerType.subtype.code.length > 0) {
                            str += '(' + cancerType.subtype.code + ')';
                        }
                        list.push(str);
                    } else if (cancerType.mainType && cancerType.mainType.name && cancerType.mainType.name.length > 0) {
                        list.push(cancerType.mainType.name);
                    }
                });
                return list.join(', ');
            }

            $scope.updateGeneColor = function() {
                if ($scope.gene && $scope.document && $scope.document.hasOwnProperty('modifiedDate')) {
                    if (new Date($scope.document.modifiedDate).getTime() > Number($scope.gene.status_timeStamp.get('lastUpdate').value.text)) {
                        return 'red';
                    }
                    return 'black';
                }
                return 'black';
            };

            $scope.remove = function(event, type, mutation, tumor, ti, treatment) {
                $scope.stopCollopse(event);
                var dlg = dialogs.confirm('Confirmation', 'Are you sure you want to delete this entry?');
                dlg.result.then(function() {
                    var obj;
                    switch(type) {
                    case 'mutation':
                        obj = mutation;
                        break;
                    case 'tumor':
                        obj = tumor;
                        break;
                    case 'treatment':
                        obj = treatment;
                        break;
                    }
                    if(isObsoleted(obj)) {
                        removeModel(type, mutation, tumor, ti, treatment, []);
                    } else {
                        obj.name_review.set('removed', true);
                        obj.name_review.set('updatedBy', User.name);
                        obj.name_review.set('updateTime', new Date().toLocaleString());
                        setReview(obj.name_uuid, true);
                    }
                }, function() {
                });
            };
            function collectUUIDs(type, obj, uuids) {
                if (type === 'mutation') {
                    uuids.push(obj.name_uuid.getText());
                    uuids.push(obj.oncogenic_uuid.getText());
                    uuids.push(obj.shortSummary_uuid.getText());
                    uuids.push(obj.summary_uuid.getText());
                    uuids.push(obj.effect_uuid.getText());
                    uuids.push(obj.description_uuid.getText());
                    _.each(obj.tumors.asArray(), function(tumor) {
                        collectUUIDs('tumor', tumor, uuids);
                    });
                }
                if (type === 'tumor') {
                    uuids.push(obj.name_uuid.getText());
                    uuids.push(obj.summary_uuid.getText());
                    uuids.push(obj.prevalence_uuid.getText());
                    uuids.push(obj.progImp_uuid.getText());
                    uuids.push(obj.trials_uuid.getText());
                    uuids.push(obj.nccn.therapy_uuid.getText());
                    uuids.push(obj.nccn.disease_uuid.getText());
                    uuids.push(obj.nccn.version_uuid.getText());
                    uuids.push(obj.nccn.description_uuid.getText());
                    _.each(obj.TI.asArray(), function(ti) {
                        collectUUIDs('TI', ti, uuids);
                    });
                }
                if(type === 'TI') {
                    uuids.push(obj.description_uuid.getText());
                    _.each(obj.treatments.asArray(), function(treatment) {
                        collectUUIDs('treatment', treatment, uuids);
                    });
                }
                if (type === 'treatment') {
                    uuids.push(obj.name_uuid.getText());
                    uuids.push(obj.level_uuid.getText());
                    uuids.push(obj.indication_uuid.getText());
                    uuids.push(obj.description_uuid.getText());
                }
                return uuids;
            }
            $scope.confirmDelete = function(event, type, mutation, tumor, ti, treatment) {
                $scope.stopCollopse(event);
                var obj;
                switch(type) {
                case 'mutation':
                    obj = mutation;
                    break;
                case 'tumor':
                    obj = tumor;
                    break;
                case 'treatment':
                    obj = treatment;
                    break;
                }
                var uuids = collectUUIDs(type, obj, []);
                if ($scope.status.isDesiredGene && !isObsoleted(obj)) {
                    // make the api call to delete evidences
                    DatabaseConnector.deleteEvidences(uuids, function(result) {
                        removeModel(type, mutation, tumor, ti, treatment, uuids);
                    }, function(error) {
                        dialogs.error('Error', 'Failed to update to database! Please contact the developer.');
                    });
                } else {
                    removeModel(type, mutation, tumor, ti, treatment, uuids);
                }
            };
            function removeModel(type, mutation, tumor, ti, treatment, uuids) {
                var indices = getIndex(mutation, tumor, ti, treatment);
                switch(type) {
                case 'mutation':
                    delete $scope.geneStatus[indices[0]];
                    $scope.geneStatus = migrateGeneStatusPosition($scope.geneStatus, indices[0]);
                    $scope.gene.mutations.removeValue(mutation);
                    break;
                case 'tumor':
                    delete $scope.geneStatus[indices[0]][indices[1]];
                    $scope.geneStatus[indices[0]] = migrateGeneStatusPosition($scope.geneStatus[indices[0]], indices[1]);
                    mutation.tumors.removeValue(tumor);
                    break;
                case 'treatment':
                    delete $scope.geneStatus[indices[0]][indices[1]][indices[2]][indices[3]];
                    $scope.geneStatus[indices[0]][indices[1]][indices[2]] = migrateGeneStatusPosition($scope.geneStatus[indices[0]][indices[1]][indices[2]], indices[3]);
                    ti.treatments.removeValue(treatment);
                    break;
                }
                _.each(uuids, function(uuid) {
                    if(uuid) {
                        $rootScope.geneMetaData.delete(uuid);
                    }
                });
            }

            function getIndex(mutation, tumor, ti, treatment) {
                var result = [-1, -1, -1, -1]; // Always return four elements array, standing for mutationIndex, tumorIndex, therapyCategoryIndex and treatmentIndex
                if (mutation) {
                    result[0] = $scope.gene.mutations.indexOf(mutation);
                    if (tumor) {
                        result[1] = mutation.tumors.indexOf(tumor);
                        if (ti) {
                            result[2] = tumor.TI.indexOf(ti);
                            if (treatment) {
                                result[3] = ti.treatments.indexOf(treatment);
                            }
                        }
                    }
                }
                return result;
            }

            $scope.initGeneStatus = function(mutation, tumor, ti, treatment) {
                var objects = [mutation, tumor, ti, treatment];
                var indices = getIndex(mutation, tumor, ti, treatment);
                $scope.geneStatus = loopInitGeneStatus(objects, $scope.geneStatus, indices, 0);
            };

            $scope.checkGeneStatus = function(mutation, tumor, ti, treatment, key, statusType) {
                if (!_.isString(statusType)) {
                    statusType = 'isOpen';
                }
                var indices = getIndex(mutation, tumor, ti, treatment);
                var result = loopCheckGeneStatus(false, $scope.geneStatus, indices, 0, key, statusType);
                return result;
            };

            $scope.getGeneStatusItem = function(mutation, tumor, ti, treatment, key) {
                var indices = getIndex(mutation, tumor, ti, treatment);
                var result = loopGetGeneStatusItem(null, $scope.geneStatus, indices, 0, key);
                return result;
            };

            function loopCheckGeneStatus(status, geneStatus, indices, index, key, statusType) {
                var strIndex = indices[index].toString();
                if (geneStatus.hasOwnProperty(strIndex)) {
                    var nextIndex = index + 1;
                    if (!_.isNumber(indices[nextIndex]) || indices[nextIndex] === -1) {
                        if (key) {
                            return geneStatus[strIndex][key] ? geneStatus[strIndex][key][statusType] : false;
                        }
                        return geneStatus[strIndex][statusType];
                    }
                    status = loopCheckGeneStatus(status, geneStatus[strIndex], indices, ++index, key, statusType);
                }
                return status;
            }

            function loopGetGeneStatusItem(ref, geneStatus, indices, index, key) {
                var strIndex = indices[index].toString();
                if (geneStatus.hasOwnProperty(strIndex)) {
                    var nextIndex = index + 1;
                    if (!_.isNumber(indices[nextIndex]) || indices[nextIndex] === -1) {
                        if (key) {
                            return geneStatus[strIndex][key] ? geneStatus[strIndex][key] : null;
                        }
                        return geneStatus[strIndex];
                    }
                    ref = loopGetGeneStatusItem(ref, geneStatus[strIndex], indices, ++index, key);
                }
                return ref;
            }

            function loopInitGeneStatus(objects, geneStatus, indices, index) {
                var defaultIsOpen = false;
                if (index < indices.length && indices[index] !== -1) {
                    var strIndex = indices[index].toString();
                    if (!geneStatus.hasOwnProperty(strIndex)) {
                        geneStatus[strIndex] = new GeneStatusSingleton(defaultIsOpen, $scope.status.hideAllEmpty);
                        if (index === 0) {
                            geneStatus[strIndex].oncogenic = new GeneStatusSingleton(defaultIsOpen, $scope.status.hideAllEmpty);
                            geneStatus[strIndex].mutationEffect = new GeneStatusSingleton(defaultIsOpen, $scope.status.hideAllEmpty);
                        } else if (index === 1) {
                            geneStatus[strIndex].prevalence = new GeneStatusSingleton(defaultIsOpen, $scope.status.hideAllEmpty);
                            geneStatus[strIndex].progImp = new GeneStatusSingleton(defaultIsOpen, $scope.status.hideAllEmpty);
                            geneStatus[strIndex].nccn = new GeneStatusSingleton(defaultIsOpen, $scope.status.hideAllEmpty);
                            geneStatus[strIndex].trials = new GeneStatusSingleton(defaultIsOpen, $scope.status.hideAllEmpty);
                        }
                    }
                    geneStatus[strIndex] = loopInitGeneStatus(objects, geneStatus[strIndex], indices, ++index);
                }
                return geneStatus;
            }

            $scope.cancelDelete = function(event, type, mutation, tumor, ti, treatment) {
                $scope.stopCollopse(event);
                var dlg = dialogs.confirm('Reminder', 'Are you sure you want to reject this change?');
                dlg.result.then(function() {
                    var obj;
                    switch (type) {
                    case 'mutation':
                        obj = mutation;
                        break;
                    case 'tumor':
                        obj = tumor;
                        break;
                    case 'treatment':
                        obj = treatment;
                        break;
                    }
                    obj.name_review.set('removed', false);
                    obj.name_review.set('updatedBy', null);
                    obj.name_review.set('updateTime', null);
                    obj.name_review.set('action', 'rejected');
                    setReview(obj.name_uuid, false);
                });
            };

            $scope.commentClick = function(event) {
                $scope.stopCollopse(event);
            };

            function fetchResults(data) {
                var PMIDs = [];
                var abstracts = [];
                _.each(data, function(item) {
                    if (item.type === 'pmid') {
                        PMIDs.push(item.id);
                    } else if (item.type === 'abstract') {
                        abstracts.push(item.id);
                    }
                });
                PMIDs.sort();
                abstracts.sort();
                return {PMIDs: PMIDs, abstracts: abstracts};
            }

            $scope.getAllCitations = function() {
                var results = [];
                var geneData = JSON.stringify(stringUtils.getGeneData(this.gene, true, true, true));
                results = fetchResults(FindRegex.result(geneData));
                var annotationPMIDs = results.PMIDs;
                var annotationAbstracts = results.abstracts;

                var vusData = JSON.stringify(stringUtils.getVUSFullData(this.vus));
                results = fetchResults(FindRegex.result(vusData));
                var vusPMIDs = results.PMIDs;
                var vusAbstracts = results.abstracts;
                var hasAnnotation = annotationPMIDs.length + annotationAbstracts.length > 0;
                var hasVUS = vusPMIDs.length + vusAbstracts.length > 0;

                // we only seperate citations information to tabs when both annotation and vus citations exist and there are too much info to fit in one tab
                var tabFlag = hasAnnotation && hasVUS && (annotationPMIDs.length > 80 || annotationAbstracts.length > 10 || vusPMIDs.length > 80 || vusAbstracts.length > 10);
                var messageContent = [];
                if (!hasAnnotation && !hasVUS) {
                    messageContent.push('No information available!');
                } else if (tabFlag) {
                    messageContent.push('<ul class="nav nav-tabs">');
                    if (hasAnnotation) {
                        messageContent.push('<li class="active"><a data-toggle="tab" href="#home"><h4>Annotation</h4></a></li>');
                    }
                    if (hasVUS) {
                        messageContent.push('<li><a data-toggle="tab" href="#menu1"><h4>VUS</h4></a></li>');
                    }
                    messageContent.push('</ul><div class="tab-content">');
                    if (hasAnnotation) {
                        messageContent.push('<div id="home" class="tab-pane fade in active"><h4>PMIDs (' + annotationPMIDs.length + ')</h4><p>' + annotationPMIDs.join(', ') + '</p>');
                        if (annotationAbstracts.length > 0) {
                            messageContent.push('<h4>Abstracts (' + annotationAbstracts.length + ')</h4><p>' + annotationAbstracts.join(', ') + '</p>');
                        }
                        messageContent.push('</div>');
                    }
                    if (hasVUS) {
                        messageContent.push('<div id="menu1" class="tab-pane fade"><h4>PMIDs (' + vusPMIDs.length + ')</h4><p>' + vusPMIDs.join(', ') + '</p>');
                        if (vusAbstracts.length > 0) {
                            messageContent.push('<h4>Abstracts (' + vusAbstracts.length + ')</h4><p>' + vusAbstracts.join(', ') + '</p>');
                        }
                        messageContent.push('</div>');
                    }
                    messageContent.push('</div>');
                } else {
                    if (hasAnnotation) {
                        messageContent.push('<h3 style="color:black">Annotation</h3>');
                        if (annotationPMIDs.length > 0) {
                            messageContent.push('<h4>PMIDs (' + annotationPMIDs.length + ')</h4><p>' + annotationPMIDs.join(', ') + '</p>');
                        }
                        if (annotationAbstracts.length > 0) {
                            messageContent.push('<h4>Abstracts (' + annotationAbstracts.length + ')</h4><p>' + annotationAbstracts.join(', ') + '</p>');
                        }
                    }

                    if (hasVUS) {
                        messageContent.push('<hr/><h3 style="color:black">VUS</h3>');
                        if (vusPMIDs.length > 0) {
                            messageContent.push('<h4>PMIDs (' + vusPMIDs.length + ')</h4><p>' + vusPMIDs.join(', ') + '</p>');
                        }
                        if (vusAbstracts.length > 0) {
                            messageContent.push('<h4>Abstracts (' + vusAbstracts.length + ')</h4><p>' + vusAbstracts.join(', ') + '</p>');
                        }
                    }
                }
                dialogs.notify('All Citations', messageContent.join(''), {size: 'lg'});
            };
            $scope.curatorsName = function() {
                return this.gene.curators.asArray().map(function(d) {
                    return d.name;
                }).join(', ');
            };

            $scope.curatorsEmail = function() {
                return this.gene.curators.asArray().map(function(d) {
                    return d.email;
                }).join(', ');
            };

            $scope.removeCurator = function(index) {
                $scope.gene.curators.remove(index);
            };

            $scope.checkTI = function(TI, status, type) {
                var _status = TI.types.get('status').toString();
                var _type = TI.types.get('type').toString();
                status = status.toString();
                type = type.toString();
                if (_status === status && _type === type) {
                    return true;
                }
                return false;
            };

            $scope.mutationEffectChanged = function(mutationEffect) {
                mutationEffect.addOn.setText('');
            };

            $scope.move = function(driveList, index, moveIndex, event) {
                var tmpStatus;
                var moveStatusIndex;
                var indexes = [];
                var geneStatus = angular.copy($scope.geneStatus);
                var key;
                var numKey;
                $scope.stopCollopse(event);

                index = parseInt(index, 10);
                moveIndex = parseInt(moveIndex, 10);

                if (moveIndex <= index) {
                    if (moveIndex <= 0) {
                        moveIndex = moveStatusIndex = 0;
                    } else {
                        moveIndex = moveStatusIndex = moveIndex - 1;
                    }
                } else {
                    moveStatusIndex = moveIndex - 1;
                }

                if (moveIndex > driveList.length) {
                    moveIndex = driveList.length;
                    moveStatusIndex = moveIndex - 1;
                }

                tmpStatus = angular.copy($scope.geneStatus[index]);

                if (index < moveStatusIndex) {
                    for (key in geneStatus) {
                        if (!isNaN(key)) {
                            numKey = Number(key);
                            if (numKey <= moveStatusIndex && numKey > index) {
                                indexes.push(numKey);
                            }
                        }
                    }
                    indexes.sort(function(a, b) {
                        return a - b;
                    }).forEach(function(e) {
                        geneStatus[e - 1] = geneStatus[e];
                    });
                } else {
                    for (key in geneStatus) {
                        if (!isNaN(key)) {
                            numKey = Number(key);
                            if (numKey >= moveStatusIndex && numKey < index) {
                                indexes.push(numKey);
                            }
                        }
                    }
                    indexes.sort(function(a, b) {
                        return b - a;
                    }).forEach(function(e) {
                        geneStatus[e + 1] = geneStatus[e];
                    });
                }

                geneStatus[moveStatusIndex] = tmpStatus;

                $scope.geneStatus = geneStatus;

                driveList.move(index, moveIndex);
            };

            $scope.stopCollopse = function(event) {
                if (event.stopPropagation) {
                    event.stopPropagation();
                }
                if (event.preventDefault && event.type !== 'keypress') {
                    event.preventDefault();
                }
            };

            $scope.generatePDF = function() {
                jspdf.create(stringUtils.getGeneData(this.gene, true, true, true));
            };

            $scope.isOpenFunc = function(type) {
                var processKey = '';
                var targetStatus = '';
                var geneStatus = $scope.geneStatus;
                var specialEscapeKeys = ['isOpen', 'hideEmpty'];
                var flag;
                if (type === 'expand') {
                    targetStatus = true;
                    processKey = 'isOpen';
                } else if (type === 'collapse') {
                    targetStatus = false;
                    processKey = 'isOpen';
                } else if (type === 'hideEmpty') {
                    targetStatus = true;
                    processKey = 'hideEmpty';
                } else if (type === 'showEmpty') {
                    targetStatus = false;
                    processKey = 'hideEmpty';
                }

                // for: mutation
                for (var key in geneStatus) {
                    if (!isNaN(key)) {
                        geneStatus[key][processKey] = targetStatus;
                    }

                    // for: tumor type
                    for (var _key in geneStatus[key]) {
                        // for: therapeutic implications
                        if (specialEscapeKeys.indexOf(_key) === -1) {
                            flag = targetStatus;
                            if (isNaN(_key) && flag) {
                                if (processKey === 'isOpen') {
                                    flag = $scope.gene.mutations.get(Number(key))[_key].text ? targetStatus : false;
                                } else {
                                    flag = targetStatus;
                                }
                            }
                            geneStatus[key][_key][processKey] = flag;

                            for (var __key in geneStatus[key][_key]) {
                                if (geneStatus[key][_key].hasOwnProperty(__key)) {
                                    flag = targetStatus;
                                    if (specialEscapeKeys.indexOf(__key) === -1) {
                                        if (isNaN(__key)) {
                                            if (processKey === 'isOpen') {
                                                if (__key === 'nccn') {
                                                    flag = $scope.hasNccn($scope.gene.mutations.get(Number(key)).tumors.get(Number(_key)).nccn) ? targetStatus : false;
                                                } else if (__key === 'trials') {
                                                    flag = $scope.gene.mutations.get(Number(key)).tumors.get(Number(_key)).trials.length > 0 ? targetStatus : false;
                                                } else {
                                                    flag = $scope.gene.mutations.get(Number(key)).tumors.get(Number(_key))[__key].text ? targetStatus : false;
                                                }
                                            } else {
                                                flag = targetStatus;
                                            }
                                            geneStatus[key][_key][__key][processKey] = flag;
                                        } else if (!isNaN(__key)) {
                                            if ($scope.gene.mutations.get(Number(key)).tumors.get(Number(_key)).TI.get(Number(__key)).treatments.length > 0) {
                                                // for: treatments
                                                geneStatus[key][_key][__key][processKey] = flag;
                                                for (var ___key in geneStatus[key][_key][__key]) {
                                                    if (specialEscapeKeys.indexOf(___key) === -1) {
                                                        geneStatus[key][_key][__key][___key][processKey] = flag;
                                                    }
                                                }
                                            } else if ($scope.gene.mutations.get(Number(key)).tumors.get(Number(_key)).TI.get(Number(__key)).description.text) {
                                                geneStatus[key][_key][__key][processKey] = flag;
                                            } else if (processKey === 'isOpen') {
                                                geneStatus[key][_key][__key].isOpen = false;
                                            } else {
                                                geneStatus[key][_key][__key][processKey] = flag;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                $scope.geneStatus = geneStatus;
            };
            /* eslint no-unused-vars: 0*/
            $scope.changeIsOpen = function(target) {
                target = !target;
            };

            $scope.checkEmpty = function(mutation, type) {
                if (type === 'mutationEffect') {
                    if (mutation.effect.value.text === '' && mutation.description.text === '' && mutation.short.text === '') {
                        return true;
                    }
                    return false;
                } else if (type === 'oncogenicity') {
                    if ((mutation.oncogenic.text === '' || mutation.oncogenic.text === 'false') && mutation.shortSummary.text === '') {
                        return true;
                    }
                    return false;
                }
                return false;
            };

            $scope.hasNccn = function(nccn) {
                if (nccn) {
                    if (nccn.disease.text && nccn.disease.text !== 'NA') {
                        return true;
                    }
                }
                return false;
            };

            $scope.curatedIconClick = function(event, status) {
                $scope.stopCollopse(event);
                status.set('curated', !status.get('curated'));
            };

            $scope.mutationNameEditable = function(mutationName) {
                return $scope.fileEditable && !($scope.userRole !== 8 &&
                    $scope.suggestedMutations.indexOf(mutationName) !== -1);
            };

            // Calculate number of 'number' elements within the object
            function getNoNKeys(object) {
                var count = 0;
                for (var key in object) {
                    if (!isNaN(key)) {
                        count++;
                    }
                }
                return count;
            }

            // Only do the simple check wheter the numebr of array has been changed.
            // It's a little triky to monitor all content.
            function regenerateGeneStatus() {
                var geneStatus = {};
                var mutationKeys = ['oncogenic'];
                var tumorKeys = ['prevalence', 'progImp', 'nccn', 'trials'];

                var changeMutation = false;

                if ($scope.gene.mutations.length !== getNoNKeys($scope.geneStatus)) {
                    changeMutation = true;
                }
                $scope.gene.mutations.asArray().forEach(function(mutation, mutationIndex) {
                    if (changeMutation) {
                        geneStatus[mutationIndex] = $.extend($scope.geneStatus[mutationIndex], new GeneStatusSingleton());
                        mutationKeys.forEach(function(key) {
                            if (mutation[key]) {
                                geneStatus[mutationIndex][key] = new GeneStatusSingleton();
                            }
                        });
                    } else {
                        geneStatus[mutationIndex] = $scope.geneStatus[mutationIndex];
                    }

                    if (mutation.tumors.length > 0) {
                        var changeTT = false;

                        if (mutation.tumors.length !== getNoNKeys($scope.geneStatus[mutationIndex])) {
                            changeTT = true;
                        }
                        mutation.tumors.asArray().forEach(function(tumor, tumorIndex) {
                            if (changeTT) {
                                geneStatus[mutationIndex][tumorIndex] = $.extend($scope.geneStatus[mutationIndex][tumorIndex], new GeneStatusSingleton());
                            }
                            tumorKeys.forEach(function(key) {
                                if (tumor[key] && changeTT) {
                                    geneStatus[mutationIndex][tumorIndex][key] = new GeneStatusSingleton();
                                }
                                tumor.TI.asArray(function(therapyType, therapyTypeIndex) {
                                    geneStatus[mutationIndex][tumorIndex][therapyTypeIndex] = $scope.geneStatus[mutationIndex][tumorIndex][therapyTypeIndex];
                                    var changeT = false;

                                    if (therapyType.treatment.length !== getNoNKeys($scope.geneStatus[mutationIndex][tumorIndex][therapyTypeIndex])) {
                                        changeT = true;
                                    }
                                    therapyType.treatments.asArray(function(treatment, treatmentIndex) {
                                        geneStatus[mutationIndex][tumorIndex][therapyTypeIndex][treatmentIndex] = changeT ? new GeneStatusSingleton() : $scope.geneStatus[mutationIndex][tumorIndex][therapyTypeIndex][treatmentIndex];
                                    });
                                });
                            });
                        });
                    }
                });
                $scope.geneStatus = geneStatus;
            }

            function migrateGeneStatusPosition(object, indexRemoved) {
                if (angular.isNumber(indexRemoved)) {
                    var indexes = [];
                    for (var key in object) {
                        if (!isNaN(key) && Number(key) > indexRemoved) {
                            indexes.push(Number(key));
                        }
                    }

                    indexes.sort(function(a, b) {
                        return a - b;
                    }).forEach(function(e) {
                        object[e - 1] = object[e];
                    });

                    delete object[indexes.pop()];
                    return object;
                }
                return false;
            }

            function checkNumWatchers() {
                var root = angular.element(document.getElementsByTagName('body'));

                var watchers = [];

                var f = function(element) {
                    angular.forEach(['$scope', '$isolateScope'], function(scopeProperty) {
                        if (element.data() && element.data().hasOwnProperty(scopeProperty)) {
                            angular.forEach(element.data()[scopeProperty].$$watchers, function(watcher) {
                                watchers.push(watcher);
                            });
                        }
                    });

                    angular.forEach(element.children(), function(childElement) {
                        f(angular.element(childElement));
                    });
                };

                f(root);

                // Remove duplicate watchers
                var watchersWithoutDuplicates = [];
                angular.forEach(watchers, function(item) {
                    if (watchersWithoutDuplicates.indexOf(item) < 0) {
                        watchersWithoutDuplicates.push(item);
                    }
                });

                console.log(watchersWithoutDuplicates);

                return watchersWithoutDuplicates.length;
            }



            function getSuggestedMutations() {
                var defaultPlaceHolder = 'No suggestion found. Please curate according to literature.';
                DatabaseConnector.getSuggestedVariants()
                    .then(function(resp) {
                        if (_.isArray(resp) && resp.length > 0) {
                            $scope.suggestedMutations = resp;
                        } else {
                            $scope.suggestedMutations = [];
                        }
                    }, function() {
                        $scope.suggestedMutations = [];
                    })
                    .finally(function() {
                        if ($scope.suggestedMutations.length === 0) {
                            $scope.addMutationPlaceholder = defaultPlaceHolder;
                        }
                    });
            }
            function loadMetaFile(callback) {
                if(!$rootScope.metaData) {
                    storage.retrieveMeta().then(function(result) {
                        if (result && (result.error || !_.isArray(result) || result.length === 0)) {
                            dialogs.error('Error', 'Fail to retrieve meta file! Please stop editing and contact the developer!');
                            var sendTo = 'dev.oncokb@gmail.com';
                            var subject = 'Fail to retrieve meta file';
                            var content;
                            if(_.isArray(result) && result.length === 0) {
                                content = 'There is no meta file inside the Meta folder';
                            } else {
                                content = 'System error is ' + JSON.stringify(result.error);
                            }
                            mainUtils.sendEmail(sendTo, subject, content);
                            callback();
                        } else {
                            storage.getMetaRealtimeDocument(result[0].id).then(function(metaRealtime) {
                                if (metaRealtime && metaRealtime.error) {
                                    dialogs.error('Error', 'Fail to get meta document! Please stop editing and contact the developer!');
                                    $scope.fileEditable = false;
                                    callback();
                                } else {
                                    $rootScope.metaRealtime = metaRealtime;
                                    $rootScope.metaModel = metaRealtime.getModel();
                                    $rootScope.metaData = metaRealtime.getModel().getRoot().get('review');
                                    assignMeta(callback);
                                }
                            });
                        }
                    });
                } else {
                    assignMeta(callback);
                }

            }
            function assignMeta(callback) {
                if (!$rootScope.metaData.get($scope.fileTitle)) {
                    var tempMap = $rootScope.metaModel.createMap();
                    $rootScope.metaData.set($scope.fileTitle, tempMap);
                }
                $rootScope.geneMetaData = $rootScope.metaData.get($scope.fileTitle);
                callback();
            }

            function bindDocEvents() {
                $scope.realtimeDocument.addEventListener(gapi.drive.realtime.EventType.COLLABORATOR_JOINED, displayCollaboratorEvent);
                $scope.realtimeDocument.addEventListener(gapi.drive.realtime.EventType.COLLABORATOR_LEFT, displayCollaboratorEvent);
                $scope.realtimeDocument.addEventListener(gapi.drive.realtime.EventType.DOCUMENT_SAVE_STATE_CHANGED, saveStateChangedEvent);
                $scope.model.addEventListener(gapi.drive.realtime.EventType.UNDO_REDO_STATE_CHANGED, onUndoStateChanged);
                $scope.gene.addEventListener(gapi.drive.realtime.EventType.VALUE_CHANGED, valueChangedEvent);
                $rootScope.metaRealtime.addEventListener(gapi.drive.realtime.EventType.DOCUMENT_SAVE_STATE_CHANGED, saveMetaChangedEvent);
            }

            function saveStateChangedEvent(evt) {
                // set gene document to readable only when it s in review
                if (underOthersReview()) {
                    $scope.$emit('interruptedDueToOtherReview');
                } else {
                    // If document is editable again, need to notify the user.
                    if (!$scope.fileEditable && $scope.document.editable) {
                        dialogs.notify('Notification',
                            'You can now continue editing the document. Thanks.');
                    }
                    $scope.fileEditable = $scope.document.editable;
                }
                if ($scope.$$phase) {
                    updateDocStatus(evt);
                } else {
                    $scope.$apply(function() {
                        updateDocStatus(evt);
                    });
                }
            }

            function updateDocStatus(evt) {
                if (evt.isSaving) {
                    documentSaving();
                } else if (!evt.isSaving && !evt.currentTarget.isClosed) {
                    documentSaved();
                } else {
                    documentClosed();
                }
            }

            function saveMetaChangedEvent(evt) {
                if ($rootScope.$$phase) {
                    updateMetaDocStatus(evt);
                } else {
                    $rootScope.$apply(function() {
                        updateMetaDocStatus(evt);
                    });
                }
            }

            function updateMetaDocStatus(evt) {
                if (evt.isSaving) {
                    documentSaving('meta');
                } else if (!evt.isSaving && !evt.currentTarget.isClosed) {
                    documentSaved('meta');
                } else {
                    documentClosed('meta');
                }
            }

            function afterCreateGeneModel() {
                var file = Documents.get({title: $scope.fileTitle});
                var timeStamp;
                file = file[0];
                if (!$scope.gene.status_timeStamp.has('lastEdit')) {
                    $scope.realtimeDocument.getModel().beginCompoundOperation();
                    timeStamp = $scope.realtimeDocument.getModel().create('TimeStamp');
                    timeStamp.value.setText(new Date().getTime().toString());
                    timeStamp.by.setText(Users.getMe().name);
                    $scope.gene.status_timeStamp.set('lastEdit', timeStamp);
                    $scope.realtimeDocument.getModel().endCompoundOperation();
                }
                if (!$scope.gene.status_timeStamp.has('lastUpdate')) {
                    $scope.realtimeDocument.getModel().beginCompoundOperation();
                    timeStamp = $scope.realtimeDocument.getModel().create('TimeStamp');
                    timeStamp.value.setText(new Date().getTime().toString());
                    timeStamp.by.setText(Users.getMe().name);
                    $scope.gene.status_timeStamp.set('lastUpdate', timeStamp);
                    $scope.realtimeDocument.getModel().endCompoundOperation();
                }
                $scope.document = file;
                $scope.fileEditable = file.editable;
                addVUS();
                $scope.status.rendering = false;
                displayAllCollaborators($scope.realtimeDocument, bindDocEvents);

                if (underReview()) {
                    // This will only happen if the currentReviewer is not empty
                    $scope.fileEditable = false;
                }
                // Add timeout until the collaborator join event is triggered.
                $timeout(function() {
                    if (underOthersReview()) {
                        $scope.$emit('interruptedDueToOtherReview');
                    } else if(underReview()) {
                        // if no other is reviewing the current document,
                        // need to reset the document to initial state.
                        $scope.exitReview();
                    }
                }, 2000);
            }

            function valueChangedEvent(evt) {
                console.log('valueChanged', evt);
                if ($scope.gene) {
                    var hasCurator = false;
                    if ($scope.gene.curators && angular.isArray($scope.gene.curators.asArray()) && $scope.gene.curators.asArray().length > 0) {
                        var _array = $scope.gene.curators.asArray();
                        for (var i = 0; i < _array.length; i++) {
                            if (_array[i].email.text === User.email) {
                                hasCurator = true;
                                break;
                            }
                        }

                        if (!hasCurator) {
                            $scope.realtimeDocument.getModel().beginCompoundOperation();
                            var __curator = $scope.realtimeDocument.getModel().create(OncoKB.Curator, User.name, User.email);
                            $scope.gene.curators.push(__curator);
                            $scope.realtimeDocument.getModel().endCompoundOperation();
                        }
                    } else {
                        $scope.realtimeDocument.getModel().beginCompoundOperation();
                        var _curator = $scope.realtimeDocument.getModel().create(OncoKB.Curator, User.name, User.email);
                        $scope.gene.curators.push(_curator);
                        $scope.realtimeDocument.getModel().endCompoundOperation();
                    }
                }
            }

            function displayCollaboratorEvent(evt) {
                switch (evt.type) {
                case 'collaborator_left':
                    removeCollaborator(evt.collaborator);
                    break;
                case 'collaborator_joined':
                    addCollaborator(evt.collaborator);
                    break;
                default:
                    console.info('Unknown event:', evt);
                    break;
                }
                $scope.$apply($scope.collaborators);
            }

            function underOthersReview() {
                var currentReviewer = $scope.gene.name_review.get('currentReviewer');
                if (currentReviewer) {
                    var _name = currentReviewer.getText();
                    if (_name &&
                        _name.toUpperCase() !== User.name.toUpperCase() &&
                        hasCollaborator(currentReviewer.getText())) {
                        return true;
                    }
                }
                return false;
            }

            function hasCollaborator(name) {
                var collaborators = $scope.realtimeDocument.getCollaborators();
                if (_.isArray(collaborators)) {
                    for (var i = 0; i < collaborators.length; i++) {
                        if (collaborators[i].displayName.toUpperCase() === name.toUpperCase()) {
                            return true;
                        }
                    }
                }
                return false;
            }

            function underReview() {
                var currentReviewer = $scope.gene.name_review.get('currentReviewer');
                if (currentReviewer && currentReviewer.getText()) {
                    return true;
                }
                return false;
            }

            function addCollaborator(user) {
                if (!$scope.collaborators.hasOwnProperty(user.userId)) {
                    $scope.collaborators[user.sessionId] = {};
                }
                $scope.collaborators[user.sessionId] = user;
            }

            function removeCollaborator(user) {
                if ($scope.collaborators.hasOwnProperty(user.sessionId)) {
                    delete $scope.collaborators[user.sessionId];
                } else {
                    console.log('Unknown collaborator:', user);
                }
            }

            function displayAllCollaborators(document, callback) {
                var collaborators = document.getCollaborators();
                var collaboratorCount = collaborators.length;
                var _user = {};
                for (var i = 0; i < collaboratorCount; i++) {
                    var user = collaborators[i];
                    if (!$scope.collaborators.hasOwnProperty(user.userId)) {
                        $scope.collaborators[user.sessionId] = {};
                    }
                    $scope.collaborators[user.sessionId] = user;
                    if (user.isMe) {
                        _user = user;
                    }
                }

                if (User.email === 'N/A') {
                    storage.getUserInfo(_user.userId).then(function(userInfo) {
                        User.name = userInfo.displayName;
                        if (angular.isArray(userInfo.emails)) {
                            if (userInfo.emails.length > 0) {
                                User.email = userInfo.emails[0].value;
                            } else {
                                User.email = 'N/A';
                            }
                        } else {
                            User.email = userInfo.emails;
                        }
                        callback();
                    });
                } else {
                    callback();
                }
            }

            function onUndoStateChanged(evt) {
                if (evt.canUndo) {
                    $scope.canUndo = true;
                } else {
                    $scope.canUndo = false;
                }
                if (evt.canRedo) {
                    $scope.canRedo = true;
                } else {
                    $scope.canRedo = false;
                }
            }

            function documentSaving(type) {
                $scope.docStatus.saving = true;
                $scope.docStatus.saved = false;
                $scope.docStatus.closed = false;

                if (type === 'meta') {
                    $scope.metaDocStatus.saved = false;
                }

                if ($rootScope.reviewMode) {
                    setReviewModeInterval();
                }
            }

            function documentSaved(type) {
                if (!$scope.docStatus.updateGene && type !== 'meta') {
                    $scope.gene.status_timeStamp.get('lastEdit').value.setText(new Date().getTime().toString());
                    $scope.gene.status_timeStamp.get('lastEdit').by.setText(Users.getMe().name);
                }
                $scope.docStatus.saving = false;
                $scope.docStatus.saved = true;
                $scope.docStatus.closed = false;
                $scope.docStatus.updateGene = false;

                if (type === 'meta') {
                    $scope.metaDocStatus.saved = true;
                }
            }

            function documentClosed(type) {
                $scope.docStatus.closed = true;
                $scope.docStatus.saving = false;
                $scope.docStatus.saved = false;
                $scope.fileEditable = false;

                if (type === 'meta') {
                    $scope.metaDocStatus.saved = false;
                }
            }

            function getOncoTreeMainTypes() {
                DatabaseConnector.getOncoTreeMainTypes()
                    .then(function(result) {
                        if (result.data) {
                            $scope.oncoTree.mainTypes = result.data;
                            $scope.oncoTree.mainTypes.push({
                                id: -1,
                                name: 'All Liquid Tumors'
                            });
                            $scope.oncoTree.mainTypes.push({
                                id: -2,
                                name: 'All Solid Tumors'
                            });
                            $scope.oncoTree.mainTypes.push({
                                id: -3,
                                name: 'All Tumors'
                            });
                            $scope.oncoTree.mainTypes.push({
                                id: -4,
                                name: 'Germline Disposition'
                            });
                            $scope.oncoTree.mainTypes.push({
                                id: -5,
                                name: 'All Pediatric Tumors'
                            });
                            $scope.oncoTree.mainTypes.push({
                                id: -5,
                                name: 'Other Tumor Types'
                            });
                            DatabaseConnector.getOncoTreeTumorTypesByMainTypes(_.map(result.data, function(mainType) {
                                return mainType.name;
                            })).then(function(data) {
                                if (_.isObject(data) && _.isArray(data.data)) {
                                    if (data.data.length === result.data.length) {
                                        var tumorTypes = {};
                                        var allTumorTypes = [];
                                        _.each(result.data, function(mainType, i) {
                                            tumorTypes[mainType.name] = data.data[i];
                                            allTumorTypes = _.union(allTumorTypes, data.data[i]);
                                        });
                                        $scope.oncoTree.tumorTypes = tumorTypes;
                                        $scope.oncoTree.allTumorTypes = allTumorTypes;
                                        $scope.meta = {
                                            newCancerTypes: [{
                                                mainType: '',
                                                subtype: '',
                                                oncoTreeTumorTypes: allTumorTypes
                                            }]
                                        };
                                    } else {
                                        console.error('The number of returned tumor types is not matched with number of main types.');
                                    }
                                }
                            }, function() {
                                // TODO: if OncoTree server returns error.
                            });
                        }
                    }, function(error) {
                        console.log(error);
                    });
            }

            function getLevels() {
                var desS = {
                    '': '',
                    '0': $rootScope.meta.levelsDesc['0'],
                    '1': $rootScope.meta.levelsDesc['1'],
                    '2A': $rootScope.meta.levelsDesc['2A'],
                    '2B': $rootScope.meta.levelsDesc['2B'],
                    '3A': $rootScope.meta.levelsDesc['3A'],
                    '3B': $rootScope.meta.levelsDesc['3B'],
                    '4': $rootScope.meta.levelsDesc['4']
                };

                var desR = {
                    '': '',
                    'R1': $rootScope.meta.levelsDesc.R1,
                    'R2': $rootScope.meta.levelsDesc.R2,
                    'R3': $rootScope.meta.levelsDesc.R3
                };

                var levels = {};

                var levelsCategories = {
                    SS: ['', '0', '1', '2A'],
                    SR: ['R1'],
                    IS: ['', '2B', '3A', '3B', '4'],
                    IR: ['R2', 'R3']
                };

                _.each(levelsCategories, function(item, key) {
                    levels[key] = [];
                    for (var i = 0; i < item.length; i++) {
                        var __datum = {};
                        __datum.label = item[i] + (item[i] === '' ? '' : ' - ') + ((['SS', 'IS'].indexOf(key) === -1) ? desR[item[i]] : desS[item[i]]);
                        __datum.value = item[i];
                        levels[key].push(__datum);
                    }
                });
                return levels;
            }

            function GeneStatusSingleton(isOpen, hideEmpty) {
                if (!_.isBoolean(isOpen)) {
                    isOpen = false;
                }
                if (!_.isBoolean(hideEmpty)) {
                    hideEmpty = false;
                }
                this.isOpen = isOpen;
                this.hideEmpty = hideEmpty;
            }

            function containVariantInVUS(variantName) {
                var size = $scope.vus.length;

                for (var i = 0; i < size; i++) {
                    if ($scope.vus.get(i).name.getText() === variantName) {
                        return true;
                    }
                }

                return false;
            }

            function addVUS() {
                var model = $scope.realtimeDocument.getModel();
                var vus;
                if (model.getRoot().get('vus')) {
                    vus = model.getRoot().get('vus');
                } else {
                    vus = model.createList();
                    model.getRoot().set('vus', vus);
                }
                $scope.vus = vus;
            }

            function isDesiredGene() {
                var _geneName = $scope.gene.name.getText();
                for (var i = 0; i < OncoKB.global.genes.length; i++) {
                    if (OncoKB.global.genes[i].hugoSymbol === _geneName) {
                        $scope.status.isDesiredGene = true;
                        $scope.meta.gene = OncoKB.global.genes[i];
                        break;
                    }
                }
                $rootScope.isDesiredGene = $scope.status.isDesiredGene;
            }

            $scope.fileTitle = $routeParams.geneName;
            $scope.gene = '';
            $scope.vus = '';
            $scope.comments = '';
            $scope.newGene = {};
            $scope.collaborators = {};
            $scope.checkboxes = {
                oncogenic: ['Yes', 'Likely', 'Likely Neutral', 'Inconclusive'],
                mutationEffect: ['Gain-of-function', 'Likely Gain-of-function', 'Loss-of-function', 'Likely Loss-of-function', 'Switch-of-function', 'Likely Switch-of-function', 'Neutral', 'Likely Neutral', 'Inconclusive'],
                hotspot: ['TRUE', 'FALSE'],
                TSG: ['Tumor Suppressor'],
                OCG: ['Oncogene']
            };
            $scope.nccnDiseaseTypes = ['', 'Acute Lymphoblastic Leukemia', 'Acute Myeloid Leukemia      20th Annual Edition!', 'Anal Carcinoma', 'Bladder Cancer', 'Bone Cancer', 'Breast Cancer', 'Cancer of Unknown Primary (See Occult Primary)', 'Central Nervous System Cancers', 'Cervical Cancer', 'Chronic Myelogenous Leukemia', 'Colon/Rectal Cancer', 'Colon Cancer      20th Annual Edition!', 'Rectal Cancer      20th Annual Edition!', 'Cutaneous Melanoma (See Melanoma)', 'Endometrial Cancer (See Uterine Neoplasms)', 'Esophageal and Esophagogastric Junction Cancers', 'Fallopian Tube Cancer (See Ovarian Cancer)', 'Gastric Cancer', 'Head and Neck Cancers', 'Hepatobiliary Cancers', 'Hodgkin Lymphoma', 'Kidney Cancer', 'Malignant Pleural Mesothelioma', 'Melanoma', 'Multiple Myeloma/Other Plasma Cell Neoplasms', 'Multiple Myeloma', 'Systemic Light Chain Amyloidosis', 'Waldenström\'s Macroglobulinemia / Lymphoplasmacytic Lymphoma', 'Myelodysplastic Syndromes', 'Neuroendocrine Tumors', 'Non-Hodgkin\'s Lymphomas', 'Non-Melanoma Skin Cancers', 'Basal Cell Skin Cancer', 'Dermatofibrosarcoma Protuberans', 'Merkel Cell Carcinoma', 'Squamous Cell Skin Cancer', 'Non-Small Cell Lung Cancer      20th Annual Edition!', 'Occult Primary', 'Ovarian Cancer', 'Pancreatic Adenocarcinoma', 'Penile Cancer', 'Primary Peritoneal Cancer (See Ovarian Cancer)', 'Prostate Cancer      20th Annual Edition!', 'Small Cell Lung Cancer      20th Annual Edition!', 'Soft Tissue Sarcoma', 'Testicular Cancer', 'Thymomas and Thymic Carcinomas', 'Thyroid Carcinoma', 'Uterine Neoplasms'];
            $scope.nccnCategories = [
                {
                    label: '',
                    value: ''
                },
                {
                    label: 'Category 1: Based upon high-level evidence, there is uniform NCCN consensus that the intervention is appropriate.',
                    value: '1'
                },
                {
                    label: 'Category 2A: Based upon lower-level evidence, there is uniform NCCN consensus that the intervention is appropriate.',
                    value: '2A'
                },
                {
                    label: 'Category 2B: Based upon lower-level evidence, there is NCCN consensus that the intervention is appropriate.',
                    value: '2B'
                },
                {
                    label: 'Category 3: Based upon any level of evidence, there is major NCCN disagreement that the intervention is appropriate.',
                    value: '3'
                }
            ];
            $scope.levels = getLevels();
            $scope.fileEditable = false;
            $scope.docStatus = {
                saved: true,
                saving: false,
                closed: false,
                savedGene: true,
                updateGene: false
            };
            $scope.metaDocStatus = {
                saved: true,
                saving: false
            };
            $scope.addMutationPlaceholder = 'Mutation Name';
            $scope.userRole = Users.getMe().role;
            $rootScope.userRole = Users.getMe().role;
            $scope.levelExps = {
                SR: $sce.trustAsHtml('<div><strong>Level R1:</strong> ' + $rootScope.meta.levelsDescHtml.R1 + '.<br/>Example 1: Colorectal cancer with KRAS mutation → resistance to cetuximab<br/>Example 2: EGFR-L858R or exon 19 mutant lung cancers with coincident T790M mutation → resistance to erlotinib</div>'),
                IR: $sce.trustAsHtml('<div><strong>Level R2:</strong> ' + $rootScope.meta.levelsDescHtml.R2 + '.<br/>Example: Resistance to crizotinib in a patient with metastatic lung adenocarcinoma harboring a CD74-ROS1 rearrangement (PMID: 23724914).<br/><strong>Level R3:</strong> ' + $rootScope.meta.levelsDescHtml.R3 + '.<br/>Example: Preclinical evidence suggests that BRAF V600E mutant thyroid tumors are insensitive to RAF inhibitors (PMID: 23365119).<br/></div>')
            };
            $scope.showHideButtons = [
                {key: 'prevelenceShow', display: 'Prevalence'},
                {key: 'proImShow', display: 'Prognostic implications'},
                {key: 'nccnShow', display: 'NCCN guidelines'},
                {
                    key: 'ssShow',
                    display: 'Standard implications for sensitivity to therapy'
                },
                {
                    key: 'srShow',
                    display: 'Standard implications for resistance to therapy'
                },
                {
                    key: 'isShow',
                    display: 'Investigational implications for sensitivity to therapy'
                },
                {
                    key: 'irShow',
                    display: 'Investigational implications for resistance to therapy'
                },
                {key: 'trialsShow', display: 'Ongoing clinical trials'}
            ];
            $scope.list = [];
            $scope.sortableOptions = {
                stop: function(e, ui) {
                    console.log('dropindex', ui.dropindex);
                    console.log('index', ui.index);
                    console.log(e, ui);
                },
                beforeStop: function(e, ui) {
                    console.log('dropindex', ui.dropindex);
                    console.log('index', ui.index);
                    console.log(e, ui);
                }
                // handle: '> .myHandle'
            };
            $scope.selfParams = {};
            $scope.geneStatus = {};
            $scope.oncoTree = {
                mainTypes: [],
                tumorTypes: {}
            };
            $scope.suggestedMutations = [];
            $scope.meta = {
                gene: {}, // Gene meta info from database
                newCancerTypes: [{
                    mainType: '',
                    subtype: '',
                    oncoTreeTumorTypes: []
                }]
            };
            $scope.status = {
                expandAll: false,
                hideAllEmpty: false,
                rendering: true,
                numAccordion: 0,
                isDesiredGene: false,
                savingAll: false,
                noChanges: false,
                hasReviewContent: false, // indicate if any changes need to be reviewed
                mutationChanged: false // indicate there are changes in mutation section
            };

            $scope.$watch('meta.newCancerTypes', function(n) {
                if (n.length > 0 && (n[n.length - 1].mainType || n[n.length - 1].subtype)) {
                    $scope.meta.newCancerTypes.push({
                        mainType: '',
                        subtype: '',
                        oncoTreeTumorTypes: angular.copy($scope.oncoTree.allTumorTypes)
                    });
                }
                for (var i = n.length - 2; i >= 0; i--) {
                    if (!n[i].mainType && !n[i].subtype) {
                        n.splice(i, 1);
                        i--;
                    }
                }
                function callback(index, result, type) {
                    if (type === 'mainType') {
                        n[index].oncoTreeTumorTypes = result;
                    } else {
                        n[index].mainType = result;
                    }
                    var next = index + 1;
                    if (next < n.length - 1) {
                        if (n[next].subtype) {
                            findMainTypeBySubtype(next, n[next].subtype, callback);
                        } else {
                            findTumorTypeByMainType(next, n[next].mainType, callback);
                        }
                    }
                }

                if (n.length > 1) {
                    if (n[0].subtype) {
                        findMainTypeBySubtype(0, n[0].subtype, callback);
                    } else {
                        findTumorTypeByMainType(0, n[0].mainType, callback);
                    }
                }
            }, true);

            function findTumorTypeByMainType(index, mainType, callback) {
                if (mainType && mainType.name) {
                    if ($scope.oncoTree.tumorTypes.hasOwnProperty(mainType.name)) {
                        if (_.isFunction(callback)) {
                            callback(index, $scope.oncoTree.tumorTypes[mainType.name], 'mainType');
                        }
                    } else {
                        DatabaseConnector.getOncoTreeTumorTypesByMainType(mainType.name)
                            .then(function(result) {
                                if (result.data) {
                                    $scope.oncoTree.tumorTypes[mainType.name] = result.data;
                                    if (_.isFunction(callback)) {
                                        callback(index, result.data, 'mainType');
                                    }
                                }
                            }, function() {
                                if (_.isFunction(callback)) {
                                    callback(index, '', 'mainType');
                                }
                            });
                    }
                } else if (_.isFunction(callback)) {
                    callback(index, '', 'mainType');
                }
            }

            function findMainTypeBySubtype(index, subtype, callback) {
                if (subtype && subtype.mainType && subtype.mainType.name) {
                    var match = -1;
                    for (var i = 0; i < $scope.oncoTree.mainTypes.length; i++) {
                        if ($scope.oncoTree.mainTypes[i].name === subtype.mainType.name) {
                            match = i;
                            break;
                        }
                    }
                    if (_.isFunction(callback)) {
                        callback(index, match > -1 ? $scope.oncoTree.mainTypes[match] : '', 'subtype');
                    }
                } else if (_.isFunction(callback)) {
                    callback(index, '', 'subtype');
                }
            }

            if ($scope.userRole === 8) {
                $scope.status.hideAllObsolete = false;
            } else {
                $scope.status.hideAllObsolete = true;
            }

            $scope.$watch('status.hideAllEmpty', function(n, o) {
                if (n !== o) {
                    if (n) {
                        $scope.isOpenFunc('hideEmpty');
                    } else {
                        $scope.isOpenFunc('showEmpty');
                    }
                }
            });

            $scope.$watch('status.expandAll', function(n, o) {
                if (n !== o) {
                    if (n) {
                        $scope.isOpenFunc('expand');
                    } else {
                        $scope.isOpenFunc('collapse');
                    }
                }
            });

            $scope.$watch('meta.newMainType', function(n) {
                if (_.isArray(n) && n.length > 0) {
                    var _tumorTypes = [];
                    var locks = 0;
                    _.each(n, function(mainType) {
                        if ($scope.oncoTree.tumorTypes.hasOwnProperty(mainType.name)) {
                            _tumorTypes = _.union(_tumorTypes, $scope.oncoTree.tumorTypes[mainType.name]);
                        } else {
                            locks++;
                            DatabaseConnector.getOncoTreeTumorTypesByMainType(mainType.name)
                                .then(function(result) {
                                    if (result.data) {
                                        $scope.oncoTree.tumorTypes[mainType.name] = result.data;
                                        _tumorTypes = _.union(_tumorTypes, result.data);
                                    }
                                    locks--;
                                }, function() {
                                    locks--;
                                });
                        }
                    });
                    var interval = $interval(function() {
                        if (locks === 0) {
                            $scope.meta.currentOncoTreeTumorTypes = _tumorTypes;
                            $interval.cancel(interval);
                        }
                    }, 100);
                }
            });
            getOncoTreeMainTypes();
            $interval(function() {
                storage.requireAuth(true).then(function(result) {
                    if (result && !result.error) {
                        console.log('\t checked token', new Date().getTime(), gapi.auth.getToken());
                    } else {
                        documentClosed();
                        $rootScope.$emit('realtimeDoc.token_refresh_required');
                        console.log('error when renew token in interval func.');
                    }
                });
            }, 600000);

            loadFile()
                .then(function(file) {
                    $scope.realtimeDocument = file;
                    var _documents = Documents.get({title: $scope.fileTitle});
                    if (_.isArray(_documents) && _documents.length > 0) {
                        $scope.document = _documents[0];
                    }

                    if ($scope.fileTitle) {
                        var model = $scope.realtimeDocument.getModel();
                        $rootScope.model = model;
                        if (model.getRoot().get('gene')) {
                            var numAccordion = 0;
                            model.getRoot().get('gene').mutations.asArray().forEach(function(mutation) {
                                numAccordion += mutation.tumors.length;
                                mutation.tumors.asArray().forEach(function(tumor) {
                                    numAccordion += 8;
                                    tumor.TI.asArray().forEach(function(ti) {
                                        numAccordion += ti.treatments.length;
                                    });
                                });
                            });
                            console.log(numAccordion);
                            $scope.status.numAccordion = numAccordion;
                            $scope.gene = model.getRoot().get('gene');
                            $scope.model = model;
                            loadMetaFile(afterCreateGeneModel);
                        } else {
                            var gene = model.create('Gene');
                            model.getRoot().set('gene', gene);
                            $scope.gene = gene;
                            $scope.gene.name.setText($scope.fileTitle);
                            $scope.model = model;
                            loadMetaFile(afterCreateGeneModel);
                        }
                    } else {
                        $scope.model = '';
                    }
                    $scope.gene.name_review.addEventListener(gapi.drive.realtime.EventType.VALUE_CHANGED, valueChangedEvent);
                })
                .finally(function() {
                    getSuggestedMutations();
                    if (_.isArray(OncoKB.global.genes)) {
                        isDesiredGene();
                    } else {
                        DatabaseConnector.getAllGene(function(data) {
                            OncoKB.global.genes = data;
                            isDesiredGene();
                        });
                    }
                });

            // Token expired, refresh
            $rootScope.$on('realtimeDoc.token_refresh_required', function() {
                var errorMessage = 'An error has occurred. This page will be redirected to Genes page.';
                dialogs.error('Error', errorMessage);
                documentClosed();
                $location.path('/genes');
            });

            // Other unidentify error
            $rootScope.$on('realtimeDoc.other_error', function() {
                var errorMessage = 'An error has occurred. This page will be redirected to Genes page.';
                dialogs.error('Error', errorMessage);
                documentClosed();
                $location.path('/genes');
            });

            // Realtime documet not found
            $rootScope.$on('realtimeDoc.client_error', function() {
                var errorMessage = 'An error has occurred. This page will be redirected to Genes page.';
                dialogs.error('Error', errorMessage);
                documentClosed();
                $location.path('/genes');
            });

            // Realtime documet not found
            $rootScope.$on('realtimeDoc.not_found', function() {
                var errorMessage = 'An error has occurred. This page will be redirected to Genes page.';
                dialogs.error('Error', errorMessage);
                documentClosed();
                $location.path('/genes');
            });

            $scope.$on('interruptedDueToOtherReview', function() {
                // if previously the document is editable, need to notify
                // the current user.
                if ($scope.fileEditable) {
                    dialogs.notify('Warning',
                        $scope.gene.name_review.get('currentReviewer') +
                        ' started to review the document, ' +
                        'you can not change anything at this moment. ' +
                        'We will notify you once the reviewer finished ' +
                        'the editing. Thanks. ' +
                        'Sorry for any inconvinience.');
                }
                $scope.fileEditable = false;
            });

            $scope.$on('startSaveDataToDatabase', function() {
                $scope.status.saveDataToDatabase = true;
                $scope.geneMainDivStyle.opacity = 0.1;
            });

            $scope.$on('doneSaveDataToDatabase', function() {
                $scope.status.saveDataToDatabase = false;
                $scope.geneMainDivStyle.opacity = 1;
            });

            $scope.$on('$locationChangeStart', function() {
                storage.closeDocument();
                documentClosed();
            });
            $window.onbeforeunload = function() {
                // If in the review mode, exit the review mode first then
                // close the tab.
                if ($rootScope.reviewMode) {
                    $scope.exitReview();
                }
            };
        }]
    )
    .controller('ModifyTumorTypeCtrl', function($scope, $modalInstance, data, _, OncoKB, $rootScope, user) {
        $scope.meta = {
            model: data.model,
            oncoTree: data.oncoTree,
            cancerTypes: data.cancerTypes,
            newCancerTypes: [],
            cancerTypes_review: data.cancerTypes_review,
            cancerTypes_uuid: data.cancerTypes_uuid
        };

        $scope.cancel = function() {
            $modalInstance.dismiss('canceled');
        }; // end cancel

        $scope.save = function() {
            $scope.meta.model.beginCompoundOperation();
            var lastReviewed = [];
            for(var i = 0; i < $scope.meta.cancerTypes.length; i++) {
                var item = $scope.meta.cancerTypes.get(i);
                lastReviewed.push({cancerType: item.cancerType.getText(), subtype: item.subtype.getText()});
            }
            if ($scope.meta.cancerTypes_review && _.isNull($scope.meta.cancerTypes_review.get('lastReviewed'))) {
                $scope.meta.cancerTypes_review.set('lastReviewed', lastReviewed);
            }
            $scope.meta.cancerTypes_review.set('updatedBy', user.name);
            $scope.meta.cancerTypes_review.set('updateTime', new Date().toLocaleString());
            $scope.meta.cancerTypes.clear();
            _.each($scope.meta.newCancerTypes, function(ct) {
                if (ct.mainType.name) {
                    var cancerType = $scope.meta.model.create(OncoKB.CancerType);
                    cancerType.cancerType.setText(ct.mainType.name);
                    if (ct.subtype) {
                        if (ct.subtype.code) {
                            cancerType.oncoTreeCode.setText(ct.subtype.code);
                        }
                        if (ct.subtype.name) {
                            cancerType.subtype.setText(ct.subtype.name);
                        }
                    }
                    cancerType.cancerType_eStatus.set('obsolete', 'false');
                    cancerType.subtype_eStatus.set('obsolete', 'false');
                    cancerType.oncoTreeCode_eStatus.set('obsolete', 'false');
                    console.log(cancerType);
                    $scope.meta.cancerTypes.push(cancerType);
                }
            });

            $scope.meta.model.endCompoundOperation();
            $modalInstance.close();

            var uuid = $scope.meta.cancerTypes_uuid.getText();
            if ( $rootScope.geneMetaData.get(uuid)) {
                 $rootScope.geneMetaData.get(uuid).set('review', true);
            } else {
                var temp = $rootScope.metaModel.createMap();
                temp.set('review', true);
                 $rootScope.geneMetaData.set(uuid, temp);
            }
        }; // end save

        $scope.$watch('meta.newCancerTypes', function(n) {
            // console.log('meta.newcancertypes watch has been called.',n, o);
            if (n.length > 0 && (n[n.length - 1].mainType || n[n.length - 1].subtype)) {
                $scope.meta.newCancerTypes.push({
                    mainType: '',
                    subtype: '',
                    oncoTreeTumorTypes: angular.copy($scope.meta.oncoTree.allTumorTypes)
                });
            }
            for (var i = n.length - 2; i >= 0; i--) {
                if (!n[i].mainType) {
                    if (n[i].mainType !== '') {
                        n.splice(i, 1);
                        i--;
                    }
                }
            }
            function callback(index, mainType, subType, oncoTreeTumorTypes) {
                n[index].oncoTreeTumorTypes = oncoTreeTumorTypes ? oncoTreeTumorTypes : $scope.meta.oncoTree.allTumorTypes;

                if (mainType) {
                    n[index].mainType = mainType;
                }

                var next = index + 1;
                if (next < n.length - 1) {
                    findCancerType(next, n[next].mainType, n[next].subtype, callback);
                }
            }

            if (n.length > 1) {
                findCancerType(0, n[0].mainType, n[0].subtype, callback);
            }
        }, true);

        initNewCancerTypes();

        function findCancerType(index, mainType, subtype, callback) {
            var list;
            var _mainType;
            if (mainType && mainType.name) {
                list = $scope.meta.oncoTree.tumorTypes[mainType.name];
            }
            if (!mainType && subtype) {
                _mainType = findMainType(subtype.mainType.name);
            }
            callback(index, _mainType, subtype, list);
        }

        function initNewCancerTypes() {
            var newCancerTypes = [];
            _.each($scope.meta.cancerTypes.asArray(), function(cancerType) {
                var mainType = findMainType(cancerType.cancerType.getText());
                var subtype = findSubtype(cancerType.subtype.getText());
                newCancerTypes.push({
                    mainType: mainType,
                    oncoTreeCode: cancerType.oncoTreeCode.getText(),
                    subtype: subtype,
                    oncoTreeTumorTypes: []
                });
            });

            newCancerTypes.push({
                mainType: '',
                subtype: '',
                oncoTreeTumorTypes: angular.copy($scope.meta.oncoTree.allTumorTypes)
            });
            $scope.meta.newCancerTypes = newCancerTypes;
        }

        function findMainType(name) {
            for (var i = 0; i < $scope.meta.oncoTree.mainTypes.length; i++) {
                if ($scope.meta.oncoTree.mainTypes[i].name === name) {
                    return $scope.meta.oncoTree.mainTypes[i];
                }
            }
            return '';
        }

        function findSubtype(name) {
            for (var i = 0; i < $scope.meta.oncoTree.allTumorTypes.length; i++) {
                if ($scope.meta.oncoTree.allTumorTypes[i].name === name) {
                    return $scope.meta.oncoTree.allTumorTypes[i];
                }
            }
            return '';
        }
    });
