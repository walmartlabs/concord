// @flow
import * as common from "../../api";

export const loadData = () => {
    console.debug("API: loadData -> starting...");

    return fetch(`/api/v1/landing_page`, {credentials: "same-origin"})
        .then(response => {
            if (!response.ok) {
                throw common.defaultError(response);
            }
            return response.json();
        })
        .then(json => {
            console.debug("API: loadData -> done, got %d row(s)", json.length);
            return json;
        });
};
