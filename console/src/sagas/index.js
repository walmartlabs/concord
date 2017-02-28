// @flow
import {fork} from "redux-saga/effects";
import projectTable from "../containers/VisibleProjectTable/sagas";
import historyTable from "../containers/VisibleHistoryTable/sagas";
import projectForm from "../containers/VisibleProjectForm/sagas";
import log from "../containers/VisibleLogViewer/sagas";
import templateList from "../containers/VisibleTemplateList/sagas";

export default function*(): Generator<*, *, *> {
    yield [
        fork(historyTable),
        fork(projectTable),
        fork(projectForm),
        fork(templateList),
        fork(log)
    ];
}
