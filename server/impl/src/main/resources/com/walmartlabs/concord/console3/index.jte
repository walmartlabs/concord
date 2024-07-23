@import com.walmartlabs.concord.console3.model.UserContext
@import static com.walmartlabs.concord.console3.resources.ConsoleResource.API_PREFIX

@param UserContext userContext

@template.layout(userContext = userContext, content = @`
    <main>
        <button class="ui button" hx-get="${API_PREFIX}/profile/organizations" hx-swap="outerHTML">
            Click Me
        </button>
    </main>
`)