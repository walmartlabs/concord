// @flow
import type {ConcordId} from "../types";
import * as common from "../api";

export const fetchStatus = (id: ConcordId) => {
    console.debug("API: fetchStatus ['%s'] -> starting...", id);
    return fetch(`/api/v1/process/${id}`, {credentials: "same-origin"})
        .then(response => {
            if (!response.ok) {
                throw new common.defaultError(response);
            }

            return response.json();
        })
        .then(json => {
            console.debug("API: fetchStatus ['%s'] -> done: %o", id, json);
            return json;
        });
};

export const kill = (id: ConcordId) => {
    console.debug("API: killProc ['%s'] -> starting...", id);

    return fetch(`/api/v1/process/${id}`, {credentials: "same-origin", method: "DELETE"})
        .then(response => {
            if (!response.ok) {
                throw new common.defaultError(response);
            }
            console.debug("API: killProc ['%s'] -> done", id);
            return true;
        });
};

export const listForms = (instanceId: ConcordId) => {
    console.debug("API: listForms ['%s'] -> starting...", instanceId);
    return fetch(`/api/v1/process/${instanceId}/form`, {credentials: "same-origin"})
        .then(response => {
            if (!response.ok) {
                throw common.defaultError(response);
            }
            return response.json();
        })
        .then(json => {
            console.debug("API: listForms ['%s'] -> done, got %o", instanceId, json);
            return json;
        });
};

export const start = (entryPoint: string) => {
    console.debug("API: start ['%s'] -> starting...", entryPoint);

    const body = JSON.stringify({});
    const contentType = {"Content-Type": "application/json"};
    const opts = {
        method: "POST",
        credentials: "same-origin",
        headers: contentType,
        body: body
    };

    return fetch(`/api/v1/process/${entryPoint}`, opts)
        .then(response => {
            if (!response.ok) {
                throw new common.defaultError(response);
            }
            return response.json();
        })
        .then(json => {
            console.debug("API: start ['%s'] -> done, got %o", entryPoint, json);
            return json;
        });
};
