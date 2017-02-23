// @flow

// basic types

export type ConcordId = string;
export type ConcordKey = string;

export const sort = {
    ASC: "ASC",
    DESC: "DESC"
};
export type SortDirection = $Keys<typeof sort>;

// http stuff

export type FetchRange = {
    unit?: string,
    length?: number,
    low?: number,
    high?: number
};
