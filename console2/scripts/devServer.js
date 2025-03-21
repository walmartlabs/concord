import express from "express";
import proxy from "express-http-proxy";

const app = express();

const noCache = (reqOpts) => {
    reqOpts.headers["Cache-Control"] = "no-cache, no-store, must-revalidate";
    reqOpts.headers["Pragma"] = "no-cache";
    reqOpts.headers["Expires"] = "0";
    return reqOpts;
};

const defaultOpts = {
    preserveHostHdr: true,
    proxyReqOptDecorator: noCache
};

// proxy api requests to the backend server
app.all("/api/*", proxy("localhost:8001", defaultOpts));

app.all("/forms/*", proxy("localhost:8001", defaultOpts));

// everything else goes to the default dev server
app.all("/*", proxy("localhost:3000", defaultOpts));

app.listen(8080);

console.log("Open http://localhost:8080");