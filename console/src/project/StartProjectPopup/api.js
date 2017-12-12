// @flow
import type {ConcordId} from "../../types";
import * as common from "../../api";

export const startProject = (repositoryId: ConcordId): Promise<any> => {
    console.debug("API: startProject ['%s'] -> starting...", repositoryId);

    const data = new FormData();
    data.append("repoId", repositoryId);

    const opts = {
        method: "POST",
        credentials: "same-origin",
        body: data
    };

    return fetch(`api/v1/process`, opts)
        .then(response => {
            if (!response.ok) {
                return common.parseError(response);
            }

            return response.json();
        })
        .then(json => {
            console.debug("API: startProject ['%s'] -> done: %o", repositoryId, json);
            return json;
        });
};