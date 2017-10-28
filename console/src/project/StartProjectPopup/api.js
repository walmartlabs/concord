// @flow
import type {ConcordKey} from "../../types";
import * as common from "../../api";

export const startProject = (projectName: ConcordKey, repositoryName: ConcordKey) => {
    console.debug("API: startProject ['%s', '%s'] -> starting...", projectName, repositoryName);

    const opts = {
        method: "POST",
        credentials: "same-origin",
        headers: {
            "Content-Type": "application/json"
        }
    };

    return fetch(`api/v1/process/${projectName}:${repositoryName}`, opts)
        .then(response => {
            if (!response.ok) {
                return common.processError(response);
            }

            return response.json();
        })
        .then(json => {
            console.debug("API: startProject ['%s', '%s'] -> done: %o", projectName, repositoryName, json);
            return json;
        });
};