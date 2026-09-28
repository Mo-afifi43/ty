package com.mhma.smartlearn.data.network.scraper

/** Reads an HTML/JSON sample from src/test/resources. */
internal fun fixture(name: String): String {
    val stream = Fixture::class.java.classLoader?.getResourceAsStream(name)
        ?: error("Missing test fixture: $name")
    return stream.bufferedReader().use { it.readText() }
}

private object Fixture
