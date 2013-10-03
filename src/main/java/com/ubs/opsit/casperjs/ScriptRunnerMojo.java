package com.ubs.opsit.casperjs;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;

import org.apache.commons.lang3.StringUtils;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

@Mojo(name = "test", defaultPhase = LifecyclePhase.TEST, threadSafe=true)
public class ScriptRunnerMojo extends AbstractRunnerMojo {

    @Parameter(alias = "tests.directory", defaultValue = "${basedir}/src/test/js")
    private File testsDir;

    @Parameter(alias = "include.javascript")
    private boolean includeJS = true;

    @Parameter(alias = "include.coffeescript")
    private boolean includeCS = true;

    private Log log = getLog();

    @Override
    public Result run() {
        Result globalResult = new Result();
        if (includeJS) {
            globalResult.add(executeScripts(".js"));
        } else {
            log.info("JavaScript files ignored");
        }
        if (includeCS) {
            globalResult.add(executeScripts(".coffee"));
        } else {
            log.info("CoffeeScript files ignored");
        }
        return globalResult;
    }

	private Result executeScripts(final String ext) {
        Result result = new Result();
        File[] files = testsDir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return StringUtils.endsWithIgnoreCase(name, ext);
            }
        });
        if (files.length == 0) {
            log.warn("No " + ext + " files found in directory " + testsDir);
        } else {
            for (File f : files) {
                log.debug("Execution of test " + f.getName());
                int res = executeScript(f);
                if (res == 0) {
                    result.addSuccess();
                } else {
                    log.warn("Test '" + f.getName() + "' has failure");
                    result.addFailure();
                }
            }
        }
        return result;
    }

    private int executeScript(File f) {
        return prepareAndExecuteCommand(f, pathWithoutExtension(f.getName()), new ArrayList<String>());
    }

}
