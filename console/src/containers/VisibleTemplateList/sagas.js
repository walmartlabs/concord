// @flow
import * as api from "../../api";
import {actionTypes} from "./actions";
import {makeListFetcher} from "../../sagas/common";

export const fetchTemplateList = makeListFetcher("fetchTemplateList", api.fetchTemplateList,
    actionTypes.FETCH_TEMPLATE_LIST_RESULT);
