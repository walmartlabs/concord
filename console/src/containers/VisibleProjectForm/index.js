import React, {Component} from "react";
import {connect} from "react-redux";
import {reduxForm, Field} from "redux-form";
import ProjectForm from "../../components/ProjectForm";
import ErrorMessage from "../../components/ErrorMessage";
import {getProjectState, getTemplateListState} from "../../reducers";
import * as projectSelectors from "./reducers";
import * as templateListSelectors from "../VisibleTemplateList/reducers";
import * as projectActions from "./actions";
import * as templateListAction from "../VisibleTemplateList/actions";
import * as templateListConstants from "../VisibleTemplateList/constants";
import {sort} from "../../constants";

const ConnectedForm = reduxForm({form: "project", enableReinitialize: true})(
    ({handleSubmit, onSubmitFn, ...rest}) => <ProjectForm Field={Field}
                                                          onSubmitFn={handleSubmit(onSubmitFn)} {...rest}/>
);

class VisibleProjectForm extends Component {

    componentDidMount() {
        const {project} = this.props;
        if (project.name) {
            this.load();
        } else {
            this.create();
        }
    }

    componentDidUpdate(prevProps) {
        const {project} = this.props;

        if (project.name && project.name !== prevProps.project.name) {
            this.load();
        } else if (!project.name && prevProps.project.name) {
            this.create();
        }
    }

    create() {
        const {fetchTemplates, makeNew} = this.props;
        makeNew();
        fetchTemplates();
    }

    load() {
        const {fetchTemplates, fetchData, project} = this.props;
        fetchTemplates();
        fetchData(project.name);
    }

    render() {
        const {project, templates, onCreateFn, onUpdateFn} = this.props;
        const isNew = project.name === undefined || project.name === null;

        if (project.error) {
            return <ErrorMessage message={project.error} retryFn={() => this.load()}/>;
        }

        return <ConnectedForm onSubmitFn={isNew ? onCreateFn : onUpdateFn}
                              loading={project.loading}
                              initialValues={project.data}
                              isNew={isNew}
                              templates={templates}/>
    }
}

const toOptions = (rows, labelField) =>
    rows.map(r => {
        const v = r[labelField];
        return {key: v, value: v, text: v}
    });

const mapStateToProps = (state, {params}) => {
    return {
        project: {
            name: params.name,
            data: projectSelectors.getData(getProjectState(state)),
            loading: projectSelectors.getIsLoading(getProjectState(state)),
            error: projectSelectors.getError(getProjectState(state))
        },
        templates: {
            options: toOptions(templateListSelectors.getRows(getTemplateListState(state)), templateListConstants.nameKey),
            loading: templateListSelectors.getIsLoading(getTemplateListState(state)),
            error: templateListSelectors.getError(getTemplateListState(state))
        }
    };
};

const mapDispatchToProps = (dispatch) => ({
    makeNew: () => dispatch(projectActions.makeNewProject()),
    fetchData: (id) => dispatch(projectActions.fetchProject(id)),
    fetchTemplates: () => dispatch(templateListAction.fetchTemplateList(templateListConstants.nameKey, sort.ASC)),

    // wrap into a promise, this way we can use redux-form's submitting/submitSucceeded properties
    onCreateFn: (data) => new Promise((resolve, reject) =>
        dispatch(projectActions.createProject(data, resolve, reject))),

    onUpdateFn: ({name, ...rest}) => new Promise((resolve, reject) =>
        dispatch(projectActions.updateProject(name, rest, resolve, reject)))
});

export default connect(mapStateToProps, mapDispatchToProps)(VisibleProjectForm);
