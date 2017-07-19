// @flow
import type {ConcordKey} from "../../types";
import * as common from "../../api";

export const fetchSecretList = (sortBy: string = "name", sortDir: string = common.sort.ASC) => {
    console.debug("API: fetchSecretList ['%s', '%s'] -> starting...", sortBy, sortDir);

    const query = common.queryParams({
        sortBy,
        asc: String(common.sort.ASC === sortDir)
    });

    return fetch(`/api/v1/secret?${query}`, {credentials: "same-origin"})
        .then(response => {
            if (!response.ok) {
                throw common.defaultError(response);
            }
            return response.json();
        })
        .then(json => {
            console.debug("API: fetchSecretList ['%s', '%s'] -> done, got %d row(s)", sortBy, sortDir, json.length);
            return json;
        });
};

export const deleteSecret = (name: ConcordKey) => {
    console.debug("API: deleteSecret ['%s'] -> starting...", name);

    return fetch(`/api/v1/secret/${name}`, {method: "DELETE", credentials: "same-origin"})
        .then(response => {
            if (!response.ok) {
                throw new Error("ERROR: " + response.statusText + " (" + response.status + ")");
            }
            console.debug("API: deleteSecret ['%s'] -> done", name);
            return true;
        });
};