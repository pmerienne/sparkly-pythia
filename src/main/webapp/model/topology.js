app.factory('Topology', function($http, Component, Connection) {

    function Topology(id, name, components, connections) {
        this.id = id;
        this.name = name;
        this.components = components;
        this.connections = connections;
    };

    Topology.build = function (data) {
        return new Topology(
            data.id,
            data.name,
            data.components.map(Component.build),
            data.connections.map(Connection.build)
        );
    };

    Topology.findAll = function() {
        return $http.get('api/topologies/').then(function(topologies) {
            return $.map(topologies.data, Topology.build);
        });
    };

    Topology.findById = function(id) {
        return $http.get('api/topologies/' + id).then(function(topology) {
            return Topology.build(topology.data);
        });
    };

    Topology.delete = function(id) {
        return $http.delete('api/topologies/' + id);
    };

    Topology.prototype.save = function() {
        return $http.put('api/topologies/' , this);
    };

    Topology.prototype.retrieveAvailableFeatures = function(component, stream) {
        var self = this;
        var features = [];
        var outputsConnectedTo = this.connectedTo(component.id, stream.name);

        outputsConnectedTo.forEach(function(os) {
            var osFeatures = os.availableFeatures();
            features.pushAll(osFeatures);

            if(os.metadata.from) {
                var is = component.inputStream(os.metadata.from)
                features.pushAll(self.retrieveAvailableFeatures(is));
            }
        });

        return features;
    };

    Topology.prototype.connectedTo = function(componentId, streamName) {
        var self = this;
        var outputs = [];

        this.connections.forEach(function (connection) {
            if(connection.to.component == componentId && connection.to.stream == streamName) {
                var output = self.component(connection.from.component).outputStream(connection.from.stream);
                outputs.push(output);
            }
        });

        return outputs;
    };

    Topology.prototype.component = function(componentId) {
        return $.grep(this.components, function (component) { return component.id == componentId})[0]
    };

    return Topology;
});