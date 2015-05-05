/*global define,App,_,$*/
define([
    'backbone',
    'mustache',
    'text!templates/typed-link-modal.html',
    'views/typed-link-item',
    'async'
], function (Backbone, Mustache, template, TypedLinkItemView, async){
    'use strict';

    var TypedLinkModalView = Backbone.View.extend({

        className:'modal hide typed-link-modal',

        events: {
            'hidden': 'onHidden',
            'click .add-type-btn': 'onAddTypedLink',
            'click .cancel-button': 'closeModal'
        },

        initialize: function () {
            this.pathSelected = this.options.pathSelected;
            this.productId = this.options.productId;
            this.serialNumber = this.options.serialNumber;
        },

        bindDOMElements:function(){
            this.$modal = this.$el;
        },

        render: function () {

            this.$el.html(Mustache.render(template, {
                i18n: App.config.i18n
            }));

            this.bindDOMElements();
            this.getExistingPathToPathAndType();

            return this;
        },

        getUrlForAvailableType: function(){

            var urlForAvailableType = '';

            if(!this.serialNumber){
                urlForAvailableType = App.config.contextPath + '/api/workspaces/' + App.config.workspaceId + '/products/' + this.productId + '/path-to-path-links-types';
            }else{
                urlForAvailableType = App.config.contextPath + '/api/workspaces/' + App.config.workspaceId + '/products/' + this.productId + '/product-instances/' + this.serialNumber + '/path-to-path-links-types';
            }

            return urlForAvailableType;
        },

        getUrlForExistingTypedLink: function(){
            var urlForExistingTypedLink = '';

            if(!this.serialNumber){
                urlForExistingTypedLink = App.config.contextPath + '/api/workspaces/' + App.config.workspaceId + '/products/' + this.productId
                    + '/path-to-path-links/source/' + this.pathSelected[0].getEncodedPath() + '/target/' + this.pathSelected[1].getEncodedPath();;
            }else{
                urlForExistingTypedLink = App.config.contextPath + '/api/workspaces/' + App.config.workspaceId + '/products/' + this.productId + '/product-instances/'
                    + this.serialNumber + '/path-to-path-links/source/' + this.pathSelected[0].getEncodedPath() + '/target/' + this.pathSelected[1].getEncodedPath();
            }

            return urlForExistingTypedLink;
        },

        getExistingPathToPathAndType:function(){
            var self = this;

            var urlForAvailableType = this.getUrlForAvailableType();


            this.existingPathToPathLinkCollection = [];
            this.availableType = [];
            $.ajax({
                type : 'GET',
                url : urlForAvailableType,
                contentType: 'application/json',
                success: function(pathToPathLinkDTOs){
                    _.each(pathToPathLinkDTOs, function(pathToPathLinkDTO){
                        self.availableType.push(pathToPathLinkDTO.type);
                    });

                    self.getExistingPathToPath();

                },
                error: function(){

                }
            });
        },

        getExistingPathToPath: function(){
            var self = this;
            var urlForExistingTypedLink = this.getUrlForExistingTypedLink();
            $.ajax({
                type : 'GET',
                url : urlForExistingTypedLink,
                contentType: 'application/json',
                success: function(pathToPathLinkDTOs){
                    _.each(pathToPathLinkDTOs, function(pathToPathLinkDTO){
                        self.existingPathToPathLinkCollection.push({
                            sourceModel : self.pathSelected[0].getEncodedPath() === pathToPathLinkDTO.source ? self.pathSelected[0] : self.pathSelected[1],
                            targetModel : self.pathSelected[1].getEncodedPath() === pathToPathLinkDTO.source ? self.pathSelected[1] : self.pathSelected[0],
                            pathToPath : pathToPathLinkDTO,
                            isEditMode : false,
                            availableType : self.availableType,
                            productId : self.productId,
                            serialNumber : self.serialNumber
                        });
                    });

                    _.each(self.existingPathToPathLinkCollection, function(pathToPathLink){
                        var typeLinkItem = new TypedLinkItemView({model:pathToPathLink}).render();
                        self.$('#path-to-path-links').append(typeLinkItem.el);
                        typeLinkItem.on('remove', function(){
                            self.existingPathToPathLinkCollection.splice(self.existingPathToPathLinkCollection.indexOf(pathToPathLink),1);
                        });
                    });

                },
                error: function(){

                }
            });
        },

        onAddTypedLink: function(){
            var newTypedLinkItemView = new TypedLinkItemView({
                model:{
                    sourceModel : this.pathSelected[0],
                    targetModel : this.pathSelected[1],
                    isEditMode : true,
                    availableType : this.availableType,
                    productId : this.productId,
                    serialNumber : this.serialNumber
                }
            }).render();

            var self = this;
            self.$('#path-to-path-links').append(newTypedLinkItemView.el);
        },


        openModal: function () {
            this.$modal.modal('show');
        },

        closeModal: function () {
            this.$modal.modal('hide');
        },

        onHidden: function () {
            this.remove();
        }

    });

    return TypedLinkModalView;
});
