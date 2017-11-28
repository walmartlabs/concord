// @flow
import type {ConcordKey} from "../../types";
import * as common from "../../api";

export const listProjects = (orgName: ConcordKey, sortBy: string = "name", sortDir: string = common.sort.ASC): Promise<any> => {
    console.debug("API: listProjects ['%s', '%s', '%s'] -> starting...", orgName, sortBy, sortDir);

    const query = common.queryParams({
        sortBy,
        asc: String(common.sort.ASC === sortDir)
    });

    return fetch(`/api/v1/org/${orgName}/project?${query}`, {credentials: "same-origin"})
        .then(response => {
            if (!response.ok) {
                throw common.defaultError(response);
            }
            return response.json();
        })
        .then(json => {
            console.debug("API: listProjects ['%s', '%s', '%s'] -> done, got %d row(s)", orgName, sortBy, sortDir, json.length);
            return json;
        });
};
