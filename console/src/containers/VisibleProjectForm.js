import React, {Component} from "react";
import {connect} from "react-redux";
import ProjectForm from "../components/ProjectForm";
import ErrorMessage from "../components/ErrorMessage";
import {reduxForm, Field} from "redux-form";
import * as selectors from "../reducers";
import * as actions from "../actions";
import * as constants from "../constants";

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
            data: selectors.getProjectData(state),
            loading: selectors.getIsProjectDataLoading(state),
            error: selectors.getProjectLoadingError(state)
        },
        templates: {
            options: toOptions(selectors.getTemplateListRows(state), constants.templateList.nameKey),
            loading: selectors.getIsTemplateListLoading(state),
            error: selectors.getTemplateListLoadingError(state)
        }
    };
};

const mapDispatchToProps = (dispatch) => ({
    makeNew: () => dispatch(actions.makeNewProject()),
    fetchData: (id) => dispatch(actions.fetchProject(id)),
    fetchTemplates: () => dispatch(actions.fetchTemplateList(constants.templateList.nameKey, constants.sort.ASC)),

    // wrap into a promise, this way we can use redux-form's submitting/submitSucceeded properties
    onCreateFn: (data) => new Promise((resolve, reject) =>
        dispatch(actions.createProject(data, resolve, reject))),

    onUpdateFn: ({name, ...rest}) => new Promise((resolve, reject) =>
        dispatch(actions.updateProject(name, rest, resolve, reject)))
});

export default connect(mapStateToProps, mapDispatchToProps)(VisibleProjectForm);
