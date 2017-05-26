import React from "react";
import PropTypes from "prop-types";
import {connect} from "react-redux";
import {formValueSelector, reduxForm, submit as submitForm} from "redux-form";
import {Button, Form, Modal} from "semantic-ui-react";
import {Dropdown, Field} from "../../shared/forms";
import SecretListDropdown from "../../user/secret/SecretsListDropdown";
import * as v from "../../shared/validation";
import * as c from "./constants";

const SOURCE_TYPE_LABELS = {
    [c.BRANCH_SOURCE_TYPE]: "Branch/tag",
    [c.REV_SOURCE_TYPE]: "Revision"
};

let repositoryForm = (props) => {
    const {open, editMode, sourceTypeValue = c.BRANCH_SOURCE_TYPE} = props;

    const {pristine, submitting, invalid} = props;
    const saveDisabled = pristine || submitting || invalid;

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

                <Dropdown widget={SecretListDropdown} name="secret" label="Secret" required/>
            </Form>
        </Modal.Content>
        <Modal.Actions>
            <Button color="red" onClick={onCloseFn}>Cancel</Button>
            <Button color="green" onClick={onSaveFn} disabled={saveDisabled}>{editMode ? "Save" : "Add"}</Button>
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
    validate,
    enableReinitialize: true,
    keepDirtyOnReinitialize: true
})(repositoryForm);

const selector = formValueSelector("repository");

const mapStateToProps = (state) => ({
    sourceTypeValue: selector(state, "sourceType")
});

const mapDispatchToProps = (dispatch) => ({
    onSaveFn: () => dispatch(submitForm("repository"))
});

export default connect(mapStateToProps, mapDispatchToProps)(repositoryForm);
