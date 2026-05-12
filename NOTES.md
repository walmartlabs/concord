# Development Notes

## Code Style

Prefer `var` in new code, but do not mix `var` and explicit local variable types in the same file.

## Git

Prefer commit subjects in the existing `module: short description` style.
Use comma-separated modules when needed, and keep the body minimal or omit it if the subject is already clear.

## Server Plugins

Prefer explicit binding using `com.google.inject.Module` over `@Named` annotations.
Use `@Named` for top-level modules and server plugins.

Server and server plugin types that currently require explicit binding using `Multibinder.newSetBinder`:
- ApiDescriptor
- AuditLogListener
- AuthenticationHandler
- BackgroundTask
- Component
- ContextHandlerConfigurator
- CustomEnqueueProcessor
- DatabaseChangeLogProvider
- ExceptionMapper
- ExternalEventTriggerProcessor
- Filter
- FilterChainConfigurator
- FilterHolder
- GaugeProvider
- GithubTriggerProcessor.EventEnricher
- HttpServlet
- ModeProcessor
- PolicyApplier
- ProcessEventListener
- ProcessLogListener
- ProcessStatusListener
- ProcessWaitHandler
- ProjectLoader
- Realm
- RepositoryRefreshListener
- RequestErrorHandler
- ScheduledTask
- SecretStore
- ServletContextListener
- ServletHolder
- UserInfoProvider
