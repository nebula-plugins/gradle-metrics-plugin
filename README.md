# Gradle Metrics Plugin

[![Build Status](https://travis-ci.org/nebula-plugins/gradle-metrics-plugin.svg?branch=master)](https://travis-ci.org/nebula-plugins/gradle-metrics-plugin)
[![Coverage Status](https://coveralls.io/repos/nebula-plugins/gradle-metrics-plugin/badge.svg?branch=master&service=github)](https://coveralls.io/github/nebula-plugins/gradle-metrics-plugin?branch=master)
[![Gitter](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/nebula-plugins/gradle-metrics-plugin?utm_source=badgeutm_medium=badgeutm_campaign=pr-badge)
[![Apache 2.0](https://img.shields.io/github/license/nebula-plugins/gradle-metrics-plugin.svg)](http://www.apache.org/licenses/LICENSE-2.0)

An incubating plugin to collect Gradle build metrics, and persist them to an Elasticsearch index.

Top level metrics include:

* Info - Gradle start parameters, system properties and environment variables. SCM and GIT information if the gradle-info-plugin has been applied
* Project - name and version
* Events - configuration, dependency resolution, task execution
* Task executions - result, elapsed time per task
* Tests - result, elapsed time per test
* Result - success, failure with throwable, elapsed time

# Example Queries

## Builds by result status

    GET /build-metrics/build/_search?search_type=count
    {
        "aggs" : {
            "build_results" : {
                "terms" : { "field" : "result.status" }
            }
        }
    }

## Builds with failures

    GET /build-metrics/build/_search?search_type=count
    {
        "filter": {
            "term": { "result.status": "failure" }
        }
    }

## Builds that did not complete

    GET /build-metrics/build/_search?search_type=count
    {
        "filter": {
            "term": { "result.status": "unknown" }
        }
    }

## Builds with failed task executions

    GET /build-metrics/build/_search?search_type=count
    {
        "filter": {
            "nested": {
                "path": "tasks",
                "filter": {
                    "term": { "tasks.result.status": "failure" }
                }
            }
        }
    }

## Builds with test failures

    GET /build-metrics/build/_search?search_type=count
    {
        "filter": {
            "nested": {
                  "path": "tests",
                  "filter": {
                        "term": { "tests.result.status": "failure" }
                  }
            }
        }
    }

## Average elapsed time by event type

    GET build-metrics/build/_search?search_type=count
    {
        "aggs" : {
            "elapsedByType" : {
                "nested" : {
                    "path" : "events"
                },
                "aggs" : {
                    "eventsByType" : {
                        "terms": { "field": "events.type" },
                        "aggs": {
                            "elapsedTime": {
                                "avg" : { "field" : "events.elapsedTime" }
                            }
                        }
                    }
                }
            }
        }
    }

## Average elapsed time for a given event type

    GET build-metrics/build/_search?search_type=count
    {
        "aggs" : {
            "elapsedByType" : {
                "nested" : {
                    "path" : "events"
                },
                "aggs" : {
                    "eventsByType" : {
                        "filter": {
                            "term": { "events.type": "<TYPE>" }
                        },
                        "aggs": {
                            "elapsedTime": {
                                "sum" : { "field" : "events.elapsedTime" }
                            }
                        }
                    }
                }
            }
        }
    }

Where `<TYPE>` is one of `init`, `configure`, `resolve`, `execution` or the exceptional case, `unknown`.
