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
import {sagas as repository} from "./project/repository";
import {sagas as secret} from "./user/secret";

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
        fork(repository),
        fork(secret)
    ];
}
