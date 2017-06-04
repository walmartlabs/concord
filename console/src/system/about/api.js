// @flow
import * as common from "../../api";

export const loadServerVersion = () => {
    console.debug("API: loadServerVersion -> starting...");

    return fetch("/api/v1/server/version", {credentials: "same-origin"})
        .then(response => {
            if (!response.ok) {
                throw common.defaultError(response);
            }

            return response.json();
        })
        .then(json => {
            console.debug("API: loadServerVersion -> done, got %o", json);
            return json;
        });
};
