var fs = require('fs');
var Yadda = require(casper.cli.get("yadda-dir") + '/index.js');
var arrays = require(casper.cli.get("yadda-dir") + '/Array.js');

var TextParser = Yadda.parsers.TextParser;
var Dictionary = Yadda.Dictionary;
var Library = Yadda.localisation.English;

function readLibrary(relativePath) {
    return require(casper.cli.get("libs-dir") + relativePath);
}

function loadScenarios(file) {
    var parser = new TextParser();
    var text = fs.read(file);
    return parser.parse(text);
};

function runSpec(spec) {
    $$info('');
    $$info('====================================');
    $$begin(spec.feature, function suite(test) {
      $$info('====================================');
      $$info('');
      arrays(spec.scenarios).eachAsync(function(scenario, completed, next) {
          $$start();
          $$info('---> ' + scenario.title);
          if (scenario.ignored) {
			  $$pass('Scenario is IGNORED')
			  $$thenBypass(scenario.steps.length);
          }
		  $$yadda(scenario.steps);

          $$run(function() {
              $$info('');
              next();
          });
      }, function(err) {
          $$info('');
          $$done();
      });
    });
};

var libraries = require(casper.cli.get("lib"));
var yadda = new Yadda.Yadda(arrays(libraries).flatten());
Yadda.plugins.casper(yadda, casper);

[casper, casper.test].forEach(
    function(obj) {
        for (var key in obj) {
            var type = typeof obj[key]
            if (typeof obj[key] == 'function') {
                window['$$' + key] = obj[key].bind(obj)
            }
        }
    }
)

casper.userAgent('Chrome')
var $$open = function(relativeUrl) {
	return casper.open('http://localhost:' + casper.cli.get("server-port") + relativeUrl)
}

runSpec(loadScenarios(casper.cli.get("feature")));