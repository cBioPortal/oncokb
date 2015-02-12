'use strict';

/**
 * @ngdoc service
 * @name oncokb.loadFile
 * @description
 * # loadFile
 * Service in the oncokb.
 */
angular.module('oncokb')
  .service('loadFile', function loadFile($route, $location, $q, storage, documents) {
    return function() {
        var title = $route.current.params.geneName;
        var userId = $route.current.params.user;
        var recheckDocPromise = check();

        console.log(userId,title);

        function check() {
            var deferred = $q.defer();
            storage.requireAuth(true, userId).then(function () {
                var _documents = documents.get({'title': title})
                if(angular.isArray(documents) && _documents.length > 0) {
                    deferred.resolve(storage.getRealtimeDocument(_documents[0].id));
                }else {
                    storage.retrieveAllFiles().then(function(result){
                        documents.set(result);
                        var __documents = documents.get({'title': title});
                        if(angular.isArray(__documents) && __documents.length > 0) {
                            deferred.resolve(storage.getRealtimeDocument(__documents[0].id));
                        }else {
                            deferred.resolve(null);
                        }
                    });
                }
            });
            return deferred.promise;
        }
        return $q.all([recheckDocPromise]).then(function(realdocument){
            console.log(realdocument);
            if(angular.isArray(realdocument) && realdocument.length > 0) {
                return realdocument[0];
            }else {
                $location.url('/');
            }
        });
    }
  });
