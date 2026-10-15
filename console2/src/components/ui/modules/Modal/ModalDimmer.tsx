import { Ref } from '@fluentui/react-component-ref'
import cx from 'clsx'

import React from 'react'
import { getElementType, getUnhandledProps, useClassNamesOnNode, useKeyOnly } from '../../lib'

export interface ModalDimmerProps
  extends StrictModalDimmerProps,
    React.HTMLAttributes<HTMLDivElement> {}

export interface StrictModalDimmerProps {
  /** An element type to render as (string or function). */
  as?: React.ElementType

  /** Primary content. */
  children?: React.ReactNode

  /** Additional classes. */
  className?: string

  /** A dimmer can be formatted to have its colors inverted. */
  inverted?: boolean

  /** The node where the dimmer should apply its body classes. */
  mountNode?: HTMLElement | null

  /** A modal can use the entire size of the screen. */
  scrolling?: boolean
}

/**
 * A modal has a dimmer.
 */
function ModalDimmer(props: ModalDimmerProps): React.ReactElement {
  const { children, className, inverted, mountNode, scrolling } = props

  const ref = React.useRef<HTMLElement>(null)

  const classes = cx(
    'ui',
    useKeyOnly(inverted, 'inverted'),
    'page modals dimmer transition visible active',
    className,
  )
  const bodyClasses = cx('dimmable dimmed', useKeyOnly(scrolling, 'scrolling'))

  const rest = getUnhandledProps(ModalDimmer, props)
  const ElementType = getElementType(ModalDimmer, props)

  useClassNamesOnNode(mountNode, bodyClasses)

  React.useEffect(() => {
    if (ref.current && ref.current.style) {
      ref.current.style.setProperty('display', 'flex', 'important')
    }
  }, [])

  return (
    <Ref innerRef={ref}>
      {React.createElement(ElementType, { ...rest, className: classes }, children)}
    </Ref>
  )
}

ModalDimmer.handledProps = ['as', 'children', 'className', 'inverted', 'mountNode', 'scrolling']



export default ModalDimmer
