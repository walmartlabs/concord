import {sort} from "./constants";

const authHeader = {"Authorization": "auBy4eDWrKWsyhiDp3AQiw"};

export const fetchHistory = (sortBy, sortDir) => {
    console.debug("API: fetchHistory ['%s', '%s'] -> starting...", sortBy, sortDir);

    const params = {
        sortBy,
        asc: sort.ASC === sortDir
    };

    const esc = encodeURIComponent;
    const query = Object.keys(params).map(k => esc(k) + "=" + esc(params[k])).join("&");

    return fetch("/api/v1/history?" + query, {headers: authHeader})
        .then(response => {
            if (!response.ok) {
                throw new Error("ERROR: " + response.statusText + " (" + response.status + ")");
            }
            return response.json();
        })
        .then(json => {
            console.debug("API: fetchHistory ['%s', '%s'] -> done, got %d row(s)", sortBy, sortDir, json.length);
            return json;
        });
};

export const killProc = (id) => {
    console.debug("API: killProc ['%s'] -> starting...", id);

    return fetch("/api/v1/process/" + id, {headers: authHeader, method: "DELETE"})
        .then(response => {
            if (!response.ok) {
                throw new Error("ERROR: " + response.statusText + " (" + response.status + ")");
            }
            console.debug("API: killProc ['%s'] -> done", id);
            return true;
        });
};

export const fetchLog = (fileName) => {
    console.debug("API: fetchLog ['%s'] -> starting...", fileName);
    return fetch("/logs/" + fileName, {headers: authHeader})
        .then(response => {
            if (!response.ok) {
                throw new Error("ERROR: " + response.statusText + " (" + response.status + ")");
            }
            return response.text();
        })
        .then(text => {
            console.debug("API: fetchLog ['%s'] -> done, length: %d", fileName, text.length);
            return text;
        });
};
