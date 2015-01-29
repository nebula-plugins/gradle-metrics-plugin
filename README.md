# Gradle Build Metrics

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
