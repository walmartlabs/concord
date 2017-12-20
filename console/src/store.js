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

import {applyMiddleware, combineReducers, createStore} from "redux";
import {createLogger} from "redux-logger";
import {routerMiddleware, routerReducer} from "react-router-redux";
import createSagaMiddleware from "redux-saga";
import sagas from "./sagas";
import { composeWithDevTools } from "redux-devtools-extension";

export default (history: any, reducers: any) => {
    const sagaMw = createSagaMiddleware();
    const routerMw = routerMiddleware(history);

    const middleware = [sagaMw, routerMw];
    if (process.env.NODE_ENV !== "production") {
        middleware.push(createLogger());
    }

    const combined = combineReducers({
        ...reducers,
        routing: routerReducer
    });

    const store = createStore(combined, composeWithDevTools(
        applyMiddleware(...middleware)
    ));

    sagaMw.run(sagas);

    return store;
}
