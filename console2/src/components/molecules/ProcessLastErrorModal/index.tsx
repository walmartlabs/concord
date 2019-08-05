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
import ReactJson from 'react-json-view';
import { Icon, Modal } from 'semantic-ui-react';
import { ProcessMeta } from '../../../api/process';

interface Props {
    processMeta?: ProcessMeta;
    title?: string;
}

export default ({ processMeta, title = 'Last error' }: Props) => {
    if (!processMeta || !processMeta.out || !processMeta.out.lastError) {
        return <></>;
    }

    return (
        <Modal
            size="fullscreen"
            dimmer="inverted"
            trigger={
                <Icon
                    className="failureDetailsButton"
                    name="question circle outline"
                    color="red"
                    title="Failure details"
                />
            }>
            <Modal.Header>{title}</Modal.Header>
            <Modal.Content>
                <ReactJson
                    src={processMeta.out.lastError}
                    collapsed={false}
                    name={null}
                    enableClipboard={false}
                />
            </Modal.Content>
        </Modal>
    );
};
