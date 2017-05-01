import express from "express";
import proxy from "express-http-proxy";

const app = express();

// proxy api requests to the backend server
app.all("/api/*", proxy("localhost:8001", {preserveHostHdr: true}));
app.all("/logs/*", proxy("localhost:8001", {preserveHostHdr: true}));
app.all("/forms/*", proxy("localhost:8001", {preserveHostHdr: true}));

// everything else goes to the default dev server
app.all("/*", proxy("localhost:3000", {preserveHostHdr: true}));

app.listen(8080);

console.log("Open http://localhost:8080");