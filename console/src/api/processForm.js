// @flow
import type {ConcordId} from "../types";
import {authHeader, defaultError} from "./common";

export const listForms = (processInstanceId: ConcordId) => {
    console.debug("API: listForms ['%s'] -> starting...", processInstanceId);
    return fetch(`/api/v1/process/${processInstanceId}/form`, {headers: authHeader})
        .then(response => {
            if (!response.ok) {
                throw defaultError(response);
            }
            return response.json();
        })
        .then(json => {
            console.debug("API: listForms ['%s'] -> done, got %o", processInstanceId, json);
            return json;
        });
};

export const fetchForm = (processInstanceId: ConcordId, formInstanceId: ConcordId) => {
    console.debug("API: fetchForm ['%s', '%s'] -> starting...", processInstanceId, formInstanceId);
    return fetch(`/api/v1/process/${processInstanceId}/form/${formInstanceId}`, {headers: authHeader})
        .then(response => {
            if (!response.ok) {
                throw new defaultError(response);
            }
            return response.json();
        })
        .then(json => {
            console.debug("API: fetchForm ['%s', '%s'] -> done, got %o", processInstanceId, formInstanceId, json);
            return json;
        });
};

export const submitForm = (processInstanceId: ConcordId, formInstanceId: ConcordId, data: mixed) => {
    console.debug("API: submitForm ['%s', '%s', %o] -> starting...", processInstanceId, formInstanceId, data);

    const body = JSON.stringify(data);
    const contentType = {"Content-Type": "application/json"};
    const opts = {
        method: "POST",
        headers: Object.assign({}, authHeader, contentType),
        body: body
    };

    return fetch(`/api/v1/process/${processInstanceId}/form/${formInstanceId}`, opts)
        .then(response => {
            if (!response.ok) {
                throw new defaultError(response);
            }
            return response.json();
        })
        .then(json => {
            console.debug("API: submitForm ['%s','%s', %o] -> done, got %o", processInstanceId, formInstanceId, data, json);
            return json;
        });
};