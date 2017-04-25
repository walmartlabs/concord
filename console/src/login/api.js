// @flow
import * as common from "../api";

export const login = (username: string, password: string) => {
    console.debug("API: login['%s'] -> starting...", username);

    const opts = {credentials: "same-origin", headers: {}};
    if (username !== undefined && password !== undefined) {
        opts.headers["Authorization"] = "Basic " + btoa(username + ":" + password);
    }

    return fetch("/api/service/console/whoami", opts)
        .then(response => {
            if (!response.ok) {
                throw common.defaultError(response);
            }

            return response.json();
        })
        .then(json => {
            console.debug("API: login ['%s'] -> done, got %o", username, json);
            return json;
        });
};
