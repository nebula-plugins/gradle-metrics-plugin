# 2.2.2

- Avoid possibility of a rare ConcurrentModificationException caused by concurrent logging events during LogstashLayout start

# 2.2.1

- Avoid potential heap utilisation issues by avoiding registering collectors (notably, logging collection) when Gradle is in offline mode or when the dispatcher service has failed

# 2.2.0

- Change to Nebulaeske versions tied to compatible Gradle version

# 1.0

- Initial release
