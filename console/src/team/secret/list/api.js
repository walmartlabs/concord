// @flow
import type {ConcordKey} from "../../../types";
import * as common from "../../../api";

export const fetchSecretList = (teamName: ConcordKey): any => {
    return fetch(`/api/v1/team/${teamName}/secret`, {credentials: "same-origin"})
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

export const deleteSecret = (teamName: ConcordKey, name: ConcordKey): any => {
    return fetch(`/api/v1/team/${teamName}/secret/${name}`, {method: "DELETE", credentials: "same-origin"})
        .then(response => {
            if (!response.ok) {
                throw new Error("ERROR: " + response.statusText + " (" + response.status + ")");
            }
            return true;
        });
};

export const getPublicKey = (teamName: ConcordKey, name: ConcordKey): any => {
    return fetch(`/api/v1/team/${teamName}/secret/${name}/public`, {method: "GET", credentials: "same-origin"})
        .then(response => {
            if (!response.ok) {
                throw new Error("ERROR: " + response.statusText + " (" + response.status + ")");
            }
            return response.json();
        });
};