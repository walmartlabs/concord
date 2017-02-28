// @flow
import type {ConcordId} from "../types";
import {authHeader, apiListQuery} from "./common";

export const fetchProjectList = apiListQuery("fetchProjectRows", "/api/v1/project");

export const fetchProject = (id: ConcordId) => {
    console.debug("API: getProject ['%s'] -> starting...", id);

    return fetch("/api/v1/project/" + id, {headers: authHeader})
        .then(response => {
            if (!response.ok) {
                throw new Error("ERROR: " + response.statusText + " (" + response.status + ")");
            }
            return response.json();
        })
        .then(json => {
            console.debug("API: getProject ['%s'] -> done, got %o", id, json);
            return json;
        });
};

export const createProject = (data: Object) => {
    console.debug("API: createProject [%o] -> starting...", data);

    const body = JSON.stringify(data);
    const contentType = {"Content-Type": "application/json"};
    const opts = {headers: Object.assign({}, authHeader, contentType), method: "POST", body: body};

    return fetch("/api/v1/project", opts)
        .then(response => {
            if (!response.ok) {
                throw new Error("ERROR: " + response.statusText + " (" + response.status + ")");
            }
            return response.json();
        })
        .then(json => {
            console.debug("API: createProject [%o] -> done, got %o", data, json);
            return json;
        });
};

export const updateProject = (id: ConcordId, data: Object) => {
    console.debug("API: updateProject ['%s', %o] -> starting...", id, data);

    const body = JSON.stringify(data);
    const contentType = {"Content-Type": "application/json"};
    const opts = {headers: Object.assign({}, authHeader, contentType), method: "PUT", body: body};

    return fetch("/api/v1/project/" + id, opts)
        .then(response => {
            if (!response.ok) {
                throw new Error("ERROR: " + response.statusText + " (" + response.status + ")");
            }
        })
        .then(json => {
            console.debug("API: updateProject ['%s', %o] -> done", id, data);
            return json;
        });
};

export const deleteProject = (id: ConcordId) => {
    console.debug("API: deleteProject ['%s'] -> starting...", id);

    return fetch("/api/v1/project/" + id, {headers: authHeader, method: "DELETE"})
        .then(response => {
            if (!response.ok) {
                throw new Error("ERROR: " + response.statusText + " (" + response.status + ")");
            }
            console.debug("API: deleteProject ['%s'] -> done", id);
            return true;
        });
};
