import type {ConcordKey} from "../../types";
import * as common from "../../api";

export const createNewKeyPair = (name: ConcordKey) => {

    const query = common.queryParams({
        name
    });

    return fetch(`/api/v1/secret/keypair?${query}`, { method: "POST", credentials: "same-origin"})
        .then(response => {
            console.log(response);
        })
        .then(json => {
            console.log(json);
        });
};
