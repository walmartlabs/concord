import _invoke from 'lodash-es/invoke'
import cx from 'clsx'

import React from 'react'
import { getElementType, getUnhandledProps, useKeyOnly } from '../../lib'

export interface BreadcrumbSectionProps
  extends Omit<React.AnchorHTMLAttributes<HTMLAnchorElement>, keyof StrictBreadcrumbSectionProps>,
    StrictBreadcrumbSectionProps {}

export interface StrictBreadcrumbSectionProps {
  /** An element type to render as (string or function). */
  as?: React.ElementType

  /** Style as the currently active section. */
  active?: boolean

  /** Primary content. */
  children?: React.ReactNode

  /** Additional classes. */
  className?: string

  /** Render as an `a` tag instead of a `div` and adds the href attribute. */
  href?: string

  /**
   * Called on click. When passed, the component will render as an `a`
   * tag by default instead of a `div`.
   *
   * @param {SyntheticEvent} event - React's original SyntheticEvent.
   * @param {object} data - All props.
   */
  onClick?: (event: React.MouseEvent<HTMLAnchorElement>, data: BreadcrumbSectionProps) => void
}

/**
 * A section sub-component for Breadcrumb component.
 */
function BreadcrumbSection(props: BreadcrumbSectionProps): React.ReactElement {
  const { active, children, className, href } = props

  const computeElementType = (): 'a' | undefined => {
    if (href || props.onClick) return 'a'
    return undefined
  }

  const handleClick = (e: React.MouseEvent<HTMLAnchorElement>): void =>
    _invoke(props, 'onClick', e, props)

  const classes = cx(useKeyOnly(active, 'active'), 'section', className)
  const rest = getUnhandledProps(BreadcrumbSection, props)
  const ElementType = getElementType(BreadcrumbSection, props, computeElementType)

  return React.createElement(
    ElementType,
    { ...rest, className: classes, href, onClick: handleClick },
    children,
  )
}

BreadcrumbSection.handledProps = ['active', 'as', 'children', 'className', 'href', 'onClick']



export default BreadcrumbSection
