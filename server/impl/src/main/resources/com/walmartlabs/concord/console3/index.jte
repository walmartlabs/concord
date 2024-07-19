@param com.walmartlabs.concord.console3.PageContext pageContext

@template.layout(content = @`
    <main>
        <button hx-post="${pageContext.base()}/clicked" hx-swap="outerHTML">
            Click Me
        </button>
    </main>
`)