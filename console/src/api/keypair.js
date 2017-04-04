// @flow
import type {ConcordId} from "../types";
import {authHeader, apiListQuery, defaultError} from "./common";

export const fetchKeypairList = apiListQuery("fetchKeypairRows", "/api/v1/secret");

export const fetchKeypair = (id: ConcordId) => {
    console.debug("API: fetchKeypair ['%s'] -> starting...", id);

    return fetch(`/api/v1/secret/${id}/public`, {headers: authHeader})
        .then(response => {
            if (!response.ok) {
                throw new defaultError(response);
            }
            return response.json();
        })
        .then(json => {
            console.debug("API: fetchKeypair ['%s'] -> done, got %o", id, json);
            return json;
        });
};

export const deleteKeypair = (id: ConcordId) => {
    console.debug("API: deleteKeypair ['%s'] -> starting...", id);

    return fetch(`/api/v1/secret/${id}`, {headers: authHeader, method: "DELETE"})
        .then(response => {
            if (!response.ok) {
                throw new Error("ERROR: " + response.statusText + " (" + response.status + ")");
            }
            console.debug("API: deleteKeypair ['%s'] -> done", id);
            return true;
        });
};
