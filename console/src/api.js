// @flow

export const sort = {
    ASC: "ASC",
    DESC: "DESC"
};

export const queryParams = (params: { [id: mixed]: string }) => {
    const esc = encodeURIComponent;
    return Object.keys(params).map(k => esc(k) + "=" + esc(params[k])).join("&");
};

export const defaultError = (resp: any) => {
    return {
        status: resp.status,
        message: `ERROR: ${resp.statusText} (${resp.status})`
    };
};
