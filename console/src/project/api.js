// @flow
import type {ConcordKey} from "../types";
import * as common from "../api";

export const fetchProject = (name: ConcordKey) => {
    console.debug("API: fetchProject ['%s'] -> starting...", name);
    return fetch(`/api/v1/project/${name}`, {credentials: "same-origin"})
        .then(response => {
            if (!response.ok) {
                throw new common.defaultError(response);
            }

            return response.json();
        })
        .then(json => {
            console.debug("API: fetchProject ['%s'] -> done: %o", name, json);
            return json;
        });
};

export const updateProject = (data: any) => {
    console.debug("API: updateProject ['%o'] -> starting...", data);

    const opts = {
        method: "POST",
        credentials: "same-origin",
        headers: {
            "Content-Type": "application/json"
        },
        body: JSON.stringify(data)
    };

    return fetch("/api/v1/project", opts)
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

export const isProjectExists = (name: string) => {
    console.debug("API: checkName ['%s'] -> starting...", name);
    return fetch(`/api/service/console/project/${name}/exists`, {credentials: "same-origin"})
        .then(response => {
            if (!response.ok) {
                throw new common.defaultError(response);
            }

            return response.json();
        })
        .then(json => {
            console.debug("API: checkName ['%s'] -> done: %o", name, json);
            return json;
        });
};

export const deleteProject = (name: ConcordKey) => {
    console.debug("API: deleteProject ['%s'] -> starting...", name);

    const opts = {
        method: "DELETE",
        credentials: "same-origin"
    };

    return fetch(`/api/v1/project/${name}`, opts)
        .then(response => {
            if (!response.ok) {
                throw new common.defaultError(response);
            }

            return response.json();
        })
        .then(json => {
            console.debug("API: deleteProject ['%s'] -> done: %o", name, json);
            return json;
        });
};