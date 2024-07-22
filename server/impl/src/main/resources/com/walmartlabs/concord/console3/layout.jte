@import com.walmartlabs.concord.console3.UserContext
@import gg.jte.Content
@param UserContext userContext = null
@param Content content
<!DOCTYPE html>
<html>
<head>
    <title>Concord</title>
    <script src="/console3/htmx.min.js"></script>
</head>
<body>
@if(userContext != null)
    Hi, ${userContext.displayName() != null ? userContext.displayName() : userContext.username()}!
@else
    Hi, guest!
@endif
${content}
</body>
</html>
