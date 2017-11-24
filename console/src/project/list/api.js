// @flow
import type {ConcordId} from "../../types";
import * as common from "../../api";

export const listProjects = (teamId: ConcordId, sortBy: string = "name", sortDir: string = common.sort.ASC): Promise<any> => {
    console.debug("API: listProjects ['%s', '%s', '%s'] -> starting...", teamId, sortBy, sortDir);

    const query = common.queryParams({
        teamId,
        sortBy,
        asc: String(common.sort.ASC === sortDir)
    });

    return fetch(`/api/v1/project?${query}`, {credentials: "same-origin"})
        .then(response => {
            if (!response.ok) {
                throw common.defaultError(response);
            }
            return response.json();
        })
        .then(json => {
            console.debug("API: listProjects ['%s', '%s'] -> done, got %d row(s)", sortBy, sortDir, json.length);
            return json;
        });
};
