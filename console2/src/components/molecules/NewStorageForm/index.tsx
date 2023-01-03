/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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
import { useEffect } from 'react';
import { Dropdown, Form, Input, Label } from 'semantic-ui-react';
import { useForm } from 'react-hook-form';
import { ValidateResult } from 'react-hook-form/dist/types';

import { ConcordKey } from '../../../api/common';
import { StorageVisibility } from '../../../api/org/jsonstore';
import { storage as validation, jsonStoreAlreadyExistsError } from '../../../validation';
import { isStorageExists } from '../../../api/service/console';

interface FormValues {
    name: string;
    visibility: StorageVisibility;
}

export type NewStorageFormValues = FormValues;

export interface Props {
    orgName: ConcordKey;
    initial: FormValues;
    submitting: boolean;
    onSubmit: (values: FormValues) => void;
}

const visibilityOptions = [
    {
        text: 'Public',
        value: StorageVisibility.PUBLIC,
        icon: 'unlock'
    },
    {
        text: 'Private',
        value: StorageVisibility.PRIVATE,
        icon: 'lock'
    }
];

const NewStoreForm = ({ orgName, onSubmit, submitting, initial }: Props) => {
    const {
        register,
        handleSubmit,
        formState: { errors },
        setValue
    } = useForm<FormValues>({
        defaultValues: initial
    });

    useEffect(() => {
        register('name', { required: true, validate: (data) => validateName(orgName, data) });
        register('visibility', { required: true });
    }, [orgName, register]);

    return (
        <Form onSubmit={handleSubmit((data) => onSubmit(data))} loading={submitting}>
            <Form.Field name="name" required={true}>
                <label>Store name</label>
                <Input
                    name="name"
                    onChange={(event) => setValue('name', event.target.value)}
                    error={!!errors.name}
                />
                {errors.name && (
                    <Label basic={true} pointing={true} color="red">
                        {errors.name.message ? errors.name.message : 'Name is required'}
                    </Label>
                )}
            </Form.Field>

            <Form.Field name="visibility" required={true}>
                <label>Visibility</label>
                <Dropdown
                    selectOnBlur={true}
                    onChange={(event, data) =>
                        setValue('visibility', data.value as StorageVisibility)
                    }
                    options={visibilityOptions}
                    selection={true}
                    defaultValue={StorageVisibility.PRIVATE}
                    error={!!errors.visibility}
                />
                {errors.visibility && (
                    <Label basic={true} pointing={true} color="red">
                        {errors.visibility.message
                            ? errors.visibility.message
                            : 'Visibility is required'}
                    </Label>
                )}
            </Form.Field>

            <Form.Button primary={true} type="submit" disabled={submitting}>
                Create
            </Form.Button>
        </Form>
    );
};

const validateName = async (orgName: ConcordKey, name: string): Promise<ValidateResult> => {
    const invalidName = validation.name(name);
    if (invalidName) {
        return Promise.resolve(invalidName);
    }

    const exists = await isStorageExists(orgName, name);
    if (exists) {
        return Promise.resolve(jsonStoreAlreadyExistsError(name));
    }
    return Promise.resolve(undefined);
};

export default NewStoreForm;
