// DateFormat
var DateFormat = 'dd/MM/yyyy HH:mm:ss';

// Models
window.Project = Backbone.Model.extend();

window.Deploy = Backbone.Model.extend();

window.Modules = Backbone.Model.extend();


window.ProjectCollection = Backbone.Collection.extend({
    model: Project,
    url: "/api/projects"
});

window.ProjectModel = Backbone.Collection.extend({
    model: Project,
    initialize: function (id) {
        this.url = "/api/project/" + id;
    }
});

window.ClientModulesModel = Backbone.Collection.extend({
    model: Modules,
    initialize: function (id, clientName) {
        this.url = "/api/project/" + id + "/client/" + clientName
    }
});

window.DeployModel = Backbone.Collection.extend({
    model: Deploy,
    initialize: function (proj, id) {
        this.url = "/api/project/" + proj + "/deploy/" + id;
    }
});

window.DeployCollection = Backbone.Collection.extend({
    model: Deploy,
    initialize: function (id) {
        this.url = "/api/project/" + id + "/deploys?max=100";
    }
});

// Views

var tabs = '<div>' +
    '<ul class="nav nav-tabs" style="border-bottom: 1px solid #DDD">' +
    '<li class="active"><a aria-expanded="true" href="#deploys" data-toggle="tab">Deploys</a></li>' +
    '<li class=""><a aria-expanded="false" href="#clients" data-toggle="tab">Modules</a></li>' +
    '</ul><br></div>';
var pager = '<div class="col-md-12 text-center">' +
    '<ul class="pagination pagination-lg pager" id="myPager"></ul>' +
    '</div>';

window.ProjectListView = Backbone.View.extend({

    initialize: function () {
        this.model.bind("reset", this.render, this);
    },

    render: function (eventName) {
        if (this.model.models.length == 0) {
            var nocontent = {xhr: {status: "204", statusText: "No projects found"}}
            errorWindow(nocontent);
        }
        else {
            this.model.models.reverse().forEach(function (proj) {
                $(this.el).append(new ProjectListItemView({model: proj.attributes}).render().el);
            }, this);
            return this;
        }

    }

});

window.ModulesListView = Backbone.View.extend({

    initialize: function () {
        this.model.bind("reset", this.render, this);
    },

    render: function (eventName) {
        $(this.el).append(new ModulesListItemView({model: mod.attributes}).render().el);
        return this;
    }

});


window.ProjectListItemView = Backbone.View.extend({

    template: _.template($('#projectListTpl').html()),

    render: function (eventName) {
        $(this.el).html(this.template(this.model));
        return this;
    }

});

window.ModulesListItemView = Backbone.View.extend({

    template: _.template($('#modulesTpl').html()),

    render: function (eventName) {
        $(this.el).html(this.template(this.model));
        return this;
    }

});

window.DeployListView = Backbone.View.extend({
    tagName: 'table',
    className: 'table table-striped table-hover col-md-12 table-fixed',
    initialize: function (options) {
        this.model.bind("reset", this.render, this);
        this.projname = options.proj
    },

    render: function (eventName) {
        var deploysTableHeader = "<thead>" +
            "<tr>" +
            "<th style='width: 70px;'>Status</th>" +
            "<th>Timestamp</th>" +
            "<th class='col-md-3'>Description</th>" +
            "<th>Version</th>" +
            "<th>User</th>" +
            "<th>Client</th>" +
            "<th style='width: 90px;'>Details</th>" +
            "</tr>" +
            "</thead>";
        $(this.el).append(deploysTableHeader);
        $(this.el).append("<tbody id='deployTable'>");
        this.model.models.forEach(function (deploy) {
            var currentDeploy = deploy.attributes;
            currentDeploy.projname = this.projname;

            $(this.el).append(new DeployListItemView({model: currentDeploy}).render().el);
        }, this);

        $(this.el).append("</tbody>");
        return this;
    }

});

window.DeployListItemView = Backbone.View.extend({
    tagName: 'tr',

    template: _.template($('#deployTpl').html()),

    render: function (eventName) {
        var deploy = this.model;
        deploy.timestamp = $.format.prettyDate(deploy.timestamp, DateFormat);
        if (deploy.events[deploy.events.length - 1]) {
            switch (deploy.events[deploy.events.length - 1].status) {
                case "SUCCESS":
                    deploy.status = "ok-sign";
                    break;
                case "FAILED":
                    deploy.status = "remove-sign";
                    break;
                case "SKIPPED":
                    deploy.status = "question-sign";
                    break;
                case "LOG":
                    deploy.status = "info-sign";
                    break;
                default:
                    deploy.status = "cloud-upload";
            }
        }
        else {
            deploy.status = "info-sign";
        }
        $(this.el).html(this.template(deploy));
        return this;
    }

});

