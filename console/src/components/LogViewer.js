import React, {Component, PropTypes} from "react";
import {findDOMNode} from "react-dom";
import {Header, Button} from "semantic-ui-react";
import RefreshButton from "../components/RefreshButton";
import "./LogViewer.css";

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
        const {status, onRefreshFn, onLoadWholeLogFn, fullSize, loading, fileName, data} = this.props;

        return <div>
            <Header as="h3">{onRefreshFn && <RefreshButton loading={loading} onClick={onRefreshFn}/>}{fileName}</Header>
            <Header as="h4">{status}</Header>

            { onLoadWholeLogFn && <Button basic onClick={onLoadWholeLogFn}>Show whole log, {fullSize} byte(s)</Button> }

            <Button className="sticky" onClick={() => this.setAutoScroll(!this.state.autoScroll)}>
                {this.state.autoScroll ? "Auto-scroll ON" : "Auto-scroll OFF"}
            </Button>

            <div className="logViewer">
                {data && data.map((d, idx) => <div key={idx}>{d}</div>)}
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
    fileName: PropTypes.string,
    onRefreshFn: PropTypes.func,

    onLoadWholeLogFn: PropTypes.func,
    fullSize: PropTypes.number,

    status: PropTypes.string
};

export default LogViewer;