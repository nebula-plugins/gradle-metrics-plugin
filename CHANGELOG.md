# 3.2.0

- Added integration with Suro
- Bug fixes

# 3.1.0

- sanitizedProperties extension property allows environment variables and system properties to be santitized
- events, tasks and tests now have corresponding Count and ElapsedTime values that sum values in those arrays

# 2.2.6

- Set 'standard' analyzer explicitly for the index to avoid issues with immense terms on instances that configure 'keyword' analyzers by default

# 2.2.5

- Improve dispatcher service startup and shutdown behaviour; particularly avoid leading ES threads due to a failed shutdown
- Index mapping change - maps were making their way into the document and extending mappings in unexpected ways
- Allow logging level to be configured via extension. Set default log level to WARN instead of INFO

# 2.2.4

- Disable _all field to prevent 'Document contains at least one immense term in field="_all"'

# 2.2.3

- Shade Guava to prevent side effects (occured internally with a buildSrc project bringing in gradleApi())

# 2.2.2

- Avoid possibility of a rare ConcurrentModificationException caused by concurrent logging events during LogstashLayout start

# 2.2.1

- Avoid potential heap utilisation issues by avoiding registering collectors (notably, logging collection) when Gradle is in offline mode or when the dispatcher service has failed

# 2.2.0

- Change to Nebulaeske versions tied to compatible Gradle version

# 1.0

- Initial release
