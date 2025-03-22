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
- CustomBeanMethodResolver
- CustomEnqueueProcessor
- CustomTaskMethodResolver
- ExceptionMapper
- ExecutionListener
- Filter
- FilterChainConfigurator
- FilterHolder
- GaugeProvider
- HttpServlet
- LogAppender
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
- TaskCallListener
- TaskProvider
- UserInfoProvider
