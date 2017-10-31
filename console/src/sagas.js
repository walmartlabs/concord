// @flow
import {fork} from "redux-saga/effects";
import {sagas as session} from "./session";
import {sagas as about} from "./system/about";
import {sagas as loginForm} from "./login";
import {sagas as process} from "./process";
import {sagas as processQueue} from "./process/queue";
import {sagas as processLog} from "./process/log";
import {sagas as form} from "./process/form";
import {sagas as wizard} from "./process/wizard";
import {sagas as portal} from "./process/portal";
import {sagas as project} from "./project";
import {sagas as projectList} from "./project/list";
import {sagas as projectStart} from "./project/StartProjectPopup"
import {sagas as repository} from "./project/repository";
import {sagas as secret} from "./secret/list";
import {sagas as secretNew} from "./secret/create"
import {sagas as landingList} from "./landing/list";

export default function*(): Generator<*, *, *> {
    yield [
        fork(session),
        fork(about),
        fork(loginForm),
        fork(process),
        fork(processQueue),
        fork(processLog),
        fork(form),
        fork(wizard),
        fork(portal),
        fork(project),
        fork(projectList),
        fork(projectStart),
        fork(repository),
        fork(secret),
        fork(secretNew),
        fork(landingList)
    ];
}
