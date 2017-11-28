// @flow
import type {ConcordKey} from "../../../types";
import * as common from "../../../api";

export const fetchSecretList = (orgName: ConcordKey): any => {
    return fetch(`/api/v1/org/${orgName}/secret`, {credentials: "same-origin"})
        .then(response => {
            if (!response.ok) {
                throw common.defaultError(response);
            }
            return response.json();
        })
        .then(json => {
            return json;
        });
};

export const deleteSecret = (orgName: ConcordKey, name: ConcordKey): any => {
    return fetch(`/api/v1/org/${orgName}/secret/${name}`, {method: "DELETE", credentials: "same-origin"})
        .then(response => {
            if (!response.ok) {
                throw new Error("ERROR: " + response.statusText + " (" + response.status + ")");
            }
            return true;
        });
};

export const getPublicKey = (orgName: ConcordKey, name: ConcordKey): any => {
    return fetch(`/api/v1/org/${orgName}/secret/${name}/public`, {method: "GET", credentials: "same-origin"})
        .then(response => {
            if (!response.ok) {
                throw new Error("ERROR: " + response.statusText + " (" + response.status + ")");
            }
            return response.json();
        });
};