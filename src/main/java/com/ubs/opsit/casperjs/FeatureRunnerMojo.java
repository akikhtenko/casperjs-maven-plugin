package com.ubs.opsit.casperjs;

import static org.apache.commons.io.FileUtils.copyInputStreamToFile;
import static org.apache.commons.io.FileUtils.listFiles;
import static org.apache.commons.io.IOCase.INSENSITIVE;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.commons.io.filefilter.TrueFileFilter;
import org.apache.commons.io.filefilter.WildcardFileFilter;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

@Mojo(name = "integration-test", defaultPhase = LifecyclePhase.INTEGRATION_TEST, threadSafe=true)
public class FeatureRunnerMojo extends AbstractRunnerMojo {

    @Parameter(alias = "features.directory", defaultValue = "${basedir}/src/test/features")
    private File featuresDirectory;

    @Parameter(alias = "libs.directory", defaultValue = "${basedir}/src/test/libs")
    private File libsDirectory;

    @Parameter(alias = "yadda.directory", defaultValue = "${basedir}/src/test/yadda")
    private File yaddaDirectory;
    
    @Parameter(alias = "feature.runner", defaultValue = "${project.build.directory}/bdd.js")
    private File featureRunner;

    private Log log = getLog();

	public Result run() {
    	Collection<File> featureFiles = getAllFeatureFilesToRun();
        if (featureFiles == null || featureFiles.size() == 0) {
            log.warn("No .feature files found in directory " + featuresDirectory);
            return new Result();
        } 

        createFeatureRunnerIfNecessary();
        return executeAllFeaturesIn(featureFiles);
	}
	
    private void createFeatureRunnerIfNecessary() {
    	if (!featureRunner.exists()) {
			try {
				copyInputStreamToFile(getClass().getResourceAsStream("/bdd.js"), featureRunner);
			} catch (IOException e) {
				throw new RuntimeException("Could not create feature runner", e);
			}
    	}
	}

	private Result executeAllFeaturesIn(Collection<File> featureFiles) {
		Result result = new Result();
		for (File f : featureFiles) {
		    log.debug("Execution of a feature " + f.getName());
		    String relativePath = featuresDirectory.toURI().relativize(f.toURI()).getPath();
		    int res = executeFeature(f, relativePath);
		    if (res == 0) {
		        result.addSuccess();
		    } else {
		        log.warn("Feature '" + f.getName() + "' has a failure");
		        result.addFailure();
		    }
		}
		return result;
	}

	private int executeFeature(File f, String relativePath) {
		File library = new File(libsDirectory, String.format("%s.js", pathWithoutExtension(relativePath)));
		if (!library.exists()) {
			throw new RuntimeException(String.format("Expected library [%s] but was not found", library.getAbsolutePath()));
		}

		List<String> additionalArguments = new ArrayList<String>();
		additionalArguments.add(String.format("--feature=%s --lib=%s --libs-dir=%s --yadda-dir=%s", 
				f.getAbsolutePath(), library.getAbsolutePath(), libsDirectory, yaddaDirectory));
        return prepareAndExecuteCommand(featureRunner, pathWithoutExtension(relativePath).replace('/', '.'), additionalArguments);
	}

	private Collection<File> getAllFeatureFilesToRun() {
		String featurePattern = System.getProperty("feature");
		if (featurePattern != null) {
			String featureWildcard = featurePattern + ".feature";
			return listFiles(featuresDirectory, new WildcardFileFilter(featureWildcard, INSENSITIVE), TrueFileFilter.INSTANCE);
		}
		return listFiles(featuresDirectory, new String[] { "feature" }, true);
	}

}
