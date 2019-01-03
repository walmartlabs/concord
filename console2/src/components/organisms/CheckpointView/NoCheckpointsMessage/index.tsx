/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
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

import * as React from 'react';

import { LoadError } from '../shared/Labels';

export const NoCheckpointsMessage: React.SFC<{}> = () => (
    <LoadError>
        No checkpoints have been created for this process.
        <br />
        Find out more in {/* // TODO: Replace with ENV var metadata */}
        <a
            href={`${
                window.concord.documentationSite
            }/docs/getting-started/concord-dsl.html#checkpoints`}>
            the documentation.
        </a>
    </LoadError>
);

export default NoCheckpointsMessage;
