// @flow
import * as common from "../../api";

export const loadData = (sortBy: string = "lastUpdatedAt", sortDir: string = common.sort.DESC) => {
    console.debug("API: loadData ['%s', '%s'] -> starting...", sortBy, sortDir);

    const query = common.queryParams({
        sortBy,
        asc: String(common.sort.ASC === sortDir)
    });

    return fetch(`/api/v1/process?${query}`, {credentials: "same-origin"})
        .then(response => {
            if (!response.ok) {
                throw common.defaultError(response);
            }
            return response.json();
        })
        .then(json => {
            console.debug("API: loadData ['%s', '%s'] -> done, got %d row(s)", sortBy, sortDir, json.length);
            return json;
        });
};
