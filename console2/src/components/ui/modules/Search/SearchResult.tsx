import cx from 'clsx'

import React from 'react'
import { getElementType, getUnhandledProps, useKeyOnly } from '../../lib'

export interface SearchResultProps
  extends StrictSearchResultProps,
    Omit<React.HTMLAttributes<HTMLDivElement>, keyof StrictSearchResultProps> {}

export interface StrictSearchResultProps {
  as?: React.ElementType
  key?: string
  active?: boolean
  className?: string
  description?: string
  id?: number | string
  onClick?: (event: React.MouseEvent<HTMLDivElement>, data: SearchResultProps) => void
  title: string
}

const defaultRenderer = (searchResult: SearchResultProps): React.ReactElement => {
  const { title, description } = searchResult

  return React.createElement(
    'div',
    { className: 'content' },
    title && React.createElement('div', { className: 'title' }, title),
    description && React.createElement('div', { className: 'description' }, description),
  )
}

function SearchResult(props: SearchResultProps): React.ReactElement {
  const { active, className } = props

  const handleClick = (e: React.MouseEvent<HTMLDivElement>): void => {
    const { onClick } = props

    if (onClick) onClick(e, props)
  }

  const classes = cx(useKeyOnly(active, 'active'), 'result', className)
  const rest = getUnhandledProps(SearchResult, props)
  const ElementType = getElementType(SearchResult, props)

  // Note: You technically only need the 'content' wrapper when there's an
  // image. However, optionally wrapping it makes this function a lot more
  // complicated and harder to read. Since always wrapping it doesn't affect
  // the style in any way let's just do that.
  return React.createElement(
    ElementType,
    { ...rest, className: classes, onClick: handleClick },
    defaultRenderer(props),
  )
}

SearchResult.handledProps = ['active', 'as', 'className', 'description', 'id', 'onClick', 'title']



export default SearchResult
