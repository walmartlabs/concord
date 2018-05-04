import { InjectedFormikProps, withFormik } from 'formik';
import * as React from 'react';
import { Button, Divider, Form, Popup } from 'semantic-ui-react';

import { ConcordId, ConcordKey } from '../../../api/common';
import { isRepositoryExists } from '../../../api/service/console';
import { notEmpty } from '../../../utils';
import { repository as validation, repositoryAlreadyExistsError } from '../../../validation';
import { FormikDropdown, FormikInput } from '../../atoms';
import { SecretDropdown } from '../../organisms';

export enum RepositorySourceType {
    BRANCH_OR_TAG = 'branchOrTag',
    COMMIT_ID = 'commitId'
}

interface FormValues {
    id?: ConcordId;
    name: string;
    url: string;
    sourceType: RepositorySourceType;
    branch?: string;
    commitId?: string;
    path?: string;
    secretName?: string;
}

export type RepositoryFormValues = FormValues;

interface Props {
    orgName: ConcordKey;
    projectName: ConcordKey;
    initial: FormValues;
    submitting: boolean;
    editMode?: boolean;
    onSubmit: (values: FormValues) => void;
    testRepository: (values: FormValues) => Promise<void>;
}

interface State {
    testRunning: boolean;
    testSuccess: boolean;
    testError?: string;
}

const sourceOptions = [
    {
        text: 'Branch/tag',
        value: RepositorySourceType.BRANCH_OR_TAG
    },
    {
        text: 'Commit ID',
        value: RepositorySourceType.COMMIT_ID
    }
];

const sanitize = (data: FormValues): FormValues => {
    const v = { ...data };

    if (v.path === '') {
        v.path = undefined;
    }

    if (v.branch === '') {
        v.branch = undefined;
    }

    if (v.commitId === '') {
        v.commitId = undefined;
    }

    return v;
};

class RepositoryForm extends React.Component<InjectedFormikProps<Props, FormValues>, State> {
    constructor(props: InjectedFormikProps<Props, FormValues>) {
        super(props);
        this.state = { testRunning: false, testSuccess: false };
    }

    handleTestConnection() {
        const { values, testRepository } = this.props;
        this.setState({ testRunning: true, testSuccess: false });

        testRepository(sanitize(values))
            .then(() => {
                this.setState({
                    testSuccess: true,
                    testRunning: false
                });
            })
            .catch((e) => {
                this.setState({
                    testSuccess: false,
                    testRunning: false,
                    testError: e.details ? e.details : e.message
                });
            });
    }

    render() {
        const {
            orgName,
            handleSubmit,
            values,
            errors,
            dirty,
            editMode = false,
            isValid
        } = this.props;

        const hasErrors = notEmpty(errors);

        return (
            <>
                <Form onSubmit={handleSubmit}>
                    <FormikInput
                        name="name"
                        label="Name"
                        placeholder="Repository name"
                        required={true}
                    />

                    <FormikInput name="url" label="URL" placeholder="Git URL" required={true} />

                    <Form.Group widths="equal">
                        <FormikDropdown
                            name="sourceType"
                            label="Source"
                            selection={true}
                            options={sourceOptions}
                        />

                        {values.sourceType === RepositorySourceType.BRANCH_OR_TAG && (
                            <FormikInput name="branch" label="Branch/Tag" fluid={true} />
                        )}

                        {values.sourceType === RepositorySourceType.COMMIT_ID && (
                            <FormikInput name="commitId" label="Commit ID" fluid={true} />
                        )}
                    </Form.Group>

                    <FormikInput name="path" label="Path" placeholder="Repository path" />

                    <SecretDropdown
                        orgName={orgName}
                        name="secretName"
                        label="Credentials"
                        required={true}
                        fluid={true}
                    />

                    <Divider />

                    <Button primary={true} type="submit" disabled={!dirty || hasErrors}>
                        {editMode ? 'Save' : 'Add'}
                    </Button>

                    <Popup
                        trigger={
                            <Button
                                basic={true}
                                positive={this.state.testSuccess}
                                negative={!!this.state.testError}
                                floated="right"
                                loading={this.state.testRunning}
                                disabled={!isValid || hasErrors}
                                onClick={(ev) => {
                                    ev.preventDefault();
                                    this.handleTestConnection();
                                }}>
                                Test connection
                            </Button>
                        }
                        open={!!this.state.testError}
                        wide={true}>
                        <Popup.Content>
                            <p style={{ color: 'red' }}>
                                Connection test error: {this.state.testError}
                            </p>
                        </Popup.Content>
                    </Popup>
                </Form>
            </>
        );
    }
}

const validator = async (values: FormValues, props: Props) => {
    let e;

    e = validation.name(values.name);
    if (e) {
        throw { name: e };
    }

    if (values.name !== props.initial.name) {
        const exists = await isRepositoryExists(props.orgName, props.projectName, values.name);
        if (exists) {
            throw { name: repositoryAlreadyExistsError(values.name) };
        }
    }

    e = validation.url(values.url);
    if (e) {
        throw { url: e };
    }

    switch (values.sourceType) {
        case RepositorySourceType.BRANCH_OR_TAG:
            e = validation.branch(values.branch);
            if (e) {
                throw { branch: e };
            }
            break;
        case RepositorySourceType.COMMIT_ID:
            e = validation.commitId(values.branch);
            if (e) {
                throw { commitId: e };
            }
            break;
        default:
            throw new Error(`Unknown repository source type: ${values.sourceType}`);
    }

    e = validation.path(values.path);
    if (e) {
        throw { path: e };
    }

    e = validation.secret(values.secretName);
    if (e) {
        throw { secret: e };
    }

    return {};
};

export default withFormik<Props, FormValues>({
    handleSubmit: (values, bag) => {
        bag.props.onSubmit(sanitize(values));
    },
    mapPropsToValues: (props) => ({
        ...props.initial
    }),
    validate: validator,
    enableReinitialize: true
})(RepositoryForm);
