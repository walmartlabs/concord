import React, {Component} from "react";
import {connect} from "react-redux";
import ProjectTable from "../components/ProjectTable";
import ErrorMessage from "../components/ErrorMessage";
import {
    getProjectListRows,
    getIsProjectListLoading,
    getProjectListLoadingError,
    getIsProjectInFlight
} from "../reducers";
import * as constants from "../constants";
import * as actions from "../actions";

class VisibleProjectTable extends Component {
    componentDidMount() {
        this.update();
    }

    componentDidUpdate(prevProps) {
        const {sortBy, sortDir} = this.props;
        if (sortBy !== prevProps.sortBy || sortDir !== prevProps.sortDir) {
            this.update();
        }
    }

    update() {
        const {sortBy, sortDir, fetchData} = this.props;
        fetchData(sortBy, sortDir);
    }

    render() {
        const {error, ...rest} = this.props;
        if (error) {
            return <ErrorMessage message={error} retryFn={() => this.update()}/>;
        }
        return <ProjectTable {...rest} onRefreshFn={() => this.update()}/>;
    }
}

const mapStateToProps = (state, {location}) => {
    const sortBy = location.query.sortBy || constants.projectList.defaultSortKey;
    const sortDir = location.query.sortDir || constants.projectList.defaultSortDir;

    return {
        cols: constants.projectList.columns,
        rows: getProjectListRows(state),
        loading: getIsProjectListLoading(state),
        error: getProjectListLoadingError(state),
        inFlightFn: (name) => getIsProjectInFlight(state, name),
        sortBy,
        sortDir
    }
};

const mapDispatchToProps = (dispatch) => ({
    fetchData: (sortBy, sortDir) => dispatch(actions.fetchProjectList(sortBy, sortDir)),
    onDeleteFn: (name) => dispatch(actions.deleteProject(name))
});

export default connect(mapStateToProps, mapDispatchToProps)(VisibleProjectTable);