// @flow

export const sort = {
    ASC: "ASC",
    DESC: "DESC"
};

export const queryParams = (params: { [id: mixed]: string }) => {
    const esc = encodeURIComponent;
    return Object.keys(params).map(k => esc(k) + "=" + esc(params[k])).join("&");
};

const errorWithDetails = (resp: any, data: any) => {
    return {
        ...defaultError(resp),
        ...data
    };
};

export const parseError = (resp: any) => {
    const contentType = resp.headers.get("Content-Type");
    if (isSiestaError(contentType)) {
        return resp.json().then(json => {
            const data = json.length > 0 ? json[0] : {};
            throw errorWithDetails(resp, data);
        });
    } else if (isJson(contentType)) {
        return resp.json().then(json => {
            throw errorWithDetails(resp, json);
        });
    }

    return new Promise(() => {
        throw defaultError(resp);
    });
};

const isJson = (h: ?string): boolean => {
    if (!h) {
        return false;
    }

    if (h.indexOf("application/json") !== -1) {
        return true;
    }
    return false;
};

const isSiestaError = (h: ?string): boolean => {
    if (!h) {
        return false;
    }

    if (h.indexOf("application/vnd.siesta-validation-errors-v1+json") !== -1) {
        return true;
    }

    return false;
};

export const defaultError = (resp: any) => {
    return {
        status: resp.status,
        message: `ERROR: ${resp.statusText} (${resp.status})`
    };
};
