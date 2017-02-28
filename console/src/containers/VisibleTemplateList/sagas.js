// @flow
import * as templateApi from "../../api/template";
import {actionTypes} from "./actions";
import {makeListFetcher} from "../../sagas/common";

export const fetchTemplateList = makeListFetcher("fetchTemplateList", templateApi.fetchTemplateList,
    actionTypes.FETCH_TEMPLATE_LIST_RESULT);
