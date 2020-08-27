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
import { Button, Form, Label } from 'semantic-ui-react';
import { ValidateResult } from 'react-hook-form/dist/types';

import { storageQuery } from '../../../validation';
import { useCallback, useRef, useState } from 'react';
import './styles.css';
import LoadingEditor from '../../molecules/LoadingEditor';

export interface Props {
    initialQuery?: string;
    submitting: boolean;
    executing: boolean;
    onSubmit: (query: string) => void;
    onExecute: (query: string) => void;
}

const EditStoreQueryForm = (props: Props) => {
    const { onSubmit, onExecute, submitting, executing, initialQuery } = props;

    const [queryError, setQueryError] = useState<ValidateResult>();
    const [isValidating, setIsValidating] = useState(false);
    const [isEditorReady, setIsEditorReady] = useState(false);
    const valueGetter = useRef<() => string>(() => '');

    const handleEditorDidMount = useCallback((getEditorValue: () => string) => {
        setIsEditorReady(true);
        valueGetter.current = getEditorValue;
    }, []);

    const handleValidate = useCallback(async () => {
        let success = false;
        setIsValidating(true);
        try {
            const queryValidateResult = await validateQuery(valueGetter.current());
            if (queryValidateResult !== undefined) {
                setQueryError(queryValidateResult);
            } else {
                setQueryError(undefined);
                success = true;
            }
            return success;
        } finally {
            setIsValidating(false);
        }
    }, []);

    const handleSubmit = useCallback(async () => {
        const success = handleValidate();
        if (success) {
            await onSubmit(valueGetter.current());
        }
    }, [handleValidate, onSubmit]);

    const handleExecute = useCallback(async () => {
        const success = await handleValidate();

        if (success) {
            await onExecute(valueGetter.current());
        }
    }, [handleValidate, onExecute]);

    const loading = submitting || executing || isValidating || !isEditorReady;
    return (
        <>
            <Form>
                <Form.Field name="query" required={true} disabled={loading}>
                    <label>Query</label>
                </Form.Field>
            </Form>

            <div className={loading ? 'editorContainer loading' : 'editorContainer'}>
                <div className={'editor'}>
                    <LoadingEditor
                        language="sql"
                        handleEditorDidMount={handleEditorDidMount}
                        initValue={initialQuery}
                        disabled={loading}
                    />
                </div>
                {queryError && (
                    <Label basic={true} pointing={true} color="red">
                        {queryError}
                    </Label>
                )}
            </div>

            <Button
                primary={true}
                onClick={handleSubmit}
                disabled={loading || initialQuery === undefined}>
                Save
            </Button>
            <Button
                primary={false}
                onClick={handleExecute}
                disabled={loading || initialQuery === undefined}>
                Execute
            </Button>
        </>
    );
};

const validateQuery = (query?: string): Promise<ValidateResult> => {
    const error = storageQuery.query(query);
    if (error) {
        return Promise.resolve(error);
    }
    return Promise.resolve(undefined);
};

export default EditStoreQueryForm;
