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

import { ConcordId } from '../../../api/common';
import { ProcessStatus } from '../../../api/process';

import './styles.css';
import {Button, Icon, Popup} from "semantic-ui-react";
import {Link} from "react-router-dom";
import {LogSegment} from "../../molecules";

interface ExternalProps {
    instanceId: ConcordId;
    processStatus?: ProcessStatus;
    loadingHandler: (inc: number) => void;
    forceRefresh: boolean;
}
const ProcessLogActivityV2 = ({
    instanceId,
    processStatus,
    loadingHandler,
    forceRefresh
}: ExternalProps) => {
    return (
        <>
            <LogSegment correlationId={"xxx"} name={"System"} status={"ok"}
                        startLoading={(correlationId, name) => console.log('start ', correlationId, name)}
                        stopLoading={(correlationId, name) => console.log('stop ', correlationId, name)}
                        data={["a", "b"]}/>

            <LogSegment correlationId={"xxx"} name={"System"} status={"error"}
                        startLoading={(correlationId, name) => console.log('start ', correlationId, name)}
                        stopLoading={(correlationId, name) => console.log('stop ', correlationId, name)}
                        data={["a", "b"]}/>

            <LogSegment correlationId={"xxx"} name={"System"} status={"running"}
                        startLoading={(correlationId, name) => console.log('start ', correlationId, name)}
                        stopLoading={(correlationId, name) => console.log('stop ', correlationId, name)}
                        data={["a", "b"]}/>

            <LogSegment correlationId={"xxx"} name={"System"} status={undefined}
                        startLoading={(correlationId, name) => console.log('start ', correlationId, name)}
                        stopLoading={(correlationId, name) => console.log('stop ', correlationId, name)}
                        data={["a", "b"]}/>
        </>
    );
};

export default ProcessLogActivityV2;
