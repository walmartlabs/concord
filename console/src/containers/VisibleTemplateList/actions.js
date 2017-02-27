// @flow
import type {SortDirection} from "../../types";

export const actionTypes = {
    FETCH_TEMPLATE_LIST_REQUEST: "FETCH_TEMPLATE_LIST_REQUEST",
    FETCH_TEMPLATE_LIST_RESULT: "FETCH_TEMPLATE_LIST_RESULT"
};

export const fetchTemplateList = (sortBy: string, sortDir: SortDirection) => ({
    type: actionTypes.FETCH_TEMPLATE_LIST_REQUEST,
    sortBy,
    sortDir
});
