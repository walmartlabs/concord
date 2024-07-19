@import com.walmartlabs.concord.console3.UserContext
@import gg.jte.Content
@param UserContext userContext = null
@param Content content
<head>
    <title>Concord</title>
    <script src="https://unpkg.com/htmx.org@2.0.1"></script>
</head>
<body>
@if(userContext != null)
Hi, ${userContext.displayName() != null ? userContext.displayName() : userContext.username()}!
@else
Hi, guest!
@endif
${content}
</body>
