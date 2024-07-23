@import com.walmartlabs.concord.console3.model.UserContext
@import gg.jte.Content
@param UserContext userContext = null
@param Content content
<!DOCTYPE html>
<html>
<head>
    <title>Concord</title>
    <script src="/console3/htmx.min.js"></script>
    <link rel="stylesheet" type="text/css" href="/console3/semantic.min.css">
    <link rel="icon" type="image/x-icon" href="/favicon.png">
</head>
<body>
@if(userContext != null)
    Hi, ${userContext.displayName() != null ? userContext.displayName() : userContext.username()}!
    ${content}
@else
    Hi, guest! Please, <a href="/#/login?from=/console3/">log in</a>.
@endif
    <script src="https://code.jquery.com/jquery-3.1.1.min.js"
        integrity="sha256-hVVnYaiADRTO2PzUGmuLJr8BLUSjGIZsDYGmIJLv2b8="
        crossorigin="anonymous"></script>
    <script src="/console3/semantic.min.js"></script>
</body>
</html>
