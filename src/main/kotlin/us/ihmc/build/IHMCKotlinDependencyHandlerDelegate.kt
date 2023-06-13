package us.ihmc.build;

import groovy.lang.Closure

import org.gradle.api.Action
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.dsl.*
import org.gradle.api.artifacts.query.ArtifactResolutionQuery
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.artifacts.transform.TransformSpec
import org.gradle.api.artifacts.type.ArtifactTypeContainer
import org.gradle.api.attributes.AttributesSchema
import org.gradle.api.plugins.ExtensionContainer
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderConvertible

/**
 * Facilitates the implementation of the [DependencyHandler] interface by delegation via subclassing.
 *
 * See [GradleDelegate] for why this is currently necessary.
 */
abstract class IHMCKotlinDependencyHandlerDelegate : DependencyHandler {

    internal
    abstract val delegate: DependencyHandler

    override fun getExtensions(): ExtensionContainer =
            delegate.extensions

    override fun add(configurationName: String, dependencyNotation: Any): Dependency? =
        delegate.add(configurationName, dependencyNotation)

    override fun add(configurationName: String, dependencyNotation: Any, configureClosure: Closure<*>): Dependency? =
        delegate.add(configurationName, dependencyNotation, configureClosure)

    override fun <T : Any?, U : ExternalModuleDependency?> addProvider(configurationName: String, dependencyNotation: Provider<T>, configuration: Action<in U>) =
        delegate.addProvider(configurationName, dependencyNotation, configuration)

    override fun <T : Any?> addProvider(configurationName: String, dependencyNotation: Provider<T>) =
        delegate.addProvider(configurationName, dependencyNotation)

    override fun <T : Any?, U : ExternalModuleDependency?> addProviderConvertible(configurationName: String, dependencyNotation: ProviderConvertible<T>, configuration: Action<in U>) =
        delegate.addProviderConvertible(configurationName, dependencyNotation, configuration)

    override fun <T : Any?> addProviderConvertible(configurationName: String, dependencyNotation: ProviderConvertible<T>) =
        delegate.addProviderConvertible(configurationName, dependencyNotation)

    override fun create(dependencyNotation: Any): Dependency =
        delegate.create(dependencyNotation)

    override fun create(dependencyNotation: Any, configureClosure: Closure<Any>): Dependency =
        delegate.create(dependencyNotation, configureClosure)

    override fun module(notation: Any): Dependency =
        delegate.module(notation)

    override fun module(notation: Any, configureClosure: Closure<Any>): Dependency =
        delegate.module(notation, configureClosure)

    override fun project(notation: Map<String, *>): Dependency =
        delegate.project(notation)

    override fun gradleApi(): Dependency =
        delegate.gradleApi()

    override fun gradleTestKit(): Dependency =
        delegate.gradleTestKit()

    override fun localGroovy(): Dependency =
        delegate.localGroovy()

    override fun getConstraints(): DependencyConstraintHandler =
        delegate.constraints

    override fun constraints(configureAction: Action<in DependencyConstraintHandler>) =
        delegate.constraints(configureAction)

    override fun getComponents(): ComponentMetadataHandler =
        delegate.components

    override fun components(configureAction: Action<in ComponentMetadataHandler>) =
        delegate.components(configureAction)

    override fun getModules(): ComponentModuleMetadataHandler =
        delegate.modules

    override fun modules(configureAction: Action<in ComponentModuleMetadataHandler>) =
        delegate.modules(configureAction)

    override fun createArtifactResolutionQuery(): ArtifactResolutionQuery =
        delegate.createArtifactResolutionQuery()

    override fun attributesSchema(configureAction: Action<in AttributesSchema>): AttributesSchema =
        delegate.attributesSchema(configureAction)

    override fun getAttributesSchema(): AttributesSchema =
        delegate.attributesSchema

    override fun getArtifactTypes(): ArtifactTypeContainer =
        delegate.artifactTypes

    override fun artifactTypes(configureAction: Action<in ArtifactTypeContainer>) =
        delegate.artifactTypes(configureAction)

    override fun <T : TransformParameters?> registerTransform(actionType: Class<out TransformAction<T>>, registrationAction: Action<in TransformSpec<T>>) =
        delegate.registerTransform(actionType, registrationAction)

    override fun platform(notation: Any): Dependency =
        delegate.platform(notation)

    override fun platform(notation: Any, configureAction: Action<in Dependency>): Dependency =
        delegate.platform(notation, configureAction)

    override fun enforcedPlatform(notation: Any): Dependency =
        delegate.enforcedPlatform(notation)

    override fun enforcedPlatform(notation: Any, configureAction: Action<in Dependency>): Dependency =
        delegate.enforcedPlatform(notation, configureAction)

    override fun enforcedPlatform(dependencyProvider: Provider<MinimalExternalModuleDependency>): Provider<MinimalExternalModuleDependency> =
        delegate.enforcedPlatform(dependencyProvider)

    override fun testFixtures(notation: Any, configureAction: Action<in Dependency>): Dependency =
        delegate.testFixtures(notation, configureAction)

    override fun variantOf(dependencyProvider: Provider<MinimalExternalModuleDependency>, variantSpec: Action<in ExternalModuleDependencyVariantSpec>): Provider<MinimalExternalModuleDependency> =
        delegate.variantOf(dependencyProvider, variantSpec)

    override fun testFixtures(notation: Any): Dependency =
        delegate.testFixtures(notation)
}
