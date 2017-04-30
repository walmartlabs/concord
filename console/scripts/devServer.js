import express from "express";
import proxy from "express-http-proxy";

const app = express();

// proxy api requests to the backend server
app.all("/api/*", proxy("localhost:8001"));
app.all("/logs/*", proxy("localhost:8001"));
app.all("/forms/*", proxy("localhost:8001"));

// everything else goes to the default dev server
app.all("/*", proxy("localhost:3000"));

app.listen(8080);

console.log("Open http://localhost:8080");