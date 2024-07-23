@import com.walmartlabs.concord.console3.UserContext
@import static com.walmartlabs.concord.console3.resources.ConsoleResource.API_PREFIX

@param UserContext userContext

@template.layout(userContext = userContext, content = @`
    <main>
        <button class="ui button" hx-post="${API_PREFIX}/click" hx-swap="outerHTML">
            Click Me
        </button>
    </main>
`)