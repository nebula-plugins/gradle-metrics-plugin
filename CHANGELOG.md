# 4.1.1

- Add transitive dependency as direct dependency

# 4.1.0

- Send reports from the InfoBroker plugin to dispatchers

# 4.0.1

- Add receipt information for REST dispatcher

# 4.0.0

- Fix issue #7: Be less paranoid about bubbling up exceptions as logged errors

# 3.3.0

- Generalize SURO dispatcher to REST dispatcher

# 3.2.4

- Fix an NPE in GradleCollector when a task doesn't include a state
- Improve logging collection cleanup logic

# 3.2.3

- Log an error rather than throw an exception when logging collection is not correctly cleaned up

# 3.2.2

- Recognise the new nebula.info-broker plugin id, so scm and ci information is captured again

# 3.2.1

- Add a precondition that identifies when the logging collector has not been reset. It turns out that Gradle 2.9 regressed the profile collector, so the dispatcher wasn't shutting down

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
