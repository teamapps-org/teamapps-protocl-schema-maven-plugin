/*-
 * ========================LICENSE_START=================================
 * TeamApps Protocol Schema Maven Plugin
 * ---
 * Copyright (C) 2022 TeamApps.org
 * ---
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =========================LICENSE_END==================================
 */
package org.teamapps.api.generator;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.*;
import org.apache.maven.project.MavenProject;

import java.util.List;

import static org.twdata.maven.mojoexecutor.MojoExecutor.*;


@Mojo(name = "generate-protocol-api",
		defaultPhase = LifecyclePhase.GENERATE_SOURCES,
		requiresDependencyResolution = ResolutionScope.COMPILE)
public class ProtocolGeneratorMojo extends AbstractMojo {

	@Component
	private MavenProject mavenProject;

	@Component
	private MavenSession mavenSession;

	@Component
	private BuildPluginManager pluginManager;

	@Parameter(defaultValue = "${maven.version}", readonly = true)
	private String mavenVersion;

	@Parameter(defaultValue = "${project.basedir}/src/main/model")
	private String modelSourceDirectory;

	@Parameter(defaultValue = "Protocol")
	private List<String> protocolClasses;

	@Parameter(defaultValue = "${project.build.directory}/generated-sources/protocol-model-api")
	private String generatorTargetDirectory;

	public void execute() throws MojoExecutionException {
		checkMavenVersion();
		compileModel();
		mavenProject.addCompileSourceRoot(generatorTargetDirectory);
		executeModelGenerator();
		mavenProject.addCompileSourceRoot(modelSourceDirectory);
	}

	private void checkMavenVersion() throws MojoExecutionException {
		String[] parts = mavenVersion.split("\\.");
		int[] versionParts = new int[3];
		for (int i = 0; i < Math.min(parts.length, versionParts.length); i++) {
			versionParts[i] = Integer.parseInt(parts[i]);
		}
		if (versionParts[0] * 1_000_000 + versionParts[1] * 1000 + versionParts[2] < 3_003_009) {
			String message = "Maven version needs to be at least 3.3.9 for teamapps-protocol-schema-maven-plugin to run! Your version is " + mavenVersion;
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
		if (protocolClasses == null || protocolClasses.isEmpty()) {
			getLog().error("Please specify the protocol classes (configuration parameter: \"modelClasses\") to generate!");
		}
		for (String modelClassName : protocolClasses) {
			getLog().info("Generating model " + modelClassName + ". generatorTargetDirectory: " + generatorTargetDirectory);

			executeMojo(
					plugin(
							groupId("org.codehaus.mojo"),
							artifactId("exec-maven-plugin"),
							version("1.3.2")
					),
					goal("java"),
					configuration(
							element(name("mainClass"), "org.teamapps.protocol.maven.ProtocolApiGenerator"),
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
