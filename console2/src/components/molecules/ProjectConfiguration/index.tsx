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

import React, { useRef, useState } from 'react';
import { Button } from 'semantic-ui-react';
import Editor from '@monaco-editor/react';
import _ from 'lodash';

interface Props {
    config: Object;
    submitting: boolean;
    submit: (config: Object) => void;
}

const ProjectConfiguration: React.FunctionComponent<Props> = ({ config, submitting, submit }) => {
    const [isEditorReady, setIsEditorReady] = useState(false);
    const [jsonError, setJsonError] = useState('');
    const valueGetter = useRef();

    const handleEditorDidMount = (_valueGetter: any) => {
        setIsEditorReady(true);
        valueGetter.current = _valueGetter;
    };

    const handleSubmit = () => {
        if (valueGetter && valueGetter.current) {
            try {
                // @ts-ignore: Cannot invoke an object which is possibly 'undefined'.
                const jsonObj = JSON.parse(valueGetter.current());
                setJsonError('');
                if (!_.isEqual(jsonObj, config)) {
                    submit(jsonObj);
                } else {
                    setJsonError('No changes detected');
                }
            } catch (error) {
                setJsonError(error.message);
            }
        }
    };

    return (
        <div style={{ display: 'flex', height: '75vh', minHeight: '500px' }}>
            <Editor
                height="100%"
                language="json"
                value={JSON.stringify(config, null, 4)}
                editorDidMount={handleEditorDidMount}
                options={{ minimap: { enabled: false } }}
            />
            <div style={{ width: '130px', marginLeft: '20px' }}>
                <Button
                    primary={true}
                    content="Save"
                    disabled={!isEditorReady}
                    loading={submitting}
                    onClick={handleSubmit}
                    style={{ width: '130px' }}
                />
                {jsonError && <p style={{ color: 'red', marginTop: '15px' }}>{jsonError}</p>}
            </div>
        </div>
    );
};

export default ProjectConfiguration;