window.DeployView = Backbone.View.extend({

    initialize: function (options) {
        this.model.bind("reset", this.render, this);
        this.projname = options.proj
    },

    template: _.template($('#deployDetailTpl').html()),

    render: function (eventName) {
        deploy = this.model.models[0].attributes;
        deploy.projname = this.projname;
        events = [];
        modules = [];
        if (deploy.events.length <= 1) {
            deploy.running = true;
        }
        deploy.events.reverse().forEach(function (ev) {
            ev.timestamp = $.format.date(ev.timestamp, DateFormat);
            switch (ev.status) {
                case "SUCCESS":
                    ev.color = "success";
                    break;
                case "FAILED":
                    ev.color = "danger";
                    break;
                case "SKIPPED":
                    ev.color = "warning";
                    break;
                case "LOG":
                    ev.color = "info";
                    break;
                default:
                    ev.color = "default";
            }
            events.push(ev);
        }, this);
        deploy.modules.forEach(function (m) {
            switch (m.status) {
                case "ADD":
                    m.icon = "ok";
                    m.color = "success";
                    break;
                default:
                    m.icon = "remove";
                    m.color = "danger";
            }
            modules.push(m);
        }, this);
        deploy.modules = modules;
        deploy.events = events;
        deploy.timestamp = $.format.date(deploy.timestamp, DateFormat);
        $(this.el).html(this.template(deploy));
        return this;
    },
    events: {
        'click #addEvent': 'addEvent',
        'click #downloadButton': 'downloadEvent'
    },
    downloadEvent: function (e) {
        e.preventDefault();
        downloadDiv('config.txt', 'configText', 'text/plain');
    },
    addEvent: function (e) {
        $('#eventModal').modal('hide');
        $.ajax({
            url: $(e.currentTarget).data("url"),
            type: "POST",
            data: JSON.stringify({
                status: $("#status").find("option:selected").text(),
                description: $("#msg").val()
            }),
            contentType: "application/json",
            dataType: "json",
            success: function () {
                Backbone.history.loadUrl(Backbone.history.fragment);
            },
            failure: function () {
                Backbone.history.loadUrl(Backbone.history.fragment);
                simpleError("Error on processing request.");
            }
        })
    }
});

window.ProjectView = Backbone.View.extend({

    template: _.template($('#projectTpl').html()),

    render: function (eventName) {
        $(this.el).html(this.template(this.model.models[0].attributes));
        return this;
    }

});

// Router
var AppRouter = Backbone.Router.extend({

    routes: {
        "": "home",
        ":projname": "project",
        ":project/:id": "deploy"
    },

    home: function () {
        projectList = new ProjectCollection();
        projectList.fetch({
            // reset:"true",
            success: (function () {
                this.projectListView = new ProjectListView({model: projectList});
                $('.content-section').html("");
                $('.project-section').html(this.projectListView.render().el);
            }),
            error: (function (xhr, status, error) {
                errorWindow(error)
            })
        });

    },

    project: function (projname) {
        proj = new ProjectModel(projname);
        proj.fetch({
            reset: "true",
            success: (function () {
                this.projView = new ProjectView({model: proj});
                $('.project-section').html(this.projView.render().el);
                $('.project-section').append(tabs);
                $('.nav').on('click', function () {
                    $('.modulesError').remove();
                    $('.simpleError').remove();
                });
                deployList = new DeployCollection(projname);
                deployList.fetch({
                    reset: "true",
                    success: (function () {
                        deployListView = new DeployListView({model: this.deployList, proj: projname});
                        $.get("/api/project/" + projname + "/clients", function (result) {
                            var tpl = _.template($("#modulesTpl").html());
                            $('.content-section').html(deployListView.render().el);
                            $('.content-section').html(
                                '<div class="tab-pane fade active in" id="deploys">' +
                                $('.content-section').html() + pager +
                                '</div>'
                            );

                            $("#deployTable").pageMe({
                                pagerSelector: '#myPager',
                                showPrevNext: true,
                                hidePageNumbers: false,
                                perPage: 10
                            });


                            var resultHtml = '<div class="tab-pane fade" id="clients"></div>';
                            $('.content-section').append(resultHtml)
                            result.forEach(function (client) {
                                $.get("/api/project/" + projname + "/client/" + client, function (data) {
                                    $('#clients').append(tpl({client: client, modules: data}));
                                })
                            }, this)


                        });

                    }),
                    error: (function (xhr, status, error) {
                        errorWindow(error)
                    })
                });
            }),
            error: (function (xhr, status, error) {
                errorWindow(error)
            })
        });
    },

    deploy: function (projname, id) {
        proj = new ProjectModel(projname);
        proj.fetch({
            reset: "true",
            success: (function () {
                this.projView = new ProjectView({model: proj});
                $('.project-section').html(this.projView.render().el);
                deployDetails = new DeployModel(projname, id);
                deployDetails.fetch({
                    reset: "true",
                    success: (function () {
                        this.deployDetailsView = new DeployView({model: this.deployDetails, proj: projname});
                        $('.content-section').html(this.deployDetailsView.render().el);

                    }),
                    error: (function (xhr, status, error) {
                        errorWindow(error)
                    })
                });
            }),
            error: (function (xhr, status, error) {
                errorWindow(error)
            })
        });

    }
});

var app = new AppRouter();
Backbone.history.start();

function errorWindow(err) {
    console.log(err);
    var error =
        '<div class="alert alert-dismissible alert-danger">' +
        '<strong>Oh snap!</strong><br>' + err.xhr.status + ": " + err.xhr.statusText
    '</div>'
    $('.content-section').html("<p></p>");
    $('.project-section').html(error);
}

function simpleError(err) {
    $('.simpleError').remove();
    var error = '<div class="alert alert-dismissible alert-danger simpleError">' +
        '<button type="button" class="close" data-dismiss="alert">�</button>' +
        '<h4>Error!</h4>' +
        '<p>' + err + '</p>' +
        '</div>"';
    $('.content-section').append(error);
}

