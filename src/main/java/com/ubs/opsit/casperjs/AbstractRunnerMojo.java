package com.ubs.opsit.casperjs;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Parameter;

public abstract class AbstractRunnerMojo extends AbstractMojo {

    @Parameter(alias = "casperjs.executable", defaultValue = "casperjs")
    private String casperExec;

    @Parameter(alias = "ignoreTestFailures")
    private boolean ignoreTestFailures = false;

    @Parameter(alias = "verbose")
    private boolean verbose = false;

    @Parameter(alias = "include.javascript")
    private boolean includeJS = true;

    @Parameter(alias = "include.coffeescript")
    private boolean includeCS = true;

    @Parameter(alias = "include.features")
    private boolean includeFeatures = true;

    @Parameter(alias = "pre")
    private String pre;

    @Parameter(alias = "post")
    private String post;

    @Parameter(alias = "includes")
    private String includes;

    @Parameter(alias = "xunit")
    private String xUnit;

    @Parameter(alias = "logLevel")
    private String logLevel;

    @Parameter(alias = "direct")
    private boolean direct = false;

    @Parameter(alias = "failFast")
    private boolean failFast = false;

    @Parameter(alias = "engine")
    private String engine;

    @Parameter
    private List<String> arguments;

    private DefaultArtifactVersion casperJsVersion;

    private Log log = getLog();

    private void init() throws MojoFailureException {
        if (StringUtils.isBlank(casperExec)) {
            throw new MojoFailureException("CasperJS executable is not defined");
        }

        casperJsVersion = new DefaultArtifactVersion(checkVersion(casperExec));
        if (verbose) {
            log.info("CasperJS version: " + casperJsVersion);
        }
    }

    @Override
    public void execute() throws MojoFailureException {
        init();
        
        Result executionResult;
        try {
        	executionResult = run();
        } catch (Exception e) {
        	throw new MojoFailureException("Plugin execution failed", e);
        }
        
        log.info(executionResult.print());
        if (!ignoreTestFailures && executionResult.getFailures() > 0) {
            throw new MojoFailureException("There are " + executionResult.getFailures() + " test failures");
        }
    }

	protected int prepareAndExecuteCommand(File executedFile, String xUnitBase, List<String> args) {
		StringBuffer command = new StringBuffer();
        command.append(casperExec);
        if(casperJsVersion.compareTo(new DefaultArtifactVersion("1.1"))>=0) {
            command.append(" test");
        }
        // Option --includes, to includes files before each test execution
        if (StringUtils.isNotBlank(includes)) {
            command.append(" --includes=").append(includes);
        }
        // Option --pre, to execute the scripts before the test suite
        if (StringUtils.isNotBlank(pre)) {
            command.append(" --pre=").append(pre);
        }
        // Option --pre, to execute the scripts after the test suite
        if (StringUtils.isNotBlank(post)) {
            command.append(" --post=").append(post);
        }
        // Option --xunit, to export results in XML file
        if (StringUtils.isNotBlank(xUnit)) {
            command.append(" --xunit=").append(String.format(xUnit, xUnitBase));
        }
        // Option --fast-fast, to terminate the test suite once a failure is found
        if (failFast) {
            command.append(" --fail-fast");
        }
        // Option --direct, to output log messages to the console
        if (direct) {
            command.append(" --direct");
        }
		// Option --engine, to select phantomJS or slimerJS engine
		if (StringUtils.isNotBlank(engine)) {
			command.append(" --engine=").append(engine);
		}
        command.append(' ').append(executedFile.getAbsolutePath());
        if (arguments != null && !arguments.isEmpty()) {
        	args.addAll(arguments);
        }
        for (String argument:args) {
        	command.append(' ').append(argument);
        }
		return executeCommand(command.toString());
	}

    private String checkVersion(String casperExecutable) throws MojoFailureException {
        log.debug("Check CasperJS version");
        InputStream stream = null;
        try {
            Process child = Runtime.getRuntime().exec(casperExecutable + " --version");
            stream = child.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
            String version = reader.readLine();
            return version;
        } catch (IOException e) {
            if (verbose) {
                log.error("Could not run CasperJS command", e);
            }
            throw new MojoFailureException("Unable to determine casperJS version");
        } finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException e) {
                }
            }
        }
    }

    private int executeCommand(String command) {
        log.debug("Execute CasperJS command [" + command + "]");
        DefaultExecutor exec = new DefaultExecutor();
        CommandLine line = CommandLine.parse(command);
        try {
            return exec.execute(line);
        } catch (IOException e) {
            if (verbose) {
                log.error("Could not run CasperJS command", e);
            }
            return -1;
        }
    }
    
    protected String pathWithoutExtension(String path) {
        int pos = path.lastIndexOf(".");
        if (pos > 0) {
            path = path.substring(0, pos);
        }
        return path;
    }
    
    protected abstract Result run();
}
