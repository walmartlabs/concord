/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 Wal-Mart Store, Inc.
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
import React from "react";
import PropTypes from "prop-types";
import {connect} from "react-redux";
import {formValueSelector, getFormValues, reduxForm, submit as submitForm} from "redux-form";
import {Button, Form, Modal, Popup, Message, Icon} from "semantic-ui-react";
import {Dropdown, Field} from "../../shared/forms";
import SecretListDropdown from "../../org/secret/list/SecretsListDropdown";
import * as v from "../../shared/validation";
import * as c from "./constants";
import {actions as repoActions, selectors as repoSelectors} from "../repository";
import {getCurrentOrg} from "../../session/reducers";

const SOURCE_TYPE_LABELS = {
    [c.BRANCH_SOURCE_TYPE]: "Branch/tag",
    [c.REV_SOURCE_TYPE]: "Revision"
};

let repositoryForm = (props) => {
    const {open, editMode, sourceTypeValue = c.BRANCH_SOURCE_TYPE} = props;

    const {pristine, submitting, invalid} = props;
    const saveDisabled = pristine || submitting || invalid;

    const {testResult, testError, testLoading, onTestRepoFn, formValues, org} = props;
    const testDisabled = submitting || invalid;
    const testIcon = testError ? "warning sign" : (testResult ? "check" : undefined);

    const testFn = (ev) => {
        ev.preventDefault();
        onTestRepoFn(Object.assign({}, formValues, {orgId: org.id}));
    };

    const {handleSubmit, onSaveFn, onCloseFn} = props;

    const sourceOptions = [
        {text: "Branch/tag", value: c.BRANCH_SOURCE_TYPE},
        {text: "Revision", value: c.REV_SOURCE_TYPE}
    ];

    return <Modal open={open} dimmer="inverted">
        <Modal.Header>{editMode ? "Edit repository" : "New repository"}</Modal.Header>
        <Modal.Content>
            <Form onSubmit={handleSubmit} loading={submitting}>
                <Field name="name" label="Name" required/>
                <Field name="url" label="URL" required/>

                <Form.Group widths="equal">
                    <Dropdown name="sourceType" label="Source" options={sourceOptions}/>
                    <Field name={sourceTypeValue} label={SOURCE_TYPE_LABELS[sourceTypeValue]}/>
                </Form.Group>

                <Field name="path" label="Path"/>

                <Dropdown widget={SecretListDropdown} name="secret" label="Credentials" required/>

                <Message size="tiny">
                    <Icon name="lock"/> "Locked" secrets require a password to use.
                </Message>
            </Form>
        </Modal.Content>
        <Modal.Actions>
            <Button color="red" onClick={onCloseFn}>Cancel</Button>
            <Button color="green" onClick={onSaveFn} disabled={saveDisabled}>{editMode ? "Save" : "Add"}</Button>

            <Popup trigger={<Button basic
                                    positive={testResult}
                                    negative={testError && true}
                                    content="Test connection"
                                    floated="left"
                                    disabled={testDisabled}
                                    loading={testLoading}
                                    icon={testIcon}
                                    onClick={testFn}/>}
                   open={testError && true}
                   on="click"
                   hideOnScroll
                   wide>
                <Popup.Content>
                    <p style={{color: "red"}}>Connection test error: {testError}</p>
                </Popup.Content>
            </Popup>
        </Modal.Actions>
    </Modal>;
};

const validate = ({name, url, sourceType, branch, commitId, secret}) => {
    const errors = {};

    errors.name = v.repository.name(name);
    errors.url = v.repository.url(url);

    if (sourceType === c.BRANCH_SOURCE_TYPE) {
        errors.branch = v.repository.branch(branch);
    } else if (sourceType === c.REV_SOURCE_TYPE) {
        errors.commitId = v.repository.commitId(commitId);
    }

    errors.secret = v.repository.secret(secret);

    return errors;
};

repositoryForm.propTypes = {
    onSubmit: PropTypes.func.isRequired
};

repositoryForm = reduxForm({
    form: "repository",
    initialValues: { sourceType: c.BRANCH_SOURCE_TYPE },
    validate,
    enableReinitialize: true,
    keepDirtyOnReinitialize: true
})(repositoryForm);

const selector = formValueSelector("repository");

const mapStateToProps = (state) => ({
    org: getCurrentOrg(state.session),
    sourceTypeValue: selector(state, "sourceType"),
    formValues: getFormValues("repository")(state),
    testResult: repoSelectors.getTestResult(state.repository),
    testError: repoSelectors.getError(state.repository),
    testLoading: repoSelectors.isLoading(state.repository)
});

const mapDispatchToProps = (dispatch) => ({
    onSaveFn: () => dispatch(submitForm("repository")),
    onTestRepoFn: (data) => dispatch(repoActions.testRepository(data))
});

export default connect(mapStateToProps, mapDispatchToProps)(repositoryForm);
