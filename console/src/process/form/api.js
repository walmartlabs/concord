// @flow
import type {ConcordId} from "../../types";
import {defaultError} from "../../api";

export const listForms = (instanceId: ConcordId) => {
    console.debug("API: listForms ['%s'] -> starting...", instanceId);
    return fetch(`/api/v1/process/${instanceId}/form`, {credentials: "same-origin"})
        .then(response => {
            if (!response.ok) {
                throw defaultError(response);
            }
            return response.json();
        })
        .then(json => {
            console.debug("API: listForms ['%s'] -> done, got %o", instanceId, json);
            return json;
        });
};

export const fetchForm = (instanceId: ConcordId, formInstanceId: ConcordId) => {
    console.debug("API: fetchForm ['%s', '%s'] -> starting...", instanceId, formInstanceId);
    return fetch(`/api/v1/process/${instanceId}/form/${formInstanceId}`, {credentials: "same-origin"})
        .then(response => {
            if (!response.ok) {
                throw new defaultError(response);
            }
            return response.json();
        })
        .then(json => {
            console.debug("API: fetchForm ['%s', '%s'] -> done, got %o", instanceId, formInstanceId, json);
            return json;
        });
};

export const submitForm = (instanceId: ConcordId, formInstanceId: ConcordId, data: mixed) => {
    console.debug("API: submitForm ['%s', '%s', %o] -> starting...", instanceId, formInstanceId, data);

    const body = JSON.stringify(data);
    const contentType = {"Content-Type": "application/json"};
    const opts = {
        method: "POST",
        credentials: "same-origin",
        headers: contentType,
        body: body
    };

    return fetch(`/api/v1/process/${instanceId}/form/${formInstanceId}`, opts)
        .then(response => {
            if (!response.ok) {
                throw new defaultError(response);
            }
            return response.json();
        })
        .then(json => {
            console.debug("API: submitForm ['%s','%s', %o] -> done, got %o", instanceId, formInstanceId, data, json);
            return json;
        });
};

export const startSession = (instanceId: ConcordId, formInstanceId: ConcordId) => {
    console.debug("API: startSession ['%s', '%s'] -> starting...", instanceId, formInstanceId);

    const opts = {
        method: "POST",
        credentials: "same-origin",
        redirect: "manual"
    };

    return fetch(`/api/service/custom_form/${instanceId}/${formInstanceId}/start`, opts)
        .then(response => {
            if (!response.ok) {
                throw new defaultError(response);
            }
            return response.json();
        })
        .then(json => {
            console.debug("API: startSession ['%s', '%s'] -> done, got: %o", instanceId, formInstanceId, json);
            return json;
        });
};
