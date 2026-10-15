import * as env from 'exenv'
import * as React from 'react'

import { instance } from './lib/index'
import { normalizeTarget } from './lib/utils'
import { InputEventListener, InputTargetElement, TargetElement } from './types'

interface EventStackProps {
  /** An event name on which we will subscribe. */
  name: string
  /** An event handler or array of event handlers. */
  on: InputEventListener
  /** A name of pool. */
  pool?: string
  /** A DOM element on which we will subscribe. */
  target?: InputTargetElement
}

/**
 * This component exposes the EventStack API as public and provides a declarative way to manage it.
 */
export default class EventStack extends React.PureComponent<EventStackProps> {
  private subscribedTarget?: TargetElement

  static defaultProps = {
    pool: 'default',
    target: 'document',
  }

  componentDidMount() {
    this.subscribe(this.props)
  }

  componentDidUpdate(prevProps: EventStackProps) {
    this.unsubscribe(prevProps)
    this.subscribe(this.props)
  }

  componentWillUnmount() {
    this.unsubscribe(this.props)
  }

  subscribe(props: Readonly<EventStackProps>) {
    if (!env.canUseDOM) return

    const { name, on, pool, target } = props
    // Ref targets can be cleared before componentWillUnmount runs.
    this.subscribedTarget = normalizeTarget(target)
    instance.sub(name, on, { pool, target: this.subscribedTarget })
  }

  unsubscribe(props: Readonly<EventStackProps>) {
    const { name, on, pool } = props
    if (!this.subscribedTarget) return

    instance.unsub(name, on, { pool, target: this.subscribedTarget })
    this.subscribedTarget = undefined
  }

  render() {
    return null
  }
}
