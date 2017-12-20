/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 Wal-Mart Store, Inc.
 * -----
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =====
 */
// @flow
import {fork, all} from "redux-saga/effects";
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
import {sagas as secret} from "./org/secret/list";
import {sagas as secretNew} from "./org/secret/create";
import {sagas as landingList} from "./landing/list";

export default function*(): Generator<*, *, *> {
    yield all([
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
    ]);
}
