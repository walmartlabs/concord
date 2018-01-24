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
import React, { Component } from 'react';
import PropTypes from 'prop-types';
import { findDOMNode } from 'react-dom';
import { connect } from 'react-redux';
import { Button, Header } from 'semantic-ui-react';
import RefreshButton from '../../shared/RefreshButton';
import ErrorMessage from '../../shared/ErrorMessage';
import * as constants from '../constants';
import * as actions from './actions';
import * as selectors from './reducers';
import reducers from './reducers';
import sagas from './sagas';
import './styles.css';

const notEmpty = (s) => {
  return s !== undefined && s !== null && s.trim().length > 0;
};

const shiftRange = ({ low, high, length }) => ({
  low: high,
  high: undefined
});

const FETCH_DELAY = 5000;

class LogViewer extends Component {
  constructor(props) {
    super(props);

    this.state = { autoScroll: true };

    this.scrollListener = (ev) => {
      const el = ev.target;
      const enabled = el.scrollTop + el.clientHeight === el.scrollHeight;
      this.setAutoScroll(enabled);
    };

    this.timer = undefined;
  }

  setAutoScroll(enabled) {
    this.setState({ autoScroll: enabled });
  }

  componentDidMount() {
    const el = window.document.getElementById('mainContent');
    el.addEventListener('scroll', this.scrollListener, false);

    this.update(true);
  }

  componentWillUnmount() {
    this.stopTimer();

    const el = window.document.getElementById('mainContent');
    el.removeEventListener('scroll', this.scrollListener);
  }

  componentDidUpdate(prevProps) {
    const { instanceId } = this.props;
    if (instanceId !== prevProps.instanceId) {
      this.update(true);
    }

    this.handleTimer();

    const { autoScroll } = this.state;
    const a = this.anchor;
    if (autoScroll && a) {
      const n = findDOMNode(a);
      n.scrollIntoView();
    }
  }

  update(reset) {
    this._update(reset);
    this.handleTimer();
  }

  _update(reset) {
    const { instanceId, loadData, range } = this.props;

    let nextRange;
    if (!reset && range) {
      nextRange = shiftRange(range);
    }

    loadData(instanceId, nextRange, reset);
  }

  handleTimer() {
    const { status } = this.props;
    if (status === constants.status.runningStatus) {
      if (!this.isTimerRunning()) {
        this.startTimer();
      }
    } else {
      this.stopTimer();
    }
  }

  stopTimer() {
    if (this.timer) {
      clearInterval(this.timer);
      this.timer = undefined;
    }
  }

  startTimer() {
    const f = () => {
      this._update();
    };
    this.timer = setInterval(f, FETCH_DELAY);
  }

  isTimerRunning() {
    return this.timer !== undefined;
  }

  loadWholeLog() {
    this.stopTimer();

    const { instanceId, loadData } = this.props;
    loadData(instanceId, { low: 0, high: undefined }, true);
  }

  render() {
    const { error, range, status, loading, instanceId, data } = this.props;

    if (error) {
      return <ErrorMessage message={error} retryFn={() => this.update()} />;
    }

    const fullSize = range && range.length;
    const loadWholeLog = range && range.min > 0 ? () => this.loadWholeLog() : undefined;

    return (
      <div>
        <Header as="h3">
          <RefreshButton loading={loading} onClick={() => this.update()} />
          {instanceId}
        </Header>
        <Header as="h4">{status}</Header>

        {loadWholeLog && (
          <Button basic onClick={loadWholeLog}>
            Show the whole log, {fullSize} byte(s)
          </Button>
        )}

        <Button className="sticky" onClick={() => this.setAutoScroll(!this.state.autoScroll)}>
          {this.state.autoScroll ? 'Auto-scroll ON' : 'Auto-scroll OFF'}
        </Button>

        <div className="logViewer">
          {data && data.filter(notEmpty).map((d, idx) => <div key={idx}>{d}</div>)}
        </div>

        <div
          ref={(el) => {
            this.anchor = el;
          }}
        />
      </div>
    );
  }
}

LogViewer.propTypes = {
  instanceId: PropTypes.string,
  data: PropTypes.array,
  loading: PropTypes.bool,
  status: PropTypes.string,
  refresh: PropTypes.func,
  loadData: PropTypes.func
};

const mapStateToProps = ({ log }, { params }) => ({
  instanceId: params.instanceId,
  loading: selectors.getIsLoading(log),
  error: selectors.getError(log),
  data: selectors.getData(log),
  range: selectors.getRange(log),
  status: selectors.getStatus(log)
});

const mapDispatchToProps = (dispatch) => ({
  loadData: (instanceId, fetchRange, reset) =>
    dispatch(actions.loadData(instanceId, fetchRange, reset))
});

export default connect(mapStateToProps, mapDispatchToProps)(LogViewer);

export { reducers, sagas };
