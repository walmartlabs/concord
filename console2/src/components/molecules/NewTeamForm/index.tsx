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
import { Button, Divider, Form } from 'semantic-ui-react';

import { ConcordKey } from '../../../api/common';
import { NewTeamEntry } from '../../../api/org/team';
import { isTeamExists } from '../../../api/service/console';
import { notEmpty } from '../../../utils';
import { team as validation, teamAlreadyExistsError } from '../../../validation';
import { FormikInput } from '../../atoms';

interface Props {
    orgName: ConcordKey;
    onSubmit: (values: NewTeamEntry) => void;
    submitting: boolean;
}

class NewTeamForm extends React.Component<InjectedFormikProps<Props, NewTeamEntry>> {
    render() {
        const { submitting, handleSubmit, errors, dirty } = this.props;

        const hasErrors = notEmpty(errors);

        return (
            <Form onSubmit={handleSubmit} loading={submitting}>
                <div data-testid="team-form-name">
                    <FormikInput name="name" label="Name" placeholder="Team name" required={true} />
                </div>

                <div data-testid="team-form-description">
                    <FormikInput
                        name="description"
                        label="Description"
                        placeholder="Short description"
                    />
                </div>

                <Divider />

                <Button primary={true} type="submit" disabled={!dirty || hasErrors} data-testid="team-form-submit">
                    Create
                </Button>
            </Form>
        );
    }
}

const validator = async (values: NewTeamEntry, { orgName }: Props): Promise<{}> => {
    let e;

    e = validation.name(values.name);
    if (e) {
        return Promise.resolve({ name: e });
    }

    const exists = await isTeamExists(orgName, values.name);
    if (exists) {
        return Promise.resolve({ name: teamAlreadyExistsError(values.name) });
    }

    e = validation.description(values.description);
    if (e) {
        return Promise.resolve({ description: e });
    }

    return {};
};

export default withFormik<Props, NewTeamEntry>({
    handleSubmit: (values, bag) => {
        bag.props.onSubmit(values);
    },
    validate: validator
})(NewTeamForm);
