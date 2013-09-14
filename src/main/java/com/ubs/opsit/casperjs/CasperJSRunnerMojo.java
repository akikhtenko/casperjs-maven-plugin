package com.ubs.opsit.casperjs;

import java.io.BufferedReader;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import static org.apache.commons.io.FileUtils.listFiles;

/**
 * Runs JavaScript and/or CoffeScript test files on CasperJS instance
 * User: Romain Linsolas
 * Date: 09/04/13
 */
@Mojo(name = "test", defaultPhase = LifecyclePhase.TEST, threadSafe=true)
public class CasperJSRunnerMojo extends AbstractMojo {

    // Parameters for the plugin

    @Parameter(alias = "casperjs.executable", defaultValue = "casperjs")
    private String casperExec;

    @Parameter(alias = "tests.directory", defaultValue = "${basedir}/src/test/js")
    private File testsDir;

    @Parameter(alias = "features.directory", defaultValue = "${basedir}/src/test/features")
    private File featuresDirectory;

    @Parameter(alias = "libs.directory", defaultValue = "${basedir}/src/test/libs")
    private File libsDirectory;

    @Parameter(alias = "features.runner", defaultValue = "${basedir}/src/test/bdd.js")
    private String featuresRunner;

    @Parameter(alias = "ignoreTestFailures")
    private boolean ignoreTestFailures = false;

    @Parameter(alias = "verbose")
    private boolean verbose = false;

    // Parameters for the CasperJS options

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
        // Test CasperJS
        casperJsVersion = new DefaultArtifactVersion(checkVersion(casperExec));
        if (verbose) {
            log.info("CasperJS version: " + casperJsVersion);
        }
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        init();
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
        if (includeFeatures) {
            globalResult.add(executeFeatures());
        } else {
            log.info("Feature files ignored");
        }
        log.info(globalResult.print());
        if (!ignoreTestFailures && globalResult.getFailures() > 0) {
            throw new MojoFailureException("There are " + globalResult.getFailures() + " test failures");
        }
    }

    private Result executeFeatures() throws MojoExecutionException {
    	Result result = new Result();
    	Collection<File> featureFiles = getAllFeatureFiles();
        if (featureFiles == null || featureFiles.size() == 0) {
            log.warn("No .feature files found in directory " + testsDir);
        } else {
            for (File f : featureFiles) {
                log.debug("Execution of feature " + f.getName());
                String relativePath = featuresDirectory.toURI().relativize(f.toURI()).getPath();
                int res = executeFeature(f, relativePath);
                if (res == 0) {
                    result.addSuccess();
                } else {
                    log.warn("Feature '" + f.getName() + "' has failure");
                    result.addFailure();
                }
            }
        }
		return result;
	}

	private int executeFeature(File f, String relativePath) throws MojoExecutionException {
		File library = new File(libsDirectory, String.format("%s.js", pathWithoutExtension(relativePath)));
		if (!library.exists()) {
			throw new MojoExecutionException(String.format("Expected library [%s] but not found", library.getAbsolutePath()));
		}

		List<String> additionalArguments = new ArrayList<String>();
		additionalArguments.add(String.format("--feature=%s --lib=%s --libs-dir=%s", f.getAbsolutePath(), library.getAbsolutePath(), libsDirectory));
        return prepareAndExecuteCommand(f, featuresRunner, pathWithoutExtension(relativePath).replace('/', '.'), additionalArguments);
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

	private Collection<File> getAllFeatureFiles() {
		return listFiles(featuresDirectory, new String[] { "feature" }, true);
	}

    private int executeScript(File f) {
        return prepareAndExecuteCommand(f, f.getAbsolutePath(), pathWithoutExtension(f.getName()), new ArrayList<String>());
    }

	private int prepareAndExecuteCommand(File f, String executedFile, String xUnitBase, List<String> args) {
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
        command.append(' ').append(executedFile);
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
    
    private String pathWithoutExtension(String path) {
        int pos = path.lastIndexOf(".");
        if (pos > 0) {
            path = path.substring(0, pos);
        }
        return path;
    }

}
