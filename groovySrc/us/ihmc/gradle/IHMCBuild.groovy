package us.ihmc.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.GroovyPlugin
import org.gradle.api.plugins.JavaPlugin

/**
 * Created by dstephen on 1/26/16.
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
        if(project.plugins.hasPlugin(JavaPlugin))
        {
            setupJavaSourceSets(project)
        }

        if(project.plugins.hasPlugin(GroovyPlugin))
        {
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
