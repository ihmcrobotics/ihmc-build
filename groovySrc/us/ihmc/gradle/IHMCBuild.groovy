package us.ihmc.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.GroovyPlugin
import org.gradle.api.plugins.JavaPlugin

/**
 * The {@code IHMCBuild) Plugin configures a project with conventions used in the IHMC Robotics Lab.
 * This includes a legacy source set structure that is atypical from many other projects.
 *
 * It also provides the {@code ihmc} extension implemented in the {@link IHMCBuildExtension} class,
 * which exposes some helper methods related to the way we manage our workspaces.
 *
 * @author Doug Stephen <a href="mailto:dstephen@ihmc.us">(dstephen@ihmc.us)</a>
 */
class IHMCBuild implements Plugin<Project> {

    @Override
    void apply(Project project) {
        addExtensions(project)

        setupSourceSetStructure(project)

        addTasks(project)
    }

    private void addExtensions(Project project) {
        project.extensions.create("ihmc", IHMCBuildExtension, project)
    }

    private void addTasks(Project project) {
        project.task('checkIHMCBuild') << {
            println "IHMC Build being applied from ${project.name}"
        }
    }

    private void setupSourceSetStructure(Project project) {
        if (project.plugins.hasPlugin(JavaPlugin)) {
            setupJavaSourceSets(project)
        }

        if (project.plugins.hasPlugin(GroovyPlugin)) {
            setupGroovySourceSets(project)
        }
    }

    private void setupGroovySourceSets(Project project) {
        project.sourceSets {
            main {
                groovy {
                    srcDirs = ['groovySrc']
                }

                resources {
                    srcDirs = ['src', 'resources']
                }
            }

            test {
                groovy {
                    srcDirs = ['groovyTest']
                }
            }
        }
    }

    private void setupJavaSourceSets(Project project) {
        project.sourceSets {
            main {
                java {
                    srcDirs = ['src']
                }

                resources {
                    srcDirs = ['src', 'resources']
                }
            }

            test {
                java {
                    srcDirs = ['test']
                }
            }
        }
    }
}
