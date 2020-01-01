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

import { InjectedFormikProps, withFormik } from 'formik';
import * as React from 'react';
import { Button, Form } from 'semantic-ui-react';

import { ConcordKey } from '../../../api/common';
import { NewTokenEntry } from '../../../api/profile/api_token';
import { isApiTokenExists } from '../../../api/service/console';
import { notEmpty } from '../../../utils';
import { apiTokenAlreadyExistsError, secret as validation } from '../../../validation';
import { FormikInput } from '../../atoms';

interface FormValues {
    name: ConcordKey;
}

export type NewAPITokenFormValues = FormValues;

interface Props {
    initial: FormValues;
    onSubmit: (values: NewTokenEntry) => void;
    submitting: boolean;
}

class NewAPITokenForm extends React.Component<InjectedFormikProps<Props, FormValues>> {
    render() {
        const { submitting, handleSubmit, errors, dirty } = this.props;

        const hasErrors = notEmpty(errors);

        return (
            <Form onSubmit={handleSubmit} loading={submitting}>
                <FormikInput name="name" label="Name" placeholder="Token name" required={true} />

                <Button primary={true} type="submit" disabled={!dirty || hasErrors}>
                    Generate
                </Button>
            </Form>
        );
    }
}

const validator = async (values: FormValues) => {
    let e;

    e = validation.name(values.name);
    if (e) {
        return Promise.resolve({ name: e });
    }

    const exists = await isApiTokenExists(values.name);
    if (exists) {
        return Promise.resolve({ name: apiTokenAlreadyExistsError(values.name) });
    }

    return {};
};

export default withFormik<Props, FormValues>({
    handleSubmit: (values, bag) => {
        bag.props.onSubmit(values);
    },
    mapPropsToValues: (props) => props.initial,
    validate: validator
})(NewAPITokenForm);
