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
