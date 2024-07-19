@import com.walmartlabs.concord.console3.PageContext
@import com.walmartlabs.concord.console3.UserContext

@param PageContext pageContext
@param UserContext userContext

@template.layout(userContext = userContext, content = @`
    <main>
        <button hx-post="${pageContext.base()}/clicked" hx-swap="outerHTML">
            Click Me
        </button>
    </main>
`)