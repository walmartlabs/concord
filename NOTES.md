# Development Notes

## Code Style

If possible, use `var` in new code.
Do not mix `var` and explicit local variable types in the same file.

## Git

If possible, use the existing `module: short description` style for commit subjects.
If you specify more than one module, separate the module names with commas.
Keep the commit body short. You can omit the body if the subject is clear.

## Server Plugins

If possible, use explicit binding with `com.google.inject.Module` instead of `@Named` annotations.
Use `@Named` for top-level modules and server plugins.

These server and server plugin types require explicit binding with `Multibinder.newSetBinder`:
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
