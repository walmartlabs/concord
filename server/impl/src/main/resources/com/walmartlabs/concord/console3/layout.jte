@import com.walmartlabs.concord.console3.UserContext
@import gg.jte.Content
@param UserContext userContext = null
@param Content content
<!DOCTYPE html>
<html>
<head>
    <title>Concord</title>
    <script src="/console3/htmx.min.js"></script>
    <link rel="stylesheet" href="/console3/modern-normalize.min.css">
    <link rel="icon" type="image/x-icon" href="/favicon.png">
</head>
<body>
@if(userContext != null)
    Hi, ${userContext.displayName() != null ? userContext.displayName() : userContext.username()}!
    ${content}
@else
    Hi, guest! Please, <a href="/#/login?from=/console3/">log in</a>.
@endif
</body>
</html>
