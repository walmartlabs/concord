import React, {Component} from "react";
import PropTypes from "prop-types";
import {connect} from "react-redux";
import {Button, Card, CardGroup, Header, Image} from "semantic-ui-react";
import {Link} from "react-router";
import * as StartProjectPopup from "../../project/StartProjectPopup/StartProjectPopup";
import RefreshButton from "../../shared/RefreshButton";
import ErrorMessage from "../../shared/ErrorMessage";
import {actions as modal} from "../../shared/Modal";
import DataItem from "../../shared/DataItem";
import * as api from "./api";

const {actions, reducers, selectors, sagas} = DataItem("landing/list", [], api.loadData);

class LandingPage extends Component {

    componentDidMount() {
        this.load();
    }

    load() {
        const {loadData} = this.props;
        loadData();
    }

    renderCard(item) {
        const {startProcessPopupFn} = this.props;
        return <Card key={item.id}>
            <Card.Content>
                {item.icon ? <Image floated='right' size='mini' src={`data:image/png;base64, ${item.icon}`}/> : ''}

                <Card.Header>
                    {item.name}
                </Card.Header>
                <Card.Meta>
                    Project: <Link to={`/project/${item.projectName}`}>{item.projectName}</Link>
                </Card.Meta>
                <Card.Description>
                    {item.description}
                </Card.Description>
            </Card.Content>
            <Card.Content extra>
                <div className='ui two buttons'>
                    <Button basic color='green'
                            onClick={() => startProcessPopupFn(item.projectName, item.repositoryName)}>Start
                        process</Button>
                </div>
            </Card.Content>
        </Card>
    }

    render() {
        const {loading, error, data} = this.props;

        if (error) {
            return <ErrorMessage message={error} retryFn={() => this.load()}/>;
        }

        return <div>
            <Header as="h3"><RefreshButton loading={loading} onClick={() => this.load()}/> Registered flows</Header>

            {(!data || data.length <= 0) && <p>No registered flows found.</p>}
            {data && data.length > 0 && <CardGroup>{data.map(item => this.renderCard(item))}</CardGroup>}
        </div>;
    }
}

LandingPage.propTypes = {
    loading: PropTypes.bool,
    error: PropTypes.any,
    data: PropTypes.array,
    startProcessPopupFn: PropTypes.func
};

const mapStateToProps = ({landingList}) => ({
    loading: selectors.isLoading(landingList),
    error: selectors.getError(landingList),
    data: selectors.getData(landingList)
});

const mapDispatchToProps = (dispatch) => ({
    loadData: () => dispatch(actions.loadData()),
    startProcessPopupFn: (projectName, repositoryName) => {
        dispatch(modal.open(StartProjectPopup.MODAL_TYPE, {projectName, repositoryName}));
    }
});

export default connect(mapStateToProps, mapDispatchToProps)(LandingPage);

export {reducers, sagas};