# Gradle Metrics Plugin

[![Build Status](https://travis-ci.org/nebula-plugins/gradle-metrics-plugin.svg?branch=master)](https://travis-ci.org/nebula-plugins/gradle-metrics-plugin)
[![Coverage Status](https://coveralls.io/repos/nebula-plugins/gradle-metrics-plugin/badge.svg?branch=master&service=github)](https://coveralls.io/github/nebula-plugins/gradle-metrics-plugin?branch=master)
[![Gitter](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/nebula-plugins/gradle-metrics-plugin?utm_source=badgeutm_medium=badgeutm_campaign=pr-badge)
[![Apache 2.0](https://img.shields.io/github/license/nebula-plugins/gradle-metrics-plugin.svg)](http://www.apache.org/licenses/LICENSE-2.0)

Collect Gradle build metrics and persist them to an external datastore. 

# Usage

To include, add the following to your build.gradle:

If newer than Gradle 2.1 you may use

    plugins {
        id 'nebula.metrics' version '3.2.0'
    }

*or*

    buildscript {
        repositories { jcenter() }

        dependencies {
            classpath 'com.netflix.nebula:gradle-metrics-plugin:3.2.0'
        }
    }

    apply plugin: 'nebula.metrics'

The metrics plugin can persist data into [Elasticsearch](https://www.elastic.co/products/elasticsearch) or [Suro](https://github.com/Netflix/suro). By default, the plugin will attempt to persist onto a local Elasticsearch instance on port `9200` with the templates under `templates/` applied. 

# Configuration

You may configure the plugin to use Elasticsearch or Suro for data persistence. Configuration should be done via the `metrics` Gradle extension. 
 
### Elasticsearch configuration  
 
    metrics {
        hostname = 'myescluster'    // default is 'localhost'
        httpPort = 59300            // default is 9200
        indexName = 'myindexname'   // default is 'build-metrics-default'
    }

### Suro configuration 
 
    metrics {
        hostname = 'mysuro'                     // default is 'localhost'
        suroPort = 5555                         // default is 443
        suroBuildEventName = 'my_build_events'  // default is 'build_metrics'
        suroLogEventName = 'my_log_events'      // default is 'build_metrics_events'              
    } 

# Metrics

Metrics include:

* Info - Gradle start parameters, system properties and environment variables. SCM and GIT information if the gradle-info-plugin has been applied
* Project - name and version
* Events - configuration, dependency resolution, task execution
* Task executions - result, elapsed time per task
* Tests - result, elapsed time per test
* Result - success, failure with throwable, elapsed time

# Example Elasticsearch Queries

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

LICENSE
=======

Copyright 2015 Netflix, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

<http://www.apache.org/licenses/LICENSE-2.0>

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
