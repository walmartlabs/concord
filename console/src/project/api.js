// @flow
import type {ConcordKey} from "../types";
import * as common from "../api";

export const fetchProject = (orgName: ConcordKey, name: ConcordKey): Promise<any> => {
    console.debug("API: fetchProject ['%s', '%s'] -> starting...", orgName, name);
    return fetch(`/api/v1/org/${orgName}/project/${name}`, {credentials: "same-origin"})
        .then(response => {
            if (!response.ok) {
                throw new common.defaultError(response);
            }

            return response.json();
        })
        .then(json => {
            console.debug("API: fetchProject ['%s', '%s'] -> done: %o", orgName, name, json);
            return json;
        });
};

export const updateProject = (data: any): Promise<any> => {
    console.debug("API: updateProject ['%o'] -> starting...", data);

    const {orgName, ...rest} = data;

    const opts = {
        method: "POST",
        credentials: "same-origin",
        headers: {
            "Content-Type": "application/json"
        },
        body: JSON.stringify(rest)
    };

    return fetch(`/api/v1/org/${orgName}/project`, opts)
        .then(response => {
            if (!response.ok) {
                throw new common.defaultError(response);
            }

            return response.json();
        })
        .then(json => {
            console.debug("API: updateProject ['%o'] -> done: %o", data, json);
            return json;
        });
};

export const isProjectExists = (orgName: ConcordKey, name: string): Promise<any> => {
    console.debug("API: isProjectExists ['%s', '%s'] -> starting...", orgName, name);
    return fetch(`/api/service/console/org/${orgName}/project/${name}/exists`, {credentials: "same-origin"})
        .then(response => {
            if (!response.ok) {
                throw new common.defaultError(response);
            }

            return response.json();
        })
        .then(json => {
            console.debug("API: isProjectExists ['%s', '%s'] -> done: %o", orgName, name, json);
            return json;
        });
};

export const deleteProject = (orgName: ConcordKey, name: ConcordKey): Promise<any> => {
    console.debug("API: deleteProject ['%s', '%s'] -> starting...", orgName, name);

    const opts = {
        method: "DELETE",
        credentials: "same-origin"
    };

    return fetch(`/api/v1/org/${orgName}/project/${name}`, opts)
        .then(response => {
            if (!response.ok) {
                throw new common.defaultError(response);
            }

            return response.json();
        })
        .then(json => {
            console.debug("API: deleteProject ['%s', '%s'] -> done: %o", orgName, name, json);
            return json;
        });
};