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
import { Form, Label } from 'semantic-ui-react';

import { ConcordKey } from '../../../api/common';
import { FieldProps } from 'formik/dist/Field';
import { Field, getIn } from 'formik';
import { ProjectSearch } from '../index';

interface ExternalProps {
    fieldName: string;
    orgName: ConcordKey;
    defaultProjectName?: string;
    label?: string;
    placeholder?: string;
    onChange?: (projectName?: ConcordKey) => void;
}

export default ({
    fieldName,
    orgName,
    defaultProjectName,
    label,
    placeholder,
    onChange
}: ExternalProps) => {
    return (
        <Field name={fieldName}>
            {({ form }: FieldProps) => {
                const touched = getIn(form.touched, fieldName);
                const error = getIn(form.errors, fieldName);
                const invalid = !!(touched && error);

                return (
                    <Form.Field error={invalid}>
                        {label && <label>{label}</label>}
                        <ProjectSearch
                            orgName={orgName}
                            placeholder={placeholder}
                            fluid={true}
                            defaultProjectName={defaultProjectName}
                            invalid={invalid}
                            onReset={(value) => {
                                form.setFieldValue(fieldName, value?.name);
                                onChange?.(value?.name);
                            }}
                            onClear={() => {
                                form.setFieldValue(fieldName, undefined);
                                onChange?.(undefined);
                            }}
                            onSelect={(value) => {
                                form.setFieldValue(fieldName, value.name);
                                onChange?.(value.name);
                            }}
                        />

                        {invalid && error && (
                            <Label basic={true} pointing={true} color="red">
                                {error}
                            </Label>
                        )}
                    </Form.Field>
                );
            }}
        </Field>
    );
};
