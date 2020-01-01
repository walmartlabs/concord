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
import { Divider, Form } from 'semantic-ui-react';
import { ConcordKey } from '../../../api/common';
import { ProjectVisibility } from '../../../api/org/project';
import { isProjectExists } from '../../../api/service/console';
import { notEmpty } from '../../../utils';
import { project as validation, projectAlreadyExistsError } from '../../../validation';
import { FormikDropdown, FormikInput } from '../../atoms';

interface FormValues {
    name: string;
    visibility: ProjectVisibility;
    description?: string;
}

export type NewProjectFormValues = FormValues;

interface Props {
    orgName: ConcordKey;
    initial: FormValues;
    submitting: boolean;
    onSubmit: (values: FormValues) => void;
}

const visibilityOptions = [
    {
        text: 'Public',
        value: ProjectVisibility.PUBLIC,
        description: 'Any user can start a process using a public project.',
        icon: 'unlock'
    },
    {
        text: 'Private',
        value: ProjectVisibility.PRIVATE,
        description: "Private projects can be used only by their organization's teams.",
        icon: 'lock'
    }
];

class NewProjectForm extends React.PureComponent<InjectedFormikProps<Props, FormValues>> {
    render() {
        const { handleSubmit, submitting } = this.props;

        const hasErrors = notEmpty(this.props.errors);

        return (
            <Form onSubmit={handleSubmit} loading={submitting}>
                <FormikInput name="name" label="Name" placeholder="Project name" required={true} />

                <FormikDropdown
                    name="visibility"
                    label="Visibility"
                    selection={true}
                    options={visibilityOptions}
                />

                <FormikInput
                    name="description"
                    label="Description"
                    placeholder="Project description"
                />

                <Divider />

                <Form.Button primary={true} type="submit" disabled={hasErrors}>
                    Create
                </Form.Button>
            </Form>
        );
    }
}

const validator = async (values: FormValues, props: Props) => {
    let e;

    e = validation.name(values.name);
    if (e) {
        return Promise.resolve({ name: e });
    }

    const exists = await isProjectExists(props.orgName, values.name);
    if (exists) {
        return Promise.resolve({ name: projectAlreadyExistsError(values.name) });
    }

    e = validation.description(values.description);
    if (e) {
        return Promise.resolve({ description: e });
    }

    return {};
};

export default withFormik<Props, FormValues>({
    handleSubmit: (values, bag) => {
        bag.props.onSubmit(values);
    },
    mapPropsToValues: (props) => props.initial,
    validate: validator
})(NewProjectForm);
