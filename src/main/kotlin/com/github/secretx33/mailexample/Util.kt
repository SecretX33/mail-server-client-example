package com.github.secretx33.mailexample

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.ObjectWriter
import com.fasterxml.jackson.databind.module.SimpleModule
import io.github.secretx33.resourceresolver.PathMatchingResourcePatternResolver
import java.lang.management.ManagementFactory
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.exists

val jackson: ObjectMapper by lazy {
    ObjectMapper().findAndRegisterModules()
        .applyProjectDefaults()
}

val prettyJackson: ObjectWriter by lazy { jackson.writerWithDefaultPrettyPrinter() }

fun ObjectMapper.applyProjectDefaults(): ObjectMapper = apply {
    registerModule(SimpleModule().apply {
        addAbstractTypeMapping(Set::class.java, LinkedHashSet::class.java)
    })
    setSerializationInclusion(JsonInclude.Include.NON_NULL)
    disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    enable(DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
}

private val resourceLoader by lazy { PathMatchingResourcePatternResolver() }

fun getResourceAsString(name: String): String {
    val resource = listOf(name, "classpath:$name", "file:$name")
        .map(resourceLoader::getResource)
        .firstOrNull { it.exists() }
        ?: throw IllegalArgumentException("Resource named '$name' was not found")
    return resource.inputStream.bufferedReader().use { it.readText() }
}

fun Path.createFileIfNotExists(): Path {
    if (exists()) return this
    parent?.createDirectories()
    return createFile()
}

val threadAmount: Int by lazy { ManagementFactory.getThreadMXBean().threadCount }