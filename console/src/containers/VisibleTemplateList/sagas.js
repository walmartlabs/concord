// @flow
import {fork, takeLatest} from "redux-saga/effects";
import * as templateApi from "../../api/template";
import {actionTypes} from "./actions";
import {makeListFetcher} from "../../sagas/common";

const fetchTemplateList = makeListFetcher("fetchTemplateList", templateApi.fetchTemplateList,
    actionTypes.FETCH_TEMPLATE_LIST_RESULT);

export default function* (): Generator<*, *, *> {
    yield [
        fork(takeLatest, actionTypes.FETCH_TEMPLATE_LIST_REQUEST, fetchTemplateList)
    ];
}
