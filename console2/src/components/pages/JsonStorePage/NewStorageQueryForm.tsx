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
import { Button, Form, Input, Label } from 'semantic-ui-react';
import { ValidateResult } from 'react-hook-form/dist/types';
import Editor from '@monaco-editor/react';

import { ConcordKey } from '../../../api/common';
import { isStorageQueryExists } from '../../../api/service/console';
import { storageQuery, jsonStoreQueryAlreadyExistsError } from '../../../validation';
import { useCallback, useRef, useState } from 'react';

import './styles.css';

interface FormValues {
    name: ConcordKey;
    query: string;
}

export type NewStorageQueryFormValues = FormValues;

export interface Props {
    orgName: ConcordKey;
    storeName: ConcordKey;
    initial: FormValues;
    submitting: boolean;
    executing: boolean;
    onSubmit: (values: FormValues) => void;
    onExecute: (query: string) => void;
}

const NewStorageQueryForm = ({
    orgName,
    storeName,
    onSubmit,
    onExecute,
    submitting,
    executing,
    initial
}: Props) => {
    const [queryName, setQueryName] = useState(initial.name);
    const [queryNameError, setQueryNameError] = useState<string>();
    const [queryError, setQueryError] = useState<ValidateResult>();
    const [isValidating, setIsValidating] = useState(false);
    const [isEditorReady, setIsEditorReady] = useState(false);
    const valueGetter = useRef<() => string>(() => initial.query);

    const handleEditorDidMount = useCallback((getEditorValue: () => string) => {
        setIsEditorReady(true);
        valueGetter.current = getEditorValue;
    }, []);

    const handleQueryNameChange = useCallback((event: React.ChangeEvent<HTMLInputElement>) => {
        setQueryName(event.target.value);
    }, []);

    const handleValidate = useCallback(async () => {
        let hasError = false;
        setIsValidating(true);
        try {
            const nameValidateResult = await validateName(orgName, storeName, queryName);
            if (nameValidateResult !== undefined) {
                setQueryNameError(nameValidateResult);
                hasError = true;
            } else {
                setQueryNameError(undefined);
            }

            const queryValidateResult = await validateQuery(valueGetter.current());
            if (queryValidateResult !== undefined) {
                setQueryError(queryValidateResult);
                hasError = true;
            } else {
                setQueryError(undefined);
            }
        } finally {
            setIsValidating(false);
        }

        return hasError;
    }, [orgName, storeName, queryName]);

    const handleSubmit = useCallback(async () => {
        const hasError = await handleValidate();

        if (!hasError) {
            await onSubmit({ name: queryName, query: valueGetter.current() });
        }
    }, [handleValidate, queryName, onSubmit]);

    const handleExecute = useCallback(async () => {
        const hasError = await handleValidate();

        if (!hasError) {
            await onExecute(valueGetter.current());
        }
    }, [handleValidate, onExecute]);

    const loading = submitting || executing || isValidating || !isEditorReady;

    return (
        <>
            <Form>
                <Form.Field name="name" required={true} disabled={loading}>
                    <label>Name</label>
                    <Input placeholder="Query name" name="name" onChange={handleQueryNameChange} />
                    {queryNameError && (
                        <Label basic={true} pointing={true} color="red">
                            {queryNameError}
                        </Label>
                    )}
                </Form.Field>

                <Form.Field name="query" required={true} disabled={loading}>
                    <label>Query</label>
                </Form.Field>
            </Form>

            <div className={loading ? 'editorContainer loading' : 'editorContainer'}>
                <div className={'editor'}>
                    <Editor
                        height="40vh"
                        language="sql"
                        editorDidMount={handleEditorDidMount}
                        value={initial.query}
                        options={{
                            lineNumbers: 'on',
                            minimap: { enabled: false },
                            readOnly: loading
                        }}
                    />
                </div>
                {queryError && (
                    <Label basic={true} pointing={true} color="red">
                        {queryError}
                    </Label>
                )}
            </div>

            <Button primary={true} onClick={handleSubmit} disabled={loading}>
                Create
            </Button>
            <Button primary={false} onClick={handleExecute} disabled={loading}>
                Execute
            </Button>
        </>
    );
};

const validateName = async (
    orgName: ConcordKey,
    storeName: ConcordKey,
    name: string
): Promise<string | undefined> => {
    const error = storageQuery.name(name);
    if (error) {
        return Promise.resolve(error);
    }
    const exists = await isStorageQueryExists(orgName, storeName, name);
    if (exists) {
        return Promise.resolve(jsonStoreQueryAlreadyExistsError(name));
    }
    return Promise.resolve(undefined);
};

const validateQuery = (query?: string): Promise<ValidateResult> => {
    const error = storageQuery.query(query);
    if (error) {
        return Promise.resolve(error);
    }
    return Promise.resolve(undefined);
};

export default NewStorageQueryForm;
