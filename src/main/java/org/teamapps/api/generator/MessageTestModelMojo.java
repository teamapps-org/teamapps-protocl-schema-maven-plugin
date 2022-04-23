package org.teamapps.api.generator;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.*;
import org.apache.maven.project.MavenProject;

import java.util.List;

import static org.twdata.maven.mojoexecutor.MojoExecutor.*;


@Mojo(name = "generate-message-test-model",
		defaultPhase = LifecyclePhase.GENERATE_TEST_SOURCES,
		requiresDependencyResolution = ResolutionScope.COMPILE)
public class MessageTestModelMojo extends AbstractMojo {

	@Component
	private MavenProject mavenProject;

	@Component
	private MavenSession mavenSession;

	@Component
	private BuildPluginManager pluginManager;

	@Parameter(defaultValue = "${maven.version}", readonly = true)
	private String mavenVersion;

	@Parameter(defaultValue = "${project.basedir}/src/test/model")
	private String modelSourceDirectory;

	@Parameter(defaultValue = "MessageModel")
	private List<String> modelClasses;

	@Parameter(defaultValue = "${project.build.directory}/generated-test-sources/message-model-api")
	private String generatorTargetDirectory;

	public void execute() throws MojoExecutionException {
		checkMavenVersion();
		compileModel();
		mavenProject.addTestCompileSourceRoot(generatorTargetDirectory);
		executeModelGenerator();
		mavenProject.addTestCompileSourceRoot(modelSourceDirectory);
	}

	private void checkMavenVersion() throws MojoExecutionException {
		String[] parts = mavenVersion.split("\\.");
		int[] versionParts = new int[3];
		for (int i = 0; i < Math.min(parts.length, versionParts.length); i++) {
			versionParts[i] = Integer.parseInt(parts[i]);
		}
		if (versionParts[0] * 1_000_000 + versionParts[1] * 1000 + versionParts[2] < 3_003_009) {
			String message = "Maven version needs to be at least 3.3.9 for teamapps-schema-generator-plugin to run! Your version is " + mavenVersion;
			getLog().error(message);
			throw new MojoExecutionException(message);
		}
	}

	private void compileModel() throws MojoExecutionException {
		getLog().info("Compiling model directory: " + modelSourceDirectory);

		MavenProject projectCopy = this.mavenProject.clone();
		List<String> compileSourceRoots = projectCopy.getCompileSourceRoots();
		compileSourceRoots.clear();
		executeMojo(
				plugin(
						groupId("org.apache.maven.plugins"),
						artifactId("maven-compiler-plugin"),
						version("3.5.1")
				),
				goal("compile"),
				configuration(
						element(name("compileSourceRoots"),
								element("compileSourceRoot", modelSourceDirectory)
						)
				),
				executionEnvironment(
						projectCopy,
						mavenSession.clone(),
						pluginManager
				)
		);
	}

	private void executeModelGenerator() throws MojoExecutionException {
		if (modelClasses == null || modelClasses.isEmpty()) {
			getLog().error("Please specify the model classes (configuration parameter: \"modelClasses\") to generate!");
		}
		for (String modelClassName : modelClasses) {
			getLog().info("Generating model " + modelClassName + ". generatorTargetDirectory: " + generatorTargetDirectory);

			executeMojo(
					plugin(
							groupId("org.codehaus.mojo"),
							artifactId("exec-maven-plugin"),
							version("1.3.2")
					),
					goal("java"),
					configuration(
							element(name("mainClass"), "org.teamapps.protocol.maven.ModelApiGenerator"),
							element(name("commandlineArgs"), modelClassName + " \"" + generatorTargetDirectory + "\"")
					),
					executionEnvironment(
							mavenProject,
							mavenSession.clone(),
							pluginManager
					)
			);
		}
	}
}
