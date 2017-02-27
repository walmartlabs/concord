import React, {Component, PropTypes} from "react";
import {findDOMNode} from "react-dom";
import {Header, Button} from "semantic-ui-react";
import RefreshButton from "../RefreshButton";
import "./styles.css";

const notEmpty = (s) => {
    return s !== undefined && s !== null && s.trim().length > 0;
};

class LogViewer extends Component {

    constructor(props) {
        super(props);

        this.state = {autoScroll: true};

        this.scrollListener = (ev) => {
            const el = ev.target;
            const enabled = el.scrollTop + el.clientHeight === el.scrollHeight;
            this.setAutoScroll(enabled);
        };
    }

    setAutoScroll(enabled) {
        this.setState({autoScroll: enabled});
    }

    componentDidMount() {
        const el = window.document.getElementById("mainContent");
        el.addEventListener("scroll", this.scrollListener, false);
    }

    componentWillUnmount() {
        const el = window.document.getElementById("mainContent");
        el.removeEventListener("scroll", this.scrollListener);
    }

    componentDidUpdate(prevProps) {
        const {autoScroll} = this.state;
        const a = this.anchor;
        if (autoScroll && a) {
            const n = findDOMNode(a);
            n.scrollIntoView();
        }
    }

    render() {
        const {status, onRefreshFn, onLoadWholeLogFn, fullSize, loading, instanceId, data} = this.props;

        return <div>
            <Header as="h3">{onRefreshFn && <RefreshButton loading={loading} onClick={onRefreshFn}/>}{instanceId}</Header>
            <Header as="h4">{status}</Header>

            { onLoadWholeLogFn && <Button basic onClick={onLoadWholeLogFn}>Show the whole log, {fullSize} byte(s)</Button> }

            <Button className="sticky" onClick={() => this.setAutoScroll(!this.state.autoScroll)}>
                {this.state.autoScroll ? "Auto-scroll ON" : "Auto-scroll OFF"}
            </Button>

            <div className="logViewer">
                {data && data.filter(notEmpty).map((d, idx) => <div key={idx}>{d}</div>)}
            </div>

            <div ref={(el) => {
                this.anchor = el;
            }}/>
        </div>
    }
}

LogViewer.propTypes = {
    data: PropTypes.array,
    loading: PropTypes.bool,
    instanceId: PropTypes.string,
    onRefreshFn: PropTypes.func,

    onLoadWholeLogFn: PropTypes.func,
    fullSize: PropTypes.number,

    status: PropTypes.string
};

export default LogViewer;