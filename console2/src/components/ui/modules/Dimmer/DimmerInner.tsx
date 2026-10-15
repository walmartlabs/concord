import _invoke from 'lodash-es/invoke'
import cx from 'clsx'

import React, { useEffect, useRef } from 'react'
import { getElementType, getUnhandledProps, useKeyOnly } from '../../lib'

export interface DimmerInnerProps
  extends StrictDimmerInnerProps,
    Omit<React.HTMLAttributes<HTMLDivElement>, 'onClick'> {}

export interface StrictDimmerInnerProps {
  /** An element type to render as (string or function). */
  as?: React.ElementType

  /** An active dimmer will dim its parent container. */
  active?: boolean

  /** Primary content. */
  children?: React.ReactNode

  /** Additional classes. */
  className?: string

  /** Called when the dimmer is clicked. */
  onClick?: (event: React.MouseEvent<HTMLDivElement>, data: DimmerInnerProps) => void

  /** A dimmer can be formatted to have its colors inverted. */
  inverted?: boolean

  /** A dimmer can be formatted to be fixed to the page. */
  page?: boolean
}

/**
 * An inner element for a Dimmer.
 */
function DimmerInner(props: DimmerInnerProps): React.ReactElement {
  const containerRef = useRef<HTMLElement>(null)

  const { active, children, className, inverted, page } = props

  const handleClick = (e: React.MouseEvent<HTMLElement>): void => {
    _invoke(props, 'onClick', e, props)
  }

  const toggleStyles = (active: boolean | undefined): void => {
    const { current: container } = containerRef
    if (!container || !container.style) return

    if (active) {
      container.style.setProperty('display', 'flex', 'important')
    } else {
      container.style.removeProperty('display')
    }
  }

  // Applies the styles on mount and whenever "active" changes, replacing
  // "componentDidMount()" and "componentDidUpdate()".
  useEffect(() => {
    toggleStyles(active)
  }, [active])

  const classes = cx(
    'ui',
    useKeyOnly(active, 'active transition visible'),
    useKeyOnly(inverted, 'inverted'),
    useKeyOnly(page, 'page'),
    'dimmer',
    className,
  )

  const rest = getUnhandledProps(DimmerInner, props)
  const ElementType = getElementType(DimmerInner, props)

  return React.createElement(
    ElementType,
    { ...rest, className: classes, onClick: handleClick, ref: containerRef },
    children && React.createElement('div', { className: 'content' }, children),
  )
}

DimmerInner.handledProps = ['active', 'as', 'children', 'className', 'inverted', 'onClick', 'page']



export default DimmerInner
