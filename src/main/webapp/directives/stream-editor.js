app.directive('inputStreamEditor', function($compile) {
	return {
		restrict : "E",
		replace : true,
		templateUrl : 'views/component/input-stream-editor.html',
		scope : {
		    topology : "=topology",
			component : "=component",
			stream : "=stream",
		}
	};
});

app.directive('outputStreamEditor', function($compile) {
	return {
		restrict : "E",
		replace : true,
		templateUrl : 'views/component/output-stream-editor.html',
		scope : {
		    topology : "=topology",
			component : "=component",
			stream : "=stream"
		}
	};
});