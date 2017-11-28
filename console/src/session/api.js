// @flow
import * as common from "../api";

export const logout = (): Promise<any> => {
    console.debug("API: logout -> starting...",);

    return fetch("/api/service/console/logout", {method: "POST", credentials: "same-origin"})
        .then(response => {
            if (!response.ok) {
                throw common.defaultError(response);
            }

            console.debug("API: logout -> done");
            return true;
        });
};
