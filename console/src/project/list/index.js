import React, {Component} from "react";
import PropTypes from "prop-types";
import {connect} from "react-redux";
import {Link} from "react-router";
import {Header} from "semantic-ui-react";
import RefreshButton from "../../shared/RefreshButton";
import ErrorMessage from "../../shared/ErrorMessage";
import DataTable from "../../shared/DataTable";
import * as api from "./api";
import DataList from "../../shared/DataList";

const {actions, reducers, selectors, sagas} = DataList("project/list", api.listProjects);

const columns = [
    {key: "name", label: "Project", collapsing: true},
    {key: "description", label: "Description"}
];

const projectNameKey = "name";

const cellFn = (row, key) => {
    // link to the project page
    if (key === projectNameKey) {
        const n = row[key];
        return <Link to={`/project/${n}`}>{n}</Link>;
    }
    return row[key];
};

class ProjectListPage extends Component {

    componentDidMount() {
        this.load();
    }

    load() {
        const {loadData} = this.props;
        loadData();
    }

    render() {
        const {loading, error, data} = this.props;

        if (error) {
            return <ErrorMessage message={error} retryFn={() => this.load()}/>;
        }

        return <div>
            <Header as="h3"><RefreshButton loading={loading} onClick={() => this.load()}/> Projects</Header>
            <DataTable cols={columns} rows={data ? data : []} cellFn={cellFn}/>
        </div>;
    }
}

ProjectListPage.propTypes = {
    loading: PropTypes.bool,
    error: PropTypes.any,
    data: PropTypes.array
};

const mapStateToProps = ({projectList}) => ({
    loading: selectors.isLoading(projectList),
    error: selectors.getError(projectList),
    data: selectors.getData(projectList)
});

const mapDispatchToProps = (dispatch) => ({
    loadData: () => dispatch(actions.loadData())
});

export default connect(mapStateToProps, mapDispatchToProps)(ProjectListPage);

export {reducers, sagas};
