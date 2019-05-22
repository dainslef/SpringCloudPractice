package dainslef.cloud.base

interface Logger {
    val logger get() = org.slf4j.LoggerFactory.getLogger(javaClass)
}
