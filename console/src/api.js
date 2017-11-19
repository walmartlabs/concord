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

export const processError = (resp: any) => {
    const contentType = resp.headers.get("Content-Type");
    if (contentType && contentType.indexOf("application/json") !== -1) {
        return resp.json().then(data => {
            throw errorWithDetails(resp, data);
        });
    }

    return new Promise(() => {
        throw defaultError(resp);
    });
};

export const defaultError = (resp: any) => {
    return {
        status: resp.status,
        message: `ERROR: ${resp.statusText} (${resp.status})`
    };
};
