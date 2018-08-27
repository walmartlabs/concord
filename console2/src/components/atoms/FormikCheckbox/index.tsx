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

import { Field, getIn } from 'formik';
import { FieldProps } from 'formik/dist/Field';
import * as React from 'react';
import { Checkbox, Form, FormCheckboxProps, Label } from 'semantic-ui-react';

interface Props {
    name: string;
}

export default class extends React.PureComponent<FormCheckboxProps & Props> {
    render() {
        const { name: fieldName, label, required, inline, ...rest } = this.props;

        return (
            <Field
                name={fieldName}
                render={({ field, form }: FieldProps) => {
                    const touched = getIn(form.touched, fieldName);
                    const error = getIn(form.errors, fieldName);
                    const invalid = !!(touched && error);

                    const handleChanges = (ev: {}, { checked }: { checked: boolean }) =>
                        form.setFieldValue(fieldName, checked);

                    return (
                        <Form.Field error={invalid} required={required} inline={inline}>
                            <label>{label}</label>
                            <Checkbox {...rest} onChange={handleChanges} checked={field.value} />
                            {invalid &&
                                error && (
                                    <Label basic={true} pointing={true} color="red">
                                        {error}
                                    </Label>
                                )}
                        </Form.Field>
                    );
                }}
            />
        );
    }
}
