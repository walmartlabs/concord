# Development Notes

## Concord Server and Server Plugins

Prefer explicit binding using `com.google.inject.Module` over `@Named` annotations.
Use `@Named` for top-level modules and server plugins.

Some classes require explicit binding using `Multibinder.newSetBinder`:
- ApiDescriptor
- AuditLogListener
- AuthenticationHandler
- BackgroundTask
- Component
- ContextHandlerConfigurator
- CustomEnqueueProcessor
- ExceptionMapper
- Filter
- FilterChainConfigurator
- FilterHolder
- GaugeProvider
- HttpServlet
- ModeProcessor
- PolicyApplier
- ProcessEventListener
- ProcessLogListener
- ProcessStatusListener
- ProcessWaitHandler
- Realm
- RepositoryRefreshListener
- RequestErrorHandler
- ScheduledTask
- SecretStore
- ServletContextListener
- ServletHolder
- UserInfoProvider