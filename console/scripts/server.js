import express from "express";
import * as path from "path";
import proxy from "express-http-proxy";

const app = express();

// static files
app.use(express.static("build"));

// proxy api requests to the backend server
app.all("/api/*", proxy("localhost:8001"));
app.all("/logs/*", proxy("localhost:8001"));
app.all("/forms/*", proxy("localhost:8001"));

// redirect everything else to index.html
app.get("*", (req, resp) => {
    resp.sendFile(path.resolve(__dirname, "..", "build", "index.html"));
});

app.listen(8080);