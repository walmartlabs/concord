// @flow
import type {ConcordId} from "../types";
import {authHeader, apiListQuery, defaultError} from "./common";

export const fetchSecretList = apiListQuery("fetchSecretRows", "/api/v1/secret");

export const fetchSecret = (id: ConcordId) => {
    console.debug("API: fetchSecret ['%s'] -> starting...", id);

    return fetch(`/api/v1/secret/${id}/public`, {headers: authHeader})
        .then(response => {
            if (!response.ok) {
                throw new defaultError(response);
            }
            return response.json();
        })
        .then(json => {
            console.debug("API: fetchSecret ['%s'] -> done, got %o", id, json);
            return json;
        });
};

export const deleteSecret = (id: ConcordId) => {
    console.debug("API: deleteSecret ['%s'] -> starting...", id);

    return fetch(`/api/v1/secret/${id}`, {headers: authHeader, method: "DELETE"})
        .then(response => {
            if (!response.ok) {
                throw new Error("ERROR: " + response.statusText + " (" + response.status + ")");
            }
            console.debug("API: deleteSecret ['%s'] -> done", id);
            return true;
        });
};
