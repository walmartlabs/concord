import React from "react";
import {connect} from "react-redux";
import {push as pushHistory} from "react-router-redux";
import {arrayPush as formArrayPush, change as formChange, FieldArray, reduxForm} from "redux-form";
import {Button, Divider, Form, Message, Popup, Table} from "semantic-ui-react";
import {Field} from "../shared/forms";
import {actions as modal} from "../shared/Modal";
import * as RepositoryPopup from "./RepositoryPopup";
import * as DeleteProjectPopup from "./DeleteProjectPopup";
import * as StartProjectPopup from "./StartProjectPopup/StartProjectPopup";
import * as api from "./api";
import * as v from "../shared/validation";
import {actions} from "./crud";

const renderSourceText = (f, {commitId}) => commitId ? "Revision" : "Branch/tag";

const renderRepositories = (pristine, newRepositoryPopupFn, editRepositoryPopupFn, startProcess) => ({fields}) => {
    const newFn = (ev) => {
        ev.preventDefault();
        newRepositoryPopupFn();
    };

    const editFn = (idx) => (ev) => {
        ev.preventDefault();
        editRepositoryPopupFn(idx, fields.get(idx));
    };

    return <Form.Field>
        <Button basic icon="add" onClick={newFn} content="Add repository"/>
        { fields.length > 0 &&
        <Table>
            <Table.Header>
                <Table.Row>
                    <Table.HeaderCell collapsing>Name</Table.HeaderCell>
                    <Table.HeaderCell>URL</Table.HeaderCell>
                    <Table.HeaderCell collapsing>Source</Table.HeaderCell>
                    <Table.HeaderCell collapsing>Path</Table.HeaderCell>
                    <Table.HeaderCell collapsing>Secret</Table.HeaderCell>
                    <Table.HeaderCell collapsing/>
                    <Table.HeaderCell collapsing/>
                </Table.Row>
            </Table.Header>
            <Table.Body>
                {fields.map((f, idx) => (
                    <Table.Row key={idx}>
                        <Table.Cell><a href="#edit" onClick={editFn(idx)}>{fields.get(idx).name}</a></Table.Cell>
                        <Table.Cell>{fields.get(idx).url}</Table.Cell>
                        <Table.Cell>{renderSourceText(f, fields.get(idx))}</Table.Cell>
                        <Table.Cell>{fields.get(idx).path}</Table.Cell>
                        <Table.Cell>{fields.get(idx).secret}</Table.Cell>
                        <Table.Cell>
                            <Button basic icon="delete" onClick={() => fields.remove(idx)}/>
                        </Table.Cell>
                        <Table.Cell>
                            <Popup
                                trigger={<Button disabled={!pristine} basic icon="caret right"
                                                 onClick={startProcess(fields.get(idx).name)}/>}
                                content="Start a new process"
                                inverted
                            />
                        </Table.Cell>
                    </Table.Row>
                ))}
            </Table.Body>
        </Table>
        }
    </Form.Field>;
};

let projectForm = (props) => {
    const {handleSubmit, reset, createNew, newRepositoryPopupFn, editRepositoryPopupFn, deletePopupFn, startProcessFn} = props;
    const {originalName} = props;
    const {pristine, submitting, invalid} = props;

    const submitDisabled = pristine || submitting || invalid;
    const resetDisabled = pristine || submitting;
    const deleteDisabled = submitting || !originalName;

    const openDeletePopup = (ev) => {
        ev.preventDefault();
        deletePopupFn(originalName);
    };

    const startProcess = (repositoryName) => (ev) => {
        ev.preventDefault();
        startProcessFn(originalName, repositoryName);
    };

    return <Form onSubmit={handleSubmit} loading={submitting}>
        {createNew && <Field name="name" label="Name" required/> }
        <Field name="description" label="Description"/>

        <Divider horizontal>Repositories</Divider>
        <FieldArray name="repositories" component={renderRepositories(pristine, newRepositoryPopupFn, editRepositoryPopupFn, startProcess)}/>

        <Divider horizontal>Configuration</Divider>
        <Message size="tiny" info>Not supported yet. Please use the REST API to update the configuration
            parameters.</Message>

        <Button primary icon="save" content="Save" disabled={submitDisabled}/>
        <Button content="Reset" onClick={reset} disabled={resetDisabled}/>

        {!createNew && <Button floated="right" negative icon="delete" content="Delete"
                               disabled={deleteDisabled} onClick={openDeletePopup}/>}
    </Form>;
};

const validate = ({name, description}) => {
    const errors = {};
    errors.name = v.project.name(name);
    errors.description = v.project.description(description);
    return errors;
};

const asyncValidate = ({name}, dispatch, {originalName}) => {
    if (name === originalName) {
        return Promise.resolve(true);
    }

    return api.isProjectExists(name).then(exists => {
        if (exists) {
            throw Object({name: v.projectAlreadyExistsError(name)});
        }
    });
};

projectForm = reduxForm({
    form: "project",
    validate,
    asyncValidate,
    enableReinitialize: true,
    keepDirtyOnReinitialize: true
})(projectForm);

const mapDispatchToProps = (dispatch) => ({
    newRepositoryPopupFn: () => {
        // TODO replace with action creators
        const onSuccess = (data) =>
            dispatch(formArrayPush("project", "repositories", data));

        dispatch(modal.open(RepositoryPopup.MODAL_TYPE, {onSuccess}));
    },

    editRepositoryPopupFn: (idx, initialValues) => {
        // TODO replace with action creators
        const onSuccess = (data) => {
            dispatch(modal.close());
            dispatch(formChange("project", `repositories[${idx}]`, data));
        };

        dispatch(modal.open(RepositoryPopup.MODAL_TYPE, {onSuccess, initialValues, editMode: true}));
    },

    deletePopupFn: (name) => {
        const onConfirmFn = () => {
            dispatch(actions.deleteData(name, [modal.close()]));
            dispatch(pushHistory("/project/list"));
        };

        dispatch(modal.open(DeleteProjectPopup.MODAL_TYPE, {onConfirmFn}));
    },

    startProcessFn: (projectName, repositoryName) => {
        dispatch(modal.open(StartProjectPopup.MODAL_TYPE, {projectName, repositoryName}));
    }
});


export default connect(null, mapDispatchToProps)(projectForm);
