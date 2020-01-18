/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2020 Walmart Inc.
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

import { Button, Form, Modal, TextArea } from 'semantic-ui-react';
import ReactJson from 'react-json-view';
import * as React from 'react';
import { useCallback, useState } from 'react';

import './styles.css';

export type OnCloseFn = () => void;

export interface Props {
    data: object;
    onClose: OnCloseFn;
}

const renderJsonView = (data: object, onShowRaw: () => void) => (
    <>
        <div style={{ textAlign: 'right' }}>
            <Button className="viewModeToggle" basic={true} size="small" onClick={onShowRaw}>
                Disable highlighting
            </Button>
        </div>
        <ReactJson
            src={data}
            collapsed={false}
            name={null}
            enableClipboard={true}
            displayDataTypes={false}
            displayObjectSize={false}
        />
    </>
);

const renderRawView = (data: object, onShowJson: () => void) => (
    <>
        <div style={{ textAlign: 'right' }}>
            <Button className="viewModeToggle" basic={true} size="small" onClick={onShowJson}>
                Enable highlighting
            </Button>
        </div>
        <Form>
            <TextArea style={{ minHeight: 300 }} readOnly={true}>
                {JSON.stringify(data, null, 2)}
            </TextArea>
        </Form>
    </>
);

export default ({ data, onClose }: Props) => {
    const [open, setOpen] = useState(true);
    const [showRaw, setShowRaw] = useState(false);

    const handleClose = useCallback(() => {
        setOpen(false);
        onClose();
    }, [onClose]);

    return (
        <>
            <Modal dimmer="inverted" open={open} onClose={handleClose}>
                <Modal.Header>Query results</Modal.Header>
                <Modal.Content scrolling={true}>
                    {showRaw
                        ? renderRawView(data, () => setShowRaw(false))
                        : renderJsonView(data, () => setShowRaw(true))}
                </Modal.Content>
            </Modal>
        </>
    );
};
