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
import React from 'react';
import {Provider as ReduxProvider} from 'react-redux';

import configureStore from "../store";
import reducers from "../reducers";
import {hashHistory} from "react-router";

const store = configureStore(hashHistory, reducers);

export default function Provider({story}) {
    return (
        <ReduxProvider store={store}>
            {story}
        </ReduxProvider>
    );
};
